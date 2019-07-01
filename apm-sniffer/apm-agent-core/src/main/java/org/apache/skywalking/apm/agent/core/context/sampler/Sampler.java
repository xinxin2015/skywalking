package org.apache.skywalking.apm.agent.core.context.sampler;

public abstract class Sampler {

    public static final Sampler ALWAYS_SAMPLE = new Sampler() {
        @Override
        public boolean isSampled(long traceId) {
            return true;
        }

        @Override
        public String toString() {
            return "AlwaysSample";
        }
    };

    public static final Sampler NEVER_SAMPLE = new Sampler() {
        @Override
        public boolean isSampled(long traceId) {
            return false;
        }

        @Override
        public String toString() {
            return "NeverSample";
        }
    };

    /**
     * Returns true if the trace ID should be measured.
     *
     * @param traceId The trace ID to be decided on, can be ignored
     */
    public abstract boolean isSampled(long traceId);

    /**
     * Returns a sampler, given a rate expressed as a percentage.
     *
     * <p>The sampler returned is good for low volumes of traffic (<100K requests), as it is precise.
     * If you have high volumes of traffic, consider {@link BoundarySampler}.
     *
     * @param rate minimum sample rate is 0.01, or 1% of traces
     */
    public static Sampler create(float rate) {
        return CountingSampler.create(rate);
    }

}
