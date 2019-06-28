package org.apache.skywalking.apm.agent.core.context.propagation;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.skywalking.apm.agent.core.context.propagation.B3SinglePropagation.B3SingleExtractor;
import org.apache.skywalking.apm.agent.core.context.propagation.TraceContext.Extractor;
import org.apache.skywalking.apm.agent.core.context.propagation.TraceContext.Injector;
import org.apache.skywalking.apm.agent.core.util.Assert;


/**
 * Implements <a href="https://github.com/apache/incubator-zipkin-b3-propagation">B3 Propagation</a>
 */
public class B3Propagation<K> implements Propagation<K> {

    public static final Propagation.Factory FACTORY = new Factory() {
        @Override
        public <K> Propagation<K> create(KeyFactory<K> factory) {
            return new B3Propagation<>(factory);
        }

        @Override
        public boolean supportsJoin() {
            return true;
        }

        @Override
        public String toString() {
            return "B3PropagationFactory";
        }
    };

    /**
     * 128 or 64-bit trace ID lower-hex encoded into 32 or 16 characters (required)
     */
    private static final String TRACE_ID_NAME = "X-B3-TraceId";

    /**
     * 64-bit span ID lower-hex encoded into 16 characters (required)
     */
    private static final String SPAN_ID_NAME = "X-B3-SpanId";

    /**
     * 64-bit parent span ID lower-hex encoded into 16 characters (absent on root span)
     */
    private static final String PARENT_SPAN_ID_NAME = "X-B3-ParentSpanId";

    /**
     * "1" means report this span to the tracing system, "0" means do not. (absent means defer the
     * decision to the receiver of this header).
     */
    private static final String SAMPLE_NAME = "X-B3-Sampled";

    /**
     * "1" implies sampled and is a request to override collection-tier sampling policy.
     */
    private static final String FLAGS_NAME = "X-B3-Flags";

    private final K b3Key,traceIdKey,spanIdKey,parentSpanIdKey,sampledKey,debugKey;

    private final List<K> fields;

    private B3Propagation(KeyFactory<K> factory) {
        this.b3Key = factory.create("b3");
        this.traceIdKey = factory.create(TRACE_ID_NAME);
        this.spanIdKey = factory.create(SPAN_ID_NAME);
        this.parentSpanIdKey = factory.create(PARENT_SPAN_ID_NAME);
        this.sampledKey = factory.create(SAMPLE_NAME);
        this.debugKey = factory.create(FLAGS_NAME);
        this.fields = Collections.unmodifiableList(Arrays.asList(b3Key,traceIdKey,spanIdKey,
            parentSpanIdKey,sampledKey,debugKey));
    }

    @Override
    public List<K> keys() {
        return this.fields;
    }

    @Override
    public <C> Injector<C> injector(Setter<C, K> setter) {
        return new B3Injector<>(this,setter);
    }

    static final class B3Injector<C,K> implements Injector<C> {

        final B3Propagation<K> propagation;

        final Setter<C,K> setter;

        B3Injector(B3Propagation<K> propagation,Setter<C,K> setter) {
            this.propagation = propagation;
            this.setter = setter;
        }

        @Override
        public void inject(TraceContext traceContext, C carrier) {
            setter.put(carrier,propagation.traceIdKey,traceContext.traceIdString());
            setter.put(carrier,propagation.spanIdKey,traceContext.spanIdString());
            String parentId = traceContext.parentIdString();
            if (parentId != null) {
                setter.put(carrier,propagation.parentSpanIdKey,parentId);
            }
            if (traceContext.debug()) {
                setter.put(carrier,propagation.debugKey,"1");
            } else if (traceContext.sampled() != null) {
                setter.put(carrier,propagation.sampledKey,traceContext.sampled() ? "1" : "0");
            }
        }
    }

    @Override
    public <C> Extractor<C> extractor(Getter<C, K> getter) {
        return new B3Extractor<>(this,getter);
    }

    static final class B3Extractor<C,K> implements Extractor<C> {

        final B3Propagation<K> propagation;

        final B3SingleExtractor<C,K> singleExtractor;

        final Getter<C,K> getter;

        B3Extractor(B3Propagation<K> propagation,Getter<C,K> getter) {
            this.propagation = propagation;
            this.singleExtractor = new B3SingleExtractor<>(propagation.b3Key,getter);
            this.getter = getter;
        }

        @Override
        public TraceContextOrSamplingFlags extract(C carrier) {
            Assert.notNull(carrier,"carrier can not be null");

            // try to extract single-header format
            TraceContextOrSamplingFlags extracted = singleExtractor.extract(carrier);
            if (!TraceContextOrSamplingFlags.EMPTY.equals(extracted)) {
                return extracted;
            }

            // Start by looking at the sampled state as this is used regardless
            // Official sampled value is 1, though some old instrumentation send true
            String sampled = getter.get(carrier,propagation.sampledKey);
            Boolean sampledV = sampled != null
                ? sampled.equals("1") || sampled.equalsIgnoreCase("true")
                : null;
            boolean debug = "1".equals(getter.get(carrier, propagation.debugKey));

            String traceIdString = getter.get(carrier, propagation.traceIdKey);
            // It is ok to go without a trace ID, if sampling or debug is set
            if (traceIdString == null) {
                return TraceContextOrSamplingFlags.create(sampledV,debug);
            }

            // try parse the trace IDs into the context.
            TraceContext.Builder result = TraceContext.newBuilder();
            if (result.parseTraceId(traceIdString, propagation.traceIdKey)
                && result.parseSpanId(getter, carrier, propagation.spanIdKey)
                && result.parseParentId(getter, carrier, propagation.parentSpanIdKey)) {
                if (sampledV != null) {
                    result.sampled(sampledV.booleanValue());
                }
                if (debug) {
                    result.debug(true);
                }
                return TraceContextOrSamplingFlags.create(result.build());
            }
            return TraceContextOrSamplingFlags.EMPTY; // trace context is malformed so return empty
        }
    }
}
