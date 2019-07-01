package org.apache.skywalking.apm.agent.core.context.internal.recorder;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.skywalking.apm.agent.core.context.Clock;
import org.apache.skywalking.apm.agent.core.context.handler.FinishedSpanHandler;
import org.apache.skywalking.apm.agent.core.context.handler.MutableSpan;
import org.apache.skywalking.apm.agent.core.context.internal.InternalPropagation;
import org.apache.skywalking.apm.agent.core.context.internal.Nullable;
import org.apache.skywalking.apm.agent.core.context.internal.Platform;
import org.apache.skywalking.apm.agent.core.context.propagation.TraceContext;
import org.apache.skywalking.apm.agent.core.util.Assert;
import org.apache.skywalking.apm.agent.logging.Log;
import org.apache.skywalking.apm.agent.logging.LogFactory;


/**
 * Similar to Finagle's deadline span map, except this is GC pressure as opposed to timeout driven.
 * This means there's no bookkeeping thread required in order to flush orphaned spans.
 *
 * <p>Spans are weakly referenced by their owning context. When the keys are collected, they are
 * transferred to a queue, waiting to be reported. A call to modify any span will implicitly flush
 * orphans to Zipkin. Spans in this state will have a "brave.flush" annotation added to them.
 *
 * <p>The internal implementation is derived from WeakConcurrentMap by Rafael Winterhalter. See
 * https://github.com/raphw/weak-lock-free/blob/master/src/main/java/com/blogspot/mydailyjava/weaklockfree/WeakConcurrentMap.java
 */
public final class PendingSpans extends ReferenceQueue<TraceContext> {

    private static final Log logger = LogFactory.getLog(PendingSpans.class);

    // Even though we only put by RealKey, we allow get and remove by LookupKey
    private final ConcurrentMap<Object, PendingSpan> delegate = new ConcurrentHashMap<>(64);

    private final Clock clock;

    private final FinishedSpanHandler handler; // Used when flushing spans

    private final AtomicBoolean noop;

    public PendingSpans(Clock clock, FinishedSpanHandler handler, AtomicBoolean noop) {
        this.clock = clock;
        this.handler = handler;
        this.noop = noop;
    }

    public PendingSpan getOrCreate(TraceContext context, boolean start) {
        Assert.notNull(context, "context can not be null");
        reportOrphanedSpans();
        PendingSpan result = delegate.get(context);
        if (result != null) {
            return result;
        }

        MutableSpan data = new MutableSpan();

        if (context.shared()) {
            data.setShared();
        }

        // save overhead calculating time if the parent is in-progress (usually is)
        TickClock clock = getClockFromParent(context);
        if (clock == null) {
            clock = new TickClock(this.clock.currentTimeMicroseconds(),System.nanoTime());
            if (start) {
                data.startTimestamp(clock.getBaseEpochMicros());
            }
        } else if (start) {
            data.startTimestamp(clock.getBaseEpochMicros());
        }

        PendingSpan pendingSpan = new PendingSpan(data,clock);
        PendingSpan previousSpan = delegate.putIfAbsent(new RealKey(context,this),pendingSpan);
        if (previousSpan != null) {
            return previousSpan; // lost race
        }

        if (logger.isDebugEnabled()) {
            pendingSpan.caller = new Throwable("Thread " + Thread.currentThread().getName() + " "
                + "allocated span here");
        }
        return pendingSpan;
    }

    /** Trace contexts are equal only on trace ID and span ID. try to get the parent's clock */
    @Nullable
    private TickClock getClockFromParent(TraceContext context) {
        long parentId = context.parentIdAsLong();

        // NOTE: we still look for lookup key even on root span, as a client span can be root, and a
        // server can share the same ID. Essentially, a shared span is similar to a child.
        PendingSpan parent = null;
        if (context.shared() || parentId != 0L) {
            long spanId = parentId != 0L ? parentId : context.spanId();
            parent = delegate.get(InternalPropagation.instance.newTraceContext(
                0, context.traceIdHigh(), context.traceId(), 0, 0, spanId, Collections.emptyList()
            ));
        }
        return parent != null ? (TickClock) parent.clock() : null;
    }

    /** @see org.apache.skywalking.apm.agent.core.context.Span#abandon() */
    public boolean remove(TraceContext context) {
        Assert.notNull(context,"context can not be null");
        PendingSpan last = delegate.remove(context);
        reportOrphanedSpans();
        return last != null;
    }

