package org.apache.skywalking.apm.agent.core.context;

final class SpanCustomizerShield implements SpanCustomizer {

    private final Span delegate;

    SpanCustomizerShield(Span delegate) {
        this.delegate = delegate;
    }

    @Override
    public SpanCustomizer name(String name) {
        delegate.name(name);
        return this;
    }

    @Override
    public SpanCustomizer tag(String key, String value) {
        delegate.tag(key,value);
        return this;
    }

    @Override
    public SpanCustomizer annotate(String value) {
        delegate.annotate(value);
        return this;
    }

    @Override
    public String toString() {
        return "SpanCustomizer(" + delegate.customizer() + ")";
    }
}
