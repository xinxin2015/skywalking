package org.apache.skywalking.apm.agent.core.context;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.skywalking.apm.agent.core.context.handler.FinishedSpanHandler;
import org.apache.skywalking.apm.agent.core.context.internal.IpLiteral;
import org.apache.skywalking.apm.agent.core.context.internal.Nullable;
import org.apache.skywalking.apm.agent.core.context.internal.Platform;
import org.apache.skywalking.apm.agent.core.context.internal.handler.FinishedSpanHandlers;
import org.apache.skywalking.apm.agent.core.context.internal.handler.ZipkinFinishedSpanHandler;
import org.apache.skywalking.apm.agent.core.context.internal.recorder.PendingSpans;
import org.apache.skywalking.apm.agent.core.context.propagation.B3Propagation;
import org.apache.skywalking.apm.agent.core.context.propagation.CurrentTraceContext;
import org.apache.skywalking.apm.agent.core.context.propagation.Propagation;
import org.apache.skywalking.apm.agent.core.context.propagation.Propagation.Factory;
import org.apache.skywalking.apm.agent.core.context.propagation.Propagation.KeyFactory;
import org.apache.skywalking.apm.agent.core.context.propagation.TraceContext;
import org.apache.skywalking.apm.agent.core.context.sampler.Sampler;
import org.apache.skywalking.apm.agent.core.util.Assert;
import org.apache.skywalking.apm.agent.logging.Log;
import org.apache.skywalking.apm.agent.logging.LogFactory;
import zipkin2.reporter.Reporter;

/**
 * This provides utilities needed for trace instrumentation. For example, a {@link Tracer}.
 *
 * <p>Instances built via {@link #newBuilder()} are registered automatically such that statically
 * configured instrumentation like JDBC drivers can use {@link #current()}.
 *
 * <p>This type can be extended so that the object graph can be built differently or overridden,
 * for example via spring or when mocking.
 */
public abstract class Tracing implements Closeable {

    public static Builder newBuilder() {
        return new Builder();
    }

    /** All tracing commands start with a {@link Span}. Use a tracer to create spans. */
    public abstract Tracer tracer();

    /**
     * When a trace leaves the process, it needs to be propagated, usually via headers. This utility
     * is used to inject or extract a trace context from remote requests.
     */
    // Implementations should override and cache this as a field.
    public Propagation<String> propagation() {
        return propagationFactory().create(KeyFactory.STRING);
    }

    /** This supports edge cases like GRPC Metadata propagation which doesn't use String keys. */
    public abstract Propagation.Factory propagationFactory();

    /**
     * Sampler is responsible for deciding if a particular trace should be "sampled", i.e. whether the
     * overhead of tracing will occur and/or if a trace will be reported to Zipkin.
     *
     * @see Tracer#withSampler(Sampler)
     */
    public abstract Sampler sampler();

    /**
     * This supports in-process propagation, typically across thread boundaries. This includes
     * utilities for concurrent types like {@linkplain java.util.concurrent.ExecutorService}.
     */
    public abstract CurrentTraceContext currentTraceContext();

    /**
     * This exposes the microsecond clock used by operations such as {@link Span#finish()}. This is
     * helpful when you want to time things manually. Notably, this clock will be coherent for all
     * child spans in this trace (that use this tracing component). For example, NTP or system clock
     * changes will not affect the result.
     *
     * @param context references a potentially unstarted span you'd like a clock correlated with
     */
    public final Clock clock(TraceContext context) {
        return tracer().getPendingSpans().getOrCreate(context, false).clock();
    }

    public abstract ErrorParser errorParser();

    // volatile for visibility on get. writes guarded by Tracing.class
    static volatile Tracing current = null;

    /**
     * Returns the most recently created tracer if its component hasn't been closed. null otherwise.
     *
     * <p>This object should not be cached.
     */
    @Nullable
    public static Tracer currentTracer() {
        Tracing tracing = current;
        return tracing != null ? tracing.tracer() : null;
    }