    /** Reports spans orphaned by garbage collection. */
    private void reportOrphanedSpans() {
        RealKey contextKey;

        // This is called on critical path of unrelated traced operations. If we have orphaned
        // spans, be
        // careful to not penalize the performance of the caller. It is better to cache time when
        // flushing a span than hurt performance of unrelated operations by calling
        // currentTimeMicroseconds N times
        long flushTime = 0L;
        boolean noop = handler == FinishedSpanHandler.NOOP || this.noop.get();
        while ((contextKey = (RealKey) poll()) != null) {
            PendingSpan value = delegate.remove(contextKey);
            if (noop || value == null || !contextKey.sampled) {
                continue;
            }
            if (flushTime == 0L) {
                flushTime = clock.currentTimeMicroseconds();
            }
            TraceContext context = InternalPropagation.instance.newTraceContext(
                InternalPropagation.FLAG_SAMPLED_SET | InternalPropagation.FLAG_SAMPLED,
                contextKey.traceIdHigh, contextKey.traceId, contextKey.localRootId, 0L,
                contextKey.spanId, Collections.emptyList());
            value.state().annotate(flushTime, "brave.flush");

            Throwable caller = value.caller;
            if (caller != null) {
                logger.debug("Span " + context + " neither finished nor flushed before GC", caller);
            }

            try {
                handler.handle(context, value.state());
            } catch (RuntimeException ex) {
                Platform.get().log("error reporting {0}", context, ex);
            }
        }
    }

    /**
     * Real keys contain a reference to the real context associated with a span. This is a weak
     * reference, so that we get notified on GC pressure.
     *
     * <p>Since {@linkplain TraceContext}'s hash code is final, it is used directly both here and in
     * lookup keys.
     */
    static final class RealKey extends WeakReference<TraceContext> {

        final int hashCode;

        // Copy the identity fields from the trace context, so we can use them when the reference clears
        final long traceIdHigh, traceId, localRootId, spanId;

        final boolean sampled;

        RealKey(TraceContext context, ReferenceQueue<TraceContext> queue) {
            super(context, queue);
            hashCode = context.hashCode();
            traceIdHigh = context.traceIdHigh();
            traceId = context.traceId();
            localRootId = context.localRootId();
            spanId = context.spanId();
            sampled = Boolean.TRUE.equals(context.sampled());
        }

        @Override
        public String toString() {
            TraceContext context = get();
            return context != null ? "WeakReference(" + context + ")" : "ClearedReference()";
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        /**
         * Resolves hash code collisions
         */
        @Override
        public boolean equals(Object other) {
            TraceContext thisContext = get(), thatContext = ((RealKey) other).get();
            if (thisContext == null) {
                return thatContext == null;
            } else {
                return thisContext.equals(thatContext);
            }
        }
    }

    /**
     * Lookup keys are cheaper than real keys as reference tracking is not involved. We cannot use
     * {@linkplain TraceContext} directly as a lookup key, as eventhough it has the same hash code as
     * the real key, it would fail in equals comparison.
     */
    static final class LookupKey {

        long traceIdHigh, traceId, spanId;

        boolean shared;

        int hashCode;

        void set(TraceContext context) {
            set(context.traceIdHigh(), context.traceId(), context.spanId(), context.shared());
        }

        void set(long traceIdHigh, long traceId, long spanId, boolean shared) {
            this.traceIdHigh = traceIdHigh;
            this.traceId = traceId;
            this.spanId = spanId;
            this.shared = shared;
            hashCode = generateHashCode(traceIdHigh, traceId, spanId, shared);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        static int generateHashCode(long traceIdHigh, long traceId, long spanId, boolean shared) {
            int h = 1;
            h *= 1000003;
            h ^= (int) ((traceIdHigh >>> 32) ^ traceIdHigh);
            h *= 1000003;
            h ^= (int) ((traceId >>> 32) ^ traceId);
            h *= 1000003;
            h ^= (int) ((spanId >>> 32) ^ spanId);
            h *= 1000003;
            h ^= shared ? InternalPropagation.FLAG_SHARED : 0; // to match TraceContext.hashCode
            return h;
        }

        /**
         * Resolves hash code collisions
         */
        @Override
        public boolean equals(Object other) {
            RealKey that = (RealKey) other;
            TraceContext thatContext = that.get();
            if (thatContext == null) {
                return false;
            }
            return traceIdHigh == thatContext.traceIdHigh()
                && traceId == thatContext.traceId()
                && spanId == thatContext.spanId()
                && shared == thatContext.shared();
        }
    }

    @Override
    public String toString() {
        return "PendingSpans" + delegate.keySet();
    }

}

