package org.apache.skywalking.apm.agent.core.context.propagation;

import java.util.Collections;
import java.util.List;
import org.apache.skywalking.apm.agent.core.context.propagation.TraceContext.Extractor;
import org.apache.skywalking.apm.agent.core.context.propagation.TraceContext.Injector;
import org.apache.skywalking.apm.agent.core.util.Assert;

/** Implements the propagation format described in {@link B3SingleFormat}. */
public class B3SinglePropagation<K> implements Propagation<K> {

    public static final Factory FACTORY = new Factory() {
        @Override
        public <K> Propagation<K> create(KeyFactory<K> factory) {
            return new B3SinglePropagation<>(factory);
        }

        @Override
        public boolean supportsJoin() {
            return true;
        }

        @Override
        public String toString() {
            return "B3SinglePropagationFactory";
        }
    };

    private final K b3Key;

    private final List<K> fields;

    private B3SinglePropagation(KeyFactory<K> factory) {
        this.b3Key = factory.create("b3");
        this.fields = Collections.unmodifiableList(Collections.singletonList(this.b3Key));
    }

    @Override
    public List<K> keys() {
        return fields;
    }

    @Override
    public <C> Injector<C> injector(Setter<C, K> setter) {
        return new B3SingleInjector<>(this,setter);
    }

    static final class B3SingleInjector<C,K> implements Injector<C> {

        private final B3SinglePropagation<K> propagation;

        private final Setter<C,K> setter;

        B3SingleInjector(B3SinglePropagation<K> propagation,Setter<C,K> setter) {
            this.propagation = propagation;
            this.setter = setter;
        }

        @Override
        public void inject(TraceContext traceContext, C carrier) {
            setter.put(carrier,propagation.b3Key,B3SingleFormat.writeB3SingleFormat(traceContext));
        }
    }

    @Override
    public <C> Extractor<C> extractor(Getter<C, K> getter) {
        return new B3SingleExtractor<>(this.b3Key,getter);
    }

    static final class B3SingleExtractor<C,K> implements Extractor<C> {

        private final K b3Key;

        private final Getter<C,K> getter;

        B3SingleExtractor(K b3Key,Getter<C,K> getter) {
            this.b3Key = b3Key;
            this.getter = getter;
        }

        @Override
        public TraceContextOrSamplingFlags extract(C carrier) {
            Assert.notNull(carrier,"carrier can not be null");
            String b3 = getter.get(carrier,b3Key);
            if (b3 == null) {
                return TraceContextOrSamplingFlags.EMPTY;
            }
            TraceContextOrSamplingFlags extracted = B3SingleFormat.parseB3SingleFormat(b3);
            // if null, the trace context is malformed so return empty
            if (extracted == null) {
                return TraceContextOrSamplingFlags.EMPTY;
            }
            return extracted;
        }
    }
}
