package org.apache.skywalking.apm.agent.core.context.propagation;

import static org.apache.skywalking.apm.agent.core.context.internal.InternalPropagation.FLAG_SAMPLED;
import static org.apache.skywalking.apm.agent.core.context.internal.InternalPropagation.FLAG_SAMPLED_SET;

import org.apache.skywalking.apm.agent.core.context.internal.HexCodec;
import org.apache.skywalking.apm.agent.core.context.internal.InternalPropagation;
import org.apache.skywalking.apm.agent.core.context.internal.Nullable;
import org.apache.skywalking.apm.agent.core.util.Assert;


/**
 * Contains inbound trace ID and sampling flags, used when users control the root trace ID, but not
 * the span ID (ex Amazon X-Ray or other correlation).
 */
public final class TraceIdContext extends SamplingFlags {

    public static Builder newBuilder() {
        return new Builder();
    }

    /** When non-zero, the trace containing this span uses 128-bit trace identifiers. */
    public long traceIdHigh() {
        return traceIdHigh;
    }

    /** Unique 8-byte identifier for a trace, set on all spans within it. */
    public long traceId() {
        return traceId;
    }

    public Builder toBuilder() {
        Builder result = new Builder();
        result.flags = flags;
        result.traceIdHigh = traceIdHigh;
        result.traceId = traceId;
        return result;
    }

    /** Returns {@code $traceId} */
    @Override
    public String toString() {
        boolean traceIdHi = traceIdHigh != 0;
        char[] result = new char[traceIdHi ? 32 : 16];
        int pos = 0;
        if (traceIdHi) {
            HexCodec.writeHexLong(result,pos,traceIdHigh);
            pos += 16;
        }
        HexCodec.writeHexLong(result,pos,traceId);
        return new String(result);
    }

    public static final class Builder {

        long traceIdHigh,traceId;

        int flags;

        /** @see TraceIdContext#traceIdHigh() */
        public Builder toTraceIdHigh(long traceIdHigh) {
            this.traceIdHigh = traceIdHigh;
            return this;
        }

        /** @see TraceIdContext#traceId() */
        public Builder traceId(long traceId) {
            this.traceId = traceId;
            return this;
        }

        /** @see TraceIdContext#sampled() */
        public Builder sampled(boolean sampled) {
            flags = InternalPropagation.sampled(sampled,flags);
            return this;
        }

        /** @see TraceIdContext#sampled() */
        public Builder sampled(@Nullable Boolean sampled) {
            if (sampled == null) {
                flags &= ~(FLAG_SAMPLED_SET | FLAG_SAMPLED);
                return this;
            }
            return sampled(sampled.booleanValue());
        }

        /** @see TraceIdContext#debug() */
        public Builder debug(boolean debug) {
            flags = SamplingFlags.debug(debug,flags);
            return this;
        }

        public final TraceIdContext build() {
            Assert.state(traceId != 0L,"Missing : traceId");
            return new TraceIdContext(flags,traceIdHigh,traceId);
        }

        Builder() {
            // no external implementations
        }
    }

    private final long traceIdHigh,traceId;

    TraceIdContext(int flags,long traceIdHigh,long traceId) {
        super(flags);
        this.traceIdHigh = traceIdHigh;
        this.traceId = traceId;
    }

    /** Only includes mandatory fields {@link #traceIdHigh()} and {@link #traceId()} */
    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof TraceIdContext)) {
            return false;
        }
        TraceIdContext that = (TraceIdContext) o;
        return (traceIdHigh == that.traceIdHigh) && (traceId == that.traceId);
    }

    /** Only includes mandatory fields {@link #traceIdHigh()} and {@link #traceId()} */
    @Override
    public int hashCode() {
        int h = 1;
        h *= 1000003;
        h ^= (int) ((traceIdHigh >>> 32) ^ traceIdHigh);
        h *= 1000003;
        h ^= (int) ((traceId >>> 32) ^ traceId);
        return h;
    }
}
