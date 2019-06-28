package org.apache.skywalking.apm.agent.core.context.propagation;

import static org.apache.skywalking.apm.agent.core.context.internal.InternalPropagation.FLAG_SAMPLED_LOCAL;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.skywalking.apm.agent.core.context.internal.InternalPropagation;
import org.apache.skywalking.apm.agent.core.context.internal.Lists;
import org.apache.skywalking.apm.agent.core.context.internal.Nullable;
import org.apache.skywalking.apm.agent.core.util.Assert;

/**
 * Union type that contains only one of trace context, trace ID context or sampling flags. This type
 * is designed for use with {@link Tracer#nextSpan(TraceContextOrSamplingFlags)}.
 *
 * <p>Users should not create instances of this, rather use {@link TraceContext.Extractor} provided
 * by a {@link Propagation} implementation such as {@link Propagation#B3_STRING}.
 *
 * <p>Those implementing {@link Propagation} should use the following advice:
 * <pre><ul>
 *   <li>If you have the trace and span ID, use {@link #create(TraceContext)}</li>
 *   <li>If you have only a trace ID, use {@link #create(TraceIdContext)}</li>
 *   <li>Otherwise, use {@link #create(SamplingFlags)}</li>
 * </ul></pre>
 * <p>If your propagation implementation adds extra data, append it via {@link
 * Builder#addExtra(Object)}.
 *
 *
 * <p>This started as a port of {@code com.github.kristofa.brave.TraceData}, which served the same
 * purpose.
 *
 * @see TraceContext.Extractor
 */
public final class TraceContextOrSamplingFlags {

    public static final TraceContextOrSamplingFlags
        EMPTY = new TraceContextOrSamplingFlags(3,SamplingFlags.EMPTY,Collections.emptyList()),
        NOT_SAMPLED = new TraceContextOrSamplingFlags(3,SamplingFlags.NOT_SAMPLED,
            Collections.emptyList()),
        SAMPLED = new TraceContextOrSamplingFlags(3,SamplingFlags.SAMPLED,Collections.emptyList()),
        DEBUG = new TraceContextOrSamplingFlags(3,SamplingFlags.DEBUG,Collections.emptyList());

    public static Builder newBuilder() {
        return new Builder();
    }

    /** Returns {@link SamplingFlags#sampled()}, regardless of subtype. */
    @Nullable
    public Boolean sampled() {
        return value.sampled();
    }

    /** Returns {@link SamplingFlags#sampledLocal()}}, regardless of subtype. */
    public final boolean sampledLocal() {
        return (value.flags & FLAG_SAMPLED_LOCAL) == FLAG_SAMPLED_LOCAL;
    }

    public TraceContextOrSamplingFlags sampled(boolean sampled) {
        int flags = InternalPropagation.sampled(sampled,value.flags);
        if (flags == value.flags) {
            return this;// save effort if no change
        }
        return withFlags(flags);
    }

    @Nullable
    public TraceContext context() {
        return type == 1 ? (TraceContext) value : null;
    }

    @Nullable
    public TraceIdContext traceIdContext() {
        return type == 2 ? (TraceIdContext) value : null;
    }

    @Nullable
    public SamplingFlags samplingFlags() {
        return type == 3 ? value : null;
    }

    /**
     * Non-empty when {@link #context} is null: A list of additional data extracted from the carrier.
     *
     * @see TraceContext#extra()
     */
    public final List<Object> extra() {
        return extra;
    }

    public final Builder toBuilder() {
        Builder result = new Builder();
        result.type = type;
        result.value = value;
        result.extra = extra;
        return result;
    }

    @Override
    public String toString() {
        return "{value=" + value + ", extra=" + extra + "}";
    }

    public static TraceContextOrSamplingFlags create(TraceContext context) {
        return new TraceContextOrSamplingFlags(1,context,Collections.emptyList());
    }

    public static TraceContextOrSamplingFlags create(TraceIdContext traceIdContext) {
        return new TraceContextOrSamplingFlags(2,traceIdContext,Collections.emptyList());
    }

    public static TraceContextOrSamplingFlags create(SamplingFlags flags) {
        // reuses constants to avoid excess allocation
        if (flags == SamplingFlags.SAMPLED) return SAMPLED;
        if (flags == SamplingFlags.EMPTY) return EMPTY;
        if (flags == SamplingFlags.NOT_SAMPLED) return NOT_SAMPLED;
        if (flags == SamplingFlags.DEBUG) return DEBUG;
        return new TraceContextOrSamplingFlags(3, flags, Collections.emptyList());
    }

