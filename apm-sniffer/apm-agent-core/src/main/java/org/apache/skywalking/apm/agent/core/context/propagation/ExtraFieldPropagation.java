package org.apache.skywalking.apm.agent.core.context.propagation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.skywalking.apm.agent.core.context.Tracing;
import org.apache.skywalking.apm.agent.core.context.internal.Nullable;
import org.apache.skywalking.apm.agent.core.context.internal.PredefinedPropagationFields;
import org.apache.skywalking.apm.agent.core.context.internal.PropagationFields;
import org.apache.skywalking.apm.agent.core.context.internal.PropagationFieldsFactory;
import org.apache.skywalking.apm.agent.core.context.propagation.TraceContext.Extractor;
import org.apache.skywalking.apm.agent.core.context.propagation.TraceContext.Injector;
import org.apache.skywalking.apm.agent.core.util.Assert;

/**
 * Allows you to propagate predefined request-scoped fields, usually but not always HTTP headers.
 *
 * <p>For example, if you are in a Cloud Foundry environment, you might want to pass the request
 * ID:
 * <pre>{@code
 * // when you initialize the builder, define the extra field you want to propagate
 * tracingBuilder.propagationFactory(
 *   ExtraFieldPropagation.newFactory(B3Propagation.FACTORY, "x-vcap-request-id")
 * );
 *
 * // later, you can tag that request ID or use it in log correlation
 * requestId = ExtraFieldPropagation.get("x-vcap-request-id");
 *
 * // You can also set or override the value similarly, which might be needed if a new request
 * ExtraFieldPropagation.get("x-country-code", "FO");
 * }</pre>
 *
 * <p>You may also need to propagate a trace context you aren't using. For example, you may be in
 * an Amazon Web Services environment, but not reporting data to X-Ray. To ensure X-Ray can co-exist
 * correctly, pass-through its tracing header like so.
 *
 * <pre>{@code
 * tracingBuilder.propagationFactory(
 *   ExtraFieldPropagation.newFactory(B3Propagation.FACTORY, "x-amzn-trace-id")
 * );
 * }</pre>
 *
 * <p>You can also prefix fields, if they follow a common pattern. For example, the following will
 * propagate the field "x-vcap-request-id" as-is, but send the fields "country-code" and "user-id"
 * on the wire as "baggage-country-code" and "baggage-user-id" respectively.
 *
 * <pre>{@code
 * // Setup your tracing instance with allowed fields
 * tracingBuilder.propagationFactory(
 *   ExtraFieldPropagation.newFactoryBuilder(B3Propagation.FACTORY)
 *                        .addField("x-vcap-request-id")
 *                        .addPrefixedFields("baggage-", Arrays.asList("country-code", "user-id"))
 *                        .build()
 * );
 *
 * // Later, you can call below to affect the country code of the current trace context
 * ExtraFieldPropagation.set("country-code", "FO");
 * String countryCode = ExtraFieldPropagation.get("country-code");
 *
 * // Or, if you have a reference to a trace context, use it explicitly
 * ExtraFieldPropagation.set(span.context(), "country-code", "FO");
 * String countryCode = ExtraFieldPropagation.get(span.context(), "country-code");
 * }</pre>
 */
public final class ExtraFieldPropagation<K> implements Propagation<K> {

    /**
     * Wraps an underlying propagation implementation, pushing one or more fields
     */
    public static Factory newFactory(Propagation.Factory delegate, String... fieldNames) {
        Assert.notNull(delegate, "delegate can not be null");
        Assert.notNull(fieldNames, "fieldNames can not be null");
        String[] validated = ensureLowerCase(new LinkedHashSet<>(Arrays.asList(fieldNames)));
        return new Factory(delegate, validated, validated);
    }

    /**
     * Wraps an underlying propagation implementation, pushing one or more fields
     */
    public static Factory newFactory(Propagation.Factory delegate,
        Collection<String> fieldNames) {
        Assert.notNull(delegate, "delegate can not be null");
        Assert.notNull(fieldNames, "fieldNames can not be null");
        String[] validated = ensureLowerCase(new LinkedHashSet<>(fieldNames));
        return new Factory(delegate, validated, validated);
    }

