package org.apache.skywalking.apm.agent.core.context.internal.recorder;

import org.apache.skywalking.apm.agent.core.context.Clock;

final class TickClock implements Clock {

    private final long baseEpochMicros;

    private final long baseTickNanos;

    TickClock(long baseEpochMicros, long baseTickNanos) {
        this.baseEpochMicros = baseEpochMicros;
        this.baseTickNanos = baseTickNanos;
    }

    @Override
    public long currentTimeMicroseconds() {
        return ((System.nanoTime() - baseTickNanos) / 1000) + baseEpochMicros;
    }

    @Override
    public String toString() {
        return "TickClock{"
            + "baseEpochMicros=" + baseEpochMicros + ", "
            + "baseTickNanos=" + baseTickNanos
            + "}";
    }

    public long getBaseEpochMicros() {
        return baseEpochMicros;
    }

    public long getBaseTickNanos() {
        return baseTickNanos;
    }
}