    /**
     * When true, no recording is done and nothing is reported to zipkin. However, trace context is
     * still injected into outgoing requests.
     *
     * @see Span#isNoop()
     */
    public abstract boolean isNoop();

    /**
     * Set true to drop data and only return {@link Span#isNoop() noop spans} regardless of sampling
     * policy. This allows operators to stop tracing in risk scenarios.
     *
     * @see #isNoop()
     */
    public abstract void setNoop(boolean noop);

    public abstract void remove();

    /**
     * Returns the most recently created tracing component iff it hasn't been closed. null otherwise.
     *
     * <p>This object should not be cached.
     */
    @Nullable
    public static Tracing current() {
        return current;
    }

    /** Ensures this component can be garbage collected, by making it not {@link #current()} */
    @Override
    public abstract void close();

    public static final class Builder {

        String localServiceName = "unknown", localIp;
        int localPort;// zero means null
        Reporter<zipkin2.Span> spanReporter;
        Clock clock;
        Sampler sampler = Sampler.ALWAYS_SAMPLE;
        CurrentTraceContext currentTraceContext = CurrentTraceContext.Default.inheritable();
        boolean traceId128Bit = false, supportsJoin = true;
        Propagation.Factory propagationFactory = B3Propagation.FACTORY;
        ErrorParser errorParser = new ErrorParser();
        List<FinishedSpanHandler> finishedSpanHandlers = new ArrayList<>();

        /**
         * Lower-case label of the remote node in the service graph, such as "favstar". Avoid names with
         * variables or unique identifiers embedded. Defaults to "unknown".
         *
         * <p>This is a primary label for trace lookup and aggregation, so it should be intuitive and
         * consistent. Many use a name from service discovery.
         *
         * @see #localIp(String)
         */
        public Builder localServiceName(String localServiceName) {
            Assert.hasLength(localServiceName, "localServiceName is empty");
            this.localServiceName = localServiceName.toLowerCase(Locale.ROOT);
            return this;
        }

        /**
         * The text representation of the primary IP address associated with this service. Ex.
         * 192.168.99.100 or 2001:db8::c001. Defaults to a link local IP.
         *
         * @see #localServiceName(String)
         * @see #localPort(int)
         * @since 5.2
         */
        public Builder localIp(String localIp) {
            String maybeIp = IpLiteral.ipOrNull(localIp);
            Assert.notNull(maybeIp, localIp + " is not a valid IP");
            this.localIp = maybeIp;
            return this;
        }

        /**
         * The primary listen port associated with this service. No default.
         *
         * @see #localIp(String)
         * @since 5.2
         */
        public Builder localPort(int localPort) {
            Assert.state(localPort <= 0xffff, "invalid localPort " + localPort);
            if (localPort < 0) {
                localPort = 0;
            }
            this.localPort = localPort;
            return this;
        }

        /**
         * Controls how spans are reported. Defaults to logging, but often an
         * {@link AsyncReporter}
         * which batches spans before sending to Zipkin.
         *
         * The {@link AsyncReporter} includes a {@link Sender}, which is a driver for transports like
         * http, kafka and scribe.
         *
         * <p>For example, here's how to batch send spans via http:
         *
         * <pre>{@code
         * spanReporter = AsyncReporter.create(URLConnectionSender.create("http://localhost:9411/api/v2/spans"));
         *
         * tracingBuilder.spanReporter(spanReporter);
         * }</pre>
         *
         * <p>See https://github.com/apache/incubator-zipkin-reporter-java
         */
        public Builder spanReporter(Reporter<zipkin2.Span> spanReporter) {
            Assert.notNull(spanReporter, "spanReporter can not be null");
            this.spanReporter = spanReporter;
            return this;
        }

        /**
         * Assigns microsecond-resolution timestamp source for operations like {@link Span#start()}.
         * Defaults to JRE-specific platform time.
         *
         * <p>Note: timestamps are read once per trace, then {@link System#nanoTime() ticks}
         * thereafter. This ensures there's no clock skew problems inside a single trace.
         *
         * See {@link Tracing#clock(TraceContext)}
         */
        public Builder clock(Clock clock) {
            Assert.notNull(clock, "clock can not be null");
            this.clock = clock;
            return this;
        }