    public static FactoryBuilder newFactoryBuilder(Propagation.Factory delegate) {
        return new FactoryBuilder(delegate);
    }

    public static final class FactoryBuilder {

        final Propagation.Factory delegate;
        final Set<String> fieldNames = new LinkedHashSet<>();
        final Map<String, String[]> prefixedNames = new LinkedHashMap<>();

        FactoryBuilder(Propagation.Factory delegate) {
            Assert.notNull(delegate, "delegate can not be null");
            this.delegate = delegate;
        }

        /**
         * Adds a field that is referenced the same in-process as it is on the wire. For example,
         * the name "x-vcap-request-id" would be set as-is including the prefix.
         *
         * <p>Note: {@code fieldName} will be implicitly lower-cased.
         */
        public FactoryBuilder addField(String fieldName) {
            Assert.hasLength(fieldName, "fieldName can not be null");
            fieldName = fieldName.trim();
            fieldNames.add(fieldName.toLowerCase(Locale.ROOT));
            return this;
        }

        /**
         * Adds a prefix when fields are extracted or injected from headers. For example, if the
         * prefix is "baggage-", the field "country-code" would end up as "baggage-country-code" on
         * the wire.
         *
         * <p>Note: any {@code fieldNames} will be implicitly lower-cased.
         */
        public FactoryBuilder addPrefixedFields(String prefix, Collection<String> fieldNames) {
            Assert.hasLength(prefix, "prefix can not be null");
            Assert.notNull(fieldNames, "fieldNames can not be null");
            prefixedNames.put(prefix, ensureLowerCase(new LinkedHashSet<>(fieldNames)));
            return this;
        }

        public Factory build() {
            if (prefixedNames.isEmpty()) {
                String[] validated = ensureLowerCase(fieldNames);
                return new Factory(delegate, validated, validated);
            }
            List<String> fields = new ArrayList<>(), keys = new ArrayList<>();
            List<Integer> keyToFieldList = new ArrayList<>();
            if (!fieldNames.isEmpty()) {
                List<String> validated = Arrays.asList(ensureLowerCase(fieldNames));
                for (int i = 0, length = validated.size(); i < length; i++) {
                    String nextFieldName = validated.get(i);
                    fields.add(nextFieldName);
                    keys.add(nextFieldName);
                    keyToFieldList.add(i);
                }
            }
            for (Map.Entry<String, String[]> entry : prefixedNames.entrySet()) {
                String nextPrefix = entry.getKey();
                String[] nextFieldNames = entry.getValue();
                for (int i = 0; i < nextFieldNames.length; i++) {
                    String nextFieldName = nextFieldNames[i];
                    int index = fields.indexOf(nextFieldName);
                    if (index == -1) {
                        index = fields.size();
                        fields.add(nextFieldName);
                    }
                    keys.add(nextPrefix + nextFieldName);
                    keyToFieldList.add(index);
                }
            }
            int keysLength = keys.size();
            int[] keyToField = new int[keysLength];
            for (int i = 0; i < keysLength; i++) {
                keyToField[i] = keyToFieldList.get(i);
            }
            return new Factory(delegate, fields.toArray(new String[0]), keys.toArray(new String[0]),
                keyToField);
        }
    }

    /**
     * Synonym for {@link #get(String)}
     */
    @Nullable
    public static String current(String name) {
        return get(name);
    }

    /**
     * Returns the value of the field with the specified key or null if not available.
     *
     * <p>Prefer {@link #get(TraceContext, String)} if you have a reference to a span.
     */
    @Nullable
    public static String get(String name) {
        TraceContext context = currentTraceContext();
        return context != null ? get(context, name) : null;
    }

    /**
     * Sets the current value of the field with the specified key, or drops if not a configured
     * field.
     *
     * <p>Prefer {@link #set(TraceContext, String, String)} if you have a reference to a span.
     */
    public static void set(String name, String value) {
        TraceContext context = currentTraceContext();
        if (context != null) {
            set(context, name, value);
        }
    }

