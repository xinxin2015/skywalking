package org.apache.skywalking.apm.agent.core.context;

import org.apache.skywalking.apm.agent.core.context.handler.FinishedSpanHandler;
import org.apache.skywalking.apm.agent.core.context.handler.MutableSpan;
import org.apache.skywalking.apm.agent.core.context.internal.recorder.PendingSpans;
import org.apache.skywalking.apm.agent.core.context.propagation.CurrentTraceContext.Scope;
import org.apache.skywalking.apm.agent.core.context.propagation.TraceContext;

/** This wraps the public api and guards access to a mutable span. */
final class RealScopedSpan extends ScopedSpan {

    private final TraceContext context;
    private final Scope scope;
    private final MutableSpan state;
    private final Clock clock;
    private final PendingSpans pendingSpans;
    private final FinishedSpanHandler finishedSpanHandler;

    RealScopedSpan(TraceContext context, Scope scope, MutableSpan state, Clock clock,
        PendingSpans pendingSpans, FinishedSpanHandler finishedSpanHandler) {
        this.context = context;
        this.scope = scope;
        this.state = state;
        this.clock = clock;
        this.pendingSpans = pendingSpans;
        this.finishedSpanHandler = finishedSpanHandler;
    }

    @Override
    public boolean isNoop() {
        return false;
    }

    @Override
    public TraceContext context() {
        return context;
    }

    @Override
    public ScopedSpan annotate(String value) {
        state.annotate(clock.currentTimeMicroseconds(), value);
        return this;
    }

    @Override
    public ScopedSpan tag(String key, String value) {
        state.tag(key, value);
        return this;
    }

    @Override
    public ScopedSpan error(Throwable throwable) {
        state.error(throwable);
        return this;
    }

    @Override
    public void finish() {
        scope.close();
        if (!pendingSpans.remove(context)) {
            return; // don't double-report
        }
        state.finishTimestamp(clock.currentTimeMicroseconds());
        finishedSpanHandler.handle(context, state);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof RealScopedSpan)) {
            return false;
        }
        RealScopedSpan that = (RealScopedSpan) o;
        return context.equals(that.context) && scope.equals(that.scope);
    }

    @Override
    public int hashCode() {
        int h = 1;
        h *= 1000003;
        h ^= context.hashCode();
        h *= 1000003;
        h ^= scope.hashCode();
        return h;
    }
}
