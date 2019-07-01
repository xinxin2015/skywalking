package org.apache.skywalking.apm.agent.core.context;

public enum NoopSpanCustomizer implements SpanCustomizer {
    INSTANCE;

    @Override
    public SpanCustomizer name(String name) {
        return this;
    }

    @Override
    public SpanCustomizer tag(String key, String value) {
        return this;
    }

    @Override
    public SpanCustomizer annotate(String value) {
        return this;
    }
}