    /**
     * Returns a mapping of fields in the current trace context, or empty if there are none.
     *
     * <p>Prefer {@link #set(TraceContext, String, String)} if you have a reference to a span.
     */
    public static Map<String, String> getAll() {
        TraceContext context = currentTraceContext();
        if (context == null) {
            return Collections.emptyMap();
        }
        return getAll(context);
    }

    /**
     * Returns a mapping of any fields in the extraction result.
     */
    public static Map<String, String> getAll(TraceContextOrSamplingFlags extracted) {
        if (extracted == null) {
            throw new NullPointerException("extracted == null");
        }
        TraceContext extractedContext = extracted.context();
        if (extractedContext != null) {
            return getAll(extractedContext);
        }
        PropagationFields fields = TraceContext.findExtra(Extra.class, extracted.extra());
        return fields != null ? fields.toMap() : Collections.emptyMap();
    }

    /**
     * Returns a mapping of any fields in the trace context.
     */
    public static Map<String, String> getAll(TraceContext context) {
        if (context == null) {
            throw new NullPointerException("context == null");
        }
        PropagationFields fields = context.findExtra(Extra.class);
        return fields != null ? fields.toMap() : Collections.emptyMap();
    }

    @Nullable
    private static TraceContext currentTraceContext() {
        Tracing tracing = Tracing.current();
        return tracing != null ? tracing.currentTraceContext().get() : null;
    }

    /**
     * Returns the value of the field with the specified key or null if not available
     */
    @Nullable
    public static String get(TraceContext context, String name) {
        return PropagationFields.get(context, lowercase(name), Extra.class);
    }

    /**
     * Sets the value of the field with the specified key, or drops if not a configured field
     */
    public static void set(TraceContext context, String name, String value) {
        PropagationFields.put(context, lowercase(name), value, Extra.class);
    }

    public static final class Factory extends Propagation.Factory {

        final Propagation.Factory delegate;
        final String[] fieldNames;
        final String[] keyNames;
        final int[] keyToField;
        final ExtraFactory extraFactory;

        Factory(Propagation.Factory delegate, String[] fieldNames, String[] keyNames) {
            this(delegate, fieldNames, keyNames, keyToField(keyNames));
        }

        /**
         * We have a key to field mapping as there may be multiple propagation keys that reference
         * the same field. For example, "baggage-userid" and "baggage_userid".
         */
        static int[] keyToField(String[] keyNames) {
            int[] result = new int[keyNames.length];
            for (int i = 0; i < result.length; i++) {
                result[i] = i;
            }
            return result;
        }

        Factory(Propagation.Factory delegate, String[] fieldNames, String[] keyNames,
            int[] keyToField) {
            this.delegate = delegate;
            this.keyToField = keyToField;
            this.fieldNames = fieldNames;
            this.keyNames = keyNames;
            this.extraFactory = new ExtraFactory(fieldNames);
        }

        @Override
        public boolean supportsJoin() {
            return delegate.supportsJoin();
        }

        @Override
        public boolean requires128BitTraceId() {
            return delegate.requires128BitTraceId();
        }

        @Override
        public final <K> ExtraFieldPropagation<K> create(Propagation.KeyFactory<K> keyFactory) {
            int length = keyNames.length;
            List<K> keys = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                keys.add(keyFactory.create(keyNames[i]));
            }
            return new ExtraFieldPropagation<>(this, keyFactory, keys);
        }