        /**
         * Sampler is responsible for deciding if a particular trace should be "sampled", i.e. whether
         * the overhead of tracing will occur and/or if a trace will be reported to Zipkin.
         *
         * @see Tracer#withSampler(Sampler) for temporary overrides
         */
        public Builder sampler(Sampler sampler) {
            Assert.notNull(sampler, "sampler can not be null");
            this.sampler = sampler;
            return this;
        }

        /**
         * Responsible for implementing {@link Tracer#startScopedSpan(String)}, {@link
         * Tracer#currentSpanCustomizer()}, {@link Tracer#currentSpan()} and {@link
         * Tracer#withSpanInScope(Span)}.
         *
         * <p>By default a simple thread-local is used. Override to support other mechanisms or to
         * synchronize with other mechanisms such as SLF4J's MDC.
         */
        public Builder currentTraceContext(CurrentTraceContext currentTraceContext) {
            Assert.notNull(currentTraceContext, "currentTraceContext can not be null");
            this.currentTraceContext = currentTraceContext;
            return this;
        }

        /**
         * Controls how trace contexts are injected or extracted from remote requests, such as from http
         * headers. Defaults to {@link B3Propagation#FACTORY}
         */
        public Builder propagationFactory(Propagation.Factory propagationFactory) {
            Assert.notNull(propagationFactory, "propagationFactory can not be null");
            this.propagationFactory = propagationFactory;
            return this;
        }

        /** When true, new root spans will have 128-bit trace IDs. Defaults to false (64-bit) */
        public Builder traceId128Bit(boolean traceId128Bit) {
            this.traceId128Bit = traceId128Bit;
            return this;
        }

        /**
         * True means the tracing system supports sharing a span ID between a {@link Span.Kind#CLIENT}
         * and {@link Span.Kind#SERVER} span. Defaults to true.
         *
         * <p>Set this to false when the tracing system requires the opposite. For example, if
         * ultimately spans are sent to Amazon X-Ray or Google Stackdriver Trace, you should set this to
         * false.
         *
         * <p>This is implicitly set to false when {@link Propagation.Factory#supportsJoin()} is false,
         * as in that case, sharing IDs isn't possible anyway.
         *
         * @see Propagation.Factory#supportsJoin()
         */
        public Builder supportsJoin(boolean supportsJoin) {
            this.supportsJoin = supportsJoin;
            return this;
        }

        public Builder errorParser(ErrorParser errorParser) {
            this.errorParser = errorParser;
            return this;
        }

        /**
         * Similar to {@link #spanReporter(Reporter)} except it can read the trace context and create
         * more efficient or completely different data structures. Importantly, the input is mutable for
         * customization purposes.
         *
         * <p>These handlers execute before the {@link #spanReporter(Reporter) span reporter}, which
         * means any mutations occur prior to Zipkin.
         *
         * <h3>Advanced notes</h3>
         *
         * <p>This is named firehose as it can receive data even when spans are not sampled remotely.
         * For example, {@link FinishedSpanHandler#alwaysSampleLocal()} will generate data for all traced
         * requests while not affecting headers. This setting is often used for metrics aggregation.
         *
         *
         * <p>Your handler can also be a custom span transport. When this is the case, set the {@link
         * #spanReporter(Reporter) span reporter} to {@link Reporter#NOOP} to avoid redundant conversion
         * overhead.
         */
        public Builder addFinishedSpanHandler(FinishedSpanHandler finishedSpanHandler) {
            Assert.notNull(finishedSpanHandler, "finishedSpanHandler can not be null");
            this.finishedSpanHandlers.add(finishedSpanHandler);
            return this;
        }

        public Tracing build() {
            if (clock == null) {
                clock = Platform.get().clock();
            }
            if (localIp == null) {
                localIp = Platform.get().linkLocalIp();
            }
            if (spanReporter == null) {
                spanReporter = new LoggingReporter();
            }
            return new Default(this);
        }
    }