    public static TraceContextOrSamplingFlags create(@Nullable Boolean sampled,boolean debug) {
        if (debug) {
            return DEBUG;
        }
        if (sampled == null) {
            return EMPTY;
        }
        return sampled ? SAMPLED : NOT_SAMPLED;
    }

    private final int type;

    private final SamplingFlags value;

    private List<Object> extra;

    TraceContextOrSamplingFlags(int type,SamplingFlags value,List<Object> extra) {
        Assert.notNull(value,"value can not be null");
        Assert.notNull(extra,"extra can not be null");
        this.type = type;
        this.value = value;
        this.extra = extra;
    }

    public static final class Builder {

        int type;

        SamplingFlags value;

        List<Object> extra = Collections.emptyList();

        boolean sampledLocal = false;

        /** @see TraceContextOrSamplingFlags#context() */
        public final Builder context(TraceContext context) {
            Assert.notNull(context,"context can not be null");
            type = 1;
            value = context;
            return this;
        }

        /** @see TraceContextOrSamplingFlags#traceIdContext() */
        public final Builder traceIdContext(TraceIdContext traceIdContext) {
            Assert.notNull(traceIdContext,"traceIdContext can not be null");
            type = 2;
            value = traceIdContext;
            return this;
        }

        /** @see TraceContext#sampledLocal() */
        public Builder sampledLocal() {
            this.sampledLocal = true;
            return this;
        }

        /** @see TraceContextOrSamplingFlags#samplingFlags() */
        public final Builder samplingFlags(SamplingFlags samplingFlags) {
            Assert.notNull(samplingFlags,"samplingFlags can not be null");
            type = 3;
            value = samplingFlags;
            return this;
        }

        /**
         * Shares the input with the builder, replacing any current data in the builder.
         *
         * @see TraceContextOrSamplingFlags#extra()
         */
        public final Builder extra(List<Object> extra) {
            Assert.notNull(extra,"extra can not be null");
            this.extra = extra; // sharing a copy in case it is immutable
            return this;
        }

        /** @see TraceContextOrSamplingFlags#extra() */
        public final Builder addExtra(Object extra) {
            Assert.notNull(extra,"extra can not be null");
            if (!(this.extra instanceof ArrayList)) {
                this.extra = new ArrayList<>(this.extra); // make it mutable
            }
            this.extra.add(extra);
            return this;
        }

        /** Returns an immutable result from the values currently in the builder */
        public final TraceContextOrSamplingFlags build() {
            final TraceContextOrSamplingFlags result;
            if (!extra.isEmpty() && type == 1) {
                TraceContext context = (TraceContext) value;
                if (context.extra().isEmpty()) {
                    context = InternalPropagation.instance.withExtra(context,
                        Lists.ensureImmutable(extra));
                } else {
                    context = InternalPropagation.instance.withExtra(context,
                        Lists.concatImmutableLists(context.extra(),extra));
                }
                result = new TraceContextOrSamplingFlags(type,context,Collections.emptyList());
            } else {
                // make sure the extra data is immutable and unmodifiable
                result = new TraceContextOrSamplingFlags(type,value,Lists.ensureImmutable(extra));
            }

            if (!sampledLocal) {
                return result;
            }
            return result.withFlags(value.flags | FLAG_SAMPLED_LOCAL);
        }

        Builder() {
            // no external implementations
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof TraceContextOrSamplingFlags)) {
            return false;
        }
        TraceContextOrSamplingFlags that = (TraceContextOrSamplingFlags) o;
        return type == that.type && value.equals(that.value) && extra.equals(that.extra);
    }

    @Override
    public int hashCode() {
        int h = 1;
        h *= 1000003;
        h ^= this.type;
        h *= 1000003;
        h ^= this.value.hashCode();
        h *= 1000003;
        h ^= this.extra.hashCode();
        return h;
    }

    private TraceContextOrSamplingFlags withFlags(int flags) {
        switch (type) {
            case 1:
                TraceContext context = InternalPropagation.instance.withFlags((TraceContext) value,flags);
                return new TraceContextOrSamplingFlags(type,context,extra);
            case 2:
                TraceIdContext traceIdContext = idContextWithFlags(flags);
                return new TraceContextOrSamplingFlags(type,traceIdContext,extra);
            case 3:
                SamplingFlags samplingFlags = SamplingFlags.toSamplingFlags(flags);
                if (extra.isEmpty()) {
                    return create(samplingFlags);
                }
                return new TraceContextOrSamplingFlags(type,samplingFlags,extra);
        }
        throw new AssertionError("programming error");
    }

    private TraceIdContext idContextWithFlags(int flags) {
        TraceIdContext traceIdContext = (TraceIdContext) value;
        return new TraceIdContext(flags,traceIdContext.traceIdHigh(),traceIdContext.traceId());
    }
}
