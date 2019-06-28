package org.apache.skywalking.apm.agent.core.context.internal.recorder;

import org.apache.skywalking.apm.agent.core.context.Clock;
import org.apache.skywalking.apm.agent.core.context.handler.MutableSpan;

public final class PendingSpan {

    private final MutableSpan state;

    private final TickClock clock;

    volatile Throwable caller;

    PendingSpan(MutableSpan state,TickClock clock) {
        this.state = state;
        this.clock = clock;
    }

    /** Returns the state currently accumulated for this trace ID and span ID */
    public MutableSpan state() {
        return state;
    }

    /** Returns a clock that ensures startTimestamp consistency across the trace */
    public Clock clock() {
        return clock;
    }

}