    static final class LoggingReporter implements Reporter<zipkin2.Span> {

        final Log logger = LogFactory.getLog(LoggingReporter.class);

        @Override
        public void report(zipkin2.Span span) {
            Assert.notNull(span, "span can not be null");
            if (!logger.isInfoEnabled()) {
                return;
            }
            logger.info(span.toString());
        }
    }

    static final class Default extends Tracing {

        //private final Tracer tracer;
        private final Propagation.Factory propagationFactory;
        private final Propagation<String> stringPropagation;
        private final CurrentTraceContext currentTraceContext;
        private final Sampler sampler;
        private final Clock clock;
        private final ErrorParser errorParser;
        private final AtomicBoolean noop;
        private final Builder builder;
        private final ThreadLocal<Tracer> tracerThreadLocal;
        private FinishedSpanHandler zipkinFirehose = FinishedSpanHandler.NOOP;
        private FinishedSpanHandler finishedSpanHandler;

        Default(Builder builder) {
            this.builder = builder;
            this.tracerThreadLocal = new ThreadLocal<>();
            this.clock = builder.clock;
            this.errorParser = builder.errorParser;
            this.propagationFactory = builder.propagationFactory;
            this.stringPropagation = builder.propagationFactory.create(KeyFactory.STRING);
            this.currentTraceContext = builder.currentTraceContext;
            this.sampler = builder.sampler;
            this.noop = new AtomicBoolean();

            List<FinishedSpanHandler> finishedSpanHandlers = builder.finishedSpanHandlers;

            // If a Zipkin reporter is present, it is invoked after the user-supplied finished
            // span handlers.

            if (builder.spanReporter != Reporter.NOOP) {
                zipkinFirehose = new ZipkinFinishedSpanHandler(builder.spanReporter, errorParser,
                    builder.localServiceName, builder.localIp, builder.localPort);
                finishedSpanHandlers = new ArrayList<>(finishedSpanHandlers);
                finishedSpanHandlers.add(zipkinFirehose);
            }

            // Compose the handlers into one which honors Tracing.noop
            finishedSpanHandler = FinishedSpanHandlers
                .noopAware(FinishedSpanHandlers.compose(finishedSpanHandlers), noop);
            maybeSetCurrent();
        }

        @Override
        public Tracer tracer() {
            Tracer tracer = tracerThreadLocal.get();
            if (tracer == null) {
                tracer = new Tracer(builder.clock, builder.propagationFactory, finishedSpanHandler
                    , new PendingSpans(clock, zipkinFirehose, noop), builder.sampler,
                    builder.currentTraceContext,
                    builder.traceId128Bit || propagationFactory.requires128BitTraceId(),
                    builder.supportsJoin && propagationFactory.supportsJoin(),
                    finishedSpanHandler.alwaysSampleLocal(), noop);
                tracerThreadLocal.set(tracer);
            }
            return tracer;
        }

        @Override
        public Propagation<String> propagation() {
            return stringPropagation;
        }

        @Override
        public Factory propagationFactory() {
            return propagationFactory;
        }

        @Override
        public Sampler sampler() {
            return sampler;
        }

        @Override
        public CurrentTraceContext currentTraceContext() {
            return currentTraceContext;
        }

        @Override
        public ErrorParser errorParser() {
            return errorParser;
        }

        @Override
        public boolean isNoop() {
            return noop.get();
        }

        @Override
        public void setNoop(boolean noop) {
            this.noop.set(noop);
        }

        @Override
        public void remove() {
            tracerThreadLocal.remove();
        }

        private void maybeSetCurrent() {
            if (current != null) {
                return;
            }
            synchronized (Tracing.class) {
                if (current == null) {
                    current = this;
                }
            }
        }

        @Override
        public void close() {
            if (current != this) {
                return;
            }
            // don't blindly set most recent to null as there could be a race
            synchronized (Tracing.class) {
                if (current == this) {
                    current = null;
                }
            }
        }
    }

    Tracing() { // intentionally hidden constructor
    }
}
