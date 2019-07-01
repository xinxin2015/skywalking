package org.apache.skywalking.apm.agent.core.context.propagation;

import org.apache.skywalking.apm.agent.core.context.internal.Nullable;
import org.apache.skywalking.apm.agent.core.util.Assert;


/**
 * In-process trace context propagation backed by a static thread local.
 *
 * <h3>Design notes</h3>
 *
 * <p>A static thread local ensures we have one context per thread, as opposed to one per thread-
 * tracer. This means all tracer instances will be able to see any tracer's contexts.
 *
 * <p>The trade-off of this (instance-based reference) vs the reverse: trace contexts are not
 * separated by tracer by default. For example, to make a trace invisible to another tracer, you
 * have to use a non-default implementation.
 *
 * <p>Sometimes people make different instances of the tracer just to change configuration like
 * the local service name. If we used a thread-instance approach, none of these would be able to see
 * eachother's scopes. This would break {@link Tracing#currentTracer()} scope visibility in a way
 * few would want to debug. It might be phrased as "MySQL always starts a new trace and I don't know
 * why."
 *
 * <p>If you want a different behavior, use a different subtype of {@link CurrentTraceContext},
 * possibly your own, or raise an issue and explain what your use case is.
 */
public class ThreadLocalCurrentTraceContext extends CurrentTraceContext { // not final for backport

    public static CurrentTraceContext create() {
        return new Builder().build();
    }

    public static CurrentTraceContext.Builder newBuilder() {
        return new Builder();
    }

    static final ThreadLocal<TraceContext> DEFAULT = new ThreadLocal<>();

    static final class Builder extends CurrentTraceContext.Builder {

        @Override
        public CurrentTraceContext build() {
            return new ThreadLocalCurrentTraceContext(this,DEFAULT);
        }
    }

    @SuppressWarnings("ThreadLocalUsage") // intentional: to support multiple Tracer instances
    private final ThreadLocal<TraceContext> local;

    ThreadLocalCurrentTraceContext(CurrentTraceContext.Builder builder,
        ThreadLocal<TraceContext> local) {
        super(builder);
        Assert.notNull(local,"local can not be null");
        this.local = local;
    }

    @Override
    public TraceContext get() {
        return local.get();
    }

    @Override
    public Scope newScope(@Nullable TraceContext currentSpan) {
        final TraceContext previous = local.get();
        local.set(currentSpan);
        class ThreadLocalScope implements Scope {
            @Override
            public void close() {
                local.set(previous);
            }
        }
        Scope result = new ThreadLocalScope();
        return decorateScope(currentSpan,result);
    }
}