        @Override
        public TraceContext decorate(TraceContext context) {
            TraceContext result = delegate.decorate(context);
            return extraFactory.decorate(result);
        }
    }

    private final Factory factory;
    private final Propagation<K> delegate;
    private final List<K> keys;

    private ExtraFieldPropagation(Factory factory, Propagation.KeyFactory<K> keyFactory,
        List<K> keys) {
        this.factory = factory;
        this.delegate = factory.delegate.create(keyFactory);
        this.keys = keys;
    }

    /**
     * Returns the extra keys this component can extract. This result is lowercase and does not
     * include any {@link #keys() trace context keys}.
     */
    // This is here to support extraction from carriers missing a get field by name function. The
    // only
    // known example is OpenTracing TextMap https://github
    // .com/opentracing/opentracing-java/issues/305
    public List<K> extraKeys() {
        return keys;
    }

    /**
     * Only returns trace context keys. Extra field names are not returned to ensure tools don't
     * delete them. This is to support users accessing extra fields without Brave apis (ex via
     * headers).
     */
    @Override
    public List<K> keys() {
        return delegate.keys();
    }

    @Override
    public <C> Injector<C> injector(Setter<C, K> setter) {
        return new ExtraFieldInjector<>(this, setter);
    }

    @Override
    public <C> Extractor<C> extractor(Getter<C, K> getter) {
        return new ExtraFieldExtractor<>(this, getter);
    }

    static final class ExtraFieldInjector<C, K> implements Injector<C> {

        final ExtraFieldPropagation<K> propagation;
        final Injector<C> delegate;
        final Propagation.Setter<C, K> setter;

        ExtraFieldInjector(ExtraFieldPropagation<K> propagation, Setter<C, K> setter) {
            this.propagation = propagation;
            this.delegate = propagation.delegate.injector(setter);
            this.setter = setter;
        }

        @Override
        public void inject(TraceContext traceContext, C carrier) {
            delegate.inject(traceContext, carrier);
            Extra extra = traceContext.findExtra(Extra.class);
            if (extra == null) {
                return;
            }
            inject(extra, carrier);
        }

        void inject(Extra fields, C carrier) {
            for (int i = 0, length = propagation.keys.size(); i < length; i++) {
                String maybeValue = fields.get(propagation.factory.keyToField[i]);
                if (maybeValue == null) {
                    continue;
                }
                setter.put(carrier, propagation.keys.get(i), maybeValue);
            }
        }
    }

    static final class ExtraFieldExtractor<C, K> implements Extractor<C> {

        final ExtraFieldPropagation<K> propagation;
        final Extractor<C> delegate;
        final Propagation.Getter<C, K> getter;

        ExtraFieldExtractor(ExtraFieldPropagation<K> propagation, Getter<C, K> getter) {
            this.propagation = propagation;
            this.delegate = propagation.delegate.extractor(getter);
            this.getter = getter;
        }

        @Override
        public TraceContextOrSamplingFlags extract(C carrier) {
            TraceContextOrSamplingFlags result = delegate.extract(carrier);

            // always allocate in case fields are added late
            Extra fields = propagation.factory.extraFactory.create();
            for (int i = 0, length = propagation.keys.size(); i < length; i++) {
                String maybeValue = getter.get(carrier, propagation.keys.get(i));
                if (maybeValue == null) {
                    continue;
                }
                fields.put(propagation.factory.keyToField[i], maybeValue);
            }
            return result.toBuilder().addExtra(fields).build();
        }
    }

    private static String[] ensureLowerCase(Collection<String> names) {
        if (names.isEmpty()) {
            throw new IllegalArgumentException("names is empty");
        }
        Iterator<String> nextName = names.iterator();
        String[] result = new String[names.size()];
        for (int i = 0; nextName.hasNext(); i++) {
            String name = nextName.next();
            if (name == null) {
                throw new NullPointerException("names[" + i + "] == null");
            }
            name = name.trim();
            if (name.isEmpty()) {
                throw new IllegalArgumentException("names[" + i + "] is empty");
            }
            result[i] = name.toLowerCase(Locale.ROOT);
        }
        return result;
    }

    static final class ExtraFactory extends PropagationFieldsFactory<Extra> {

        final String[] fieldNames;

        ExtraFactory(String[] fieldNames) {
            this.fieldNames = fieldNames;
        }

        @Override
        public Class<Extra> type() {
            return Extra.class;
        }

        @Override
        protected Extra create() {
            return new Extra(fieldNames);
        }

        @Override
        protected Extra create(Extra parent) {
            return new Extra(parent, fieldNames);
        }

        @Override
        protected TraceContext contextWithExtra(TraceContext context, List<Object> extra) {
            return context.withExtra(extra); // more efficient
        }
    }

    static final class Extra extends PredefinedPropagationFields {

        Extra(String... fieldNames) {
            super(fieldNames);
        }

        Extra(Extra parent, String... fieldNames) {
            super(parent, fieldNames);
        }
    }

    private static String lowercase(String name) {
        Assert.notNull(name, "name can not be null");
        return name.toLowerCase(Locale.ROOT);
    }
}
