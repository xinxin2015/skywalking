package org.apache.skywalking.apm.agent.core.context;


/**
 * Simple interface users can customize a span with. For example, this can add custom tags useful
 * in looking up spans.
 *
 * <p>This type is safer to expose directly to users than {@link Span}, as it has no hooks that
 * can affect the span lifecycle.
 *
 * <p>While unnecessary when tagging constants, guard potentially expensive operations on the
 * {@link NoopSpanCustomizer} type.
 *
 * <p>Ex.
 * <pre>{@code
 * if (!(customizer instanceof NoopSpanCustomizer)) {
 *   customizer.tag("summary", computeSummary());
 * }
 * }</pre>
 */
// Note: this is exposed to users. We cannot add methods to this until Java 8 is required or we do a
// major version bump
// BRAVE6: add isNoop to avoid instanceof checks
// BRAVE6: add error to support error handling
public interface SpanCustomizer {

    /**
     * Sets the string name for the logical operation this span represents.
     */
    SpanCustomizer name(String name);

    /**
     * Tags give your span context for search, viewing and analysis. For example, a key
     * "your_app.version" would let you lookup spans by version. A tag "sql.query" isn't searchable,
     * but it can help in debugging when viewing a trace.
     *
     * @param key Name used to lookup spans, such as "your_app.version".
     * @param value String value, cannot be <code>null</code>.
     */
    SpanCustomizer tag(String key,String value);

    /**
     * Associates an event that explains latency with the current system time.
     *
     * @param value A short tag indicating the event, like "finagle.retry"
     */
    SpanCustomizer annotate(String value);

}
