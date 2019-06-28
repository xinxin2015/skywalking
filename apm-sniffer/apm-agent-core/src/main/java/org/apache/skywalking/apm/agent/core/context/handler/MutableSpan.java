package org.apache.skywalking.apm.agent.core.context.handler;

import java.util.ArrayList;
import java.util.Locale;
import org.apache.skywalking.apm.agent.core.context.Span.Kind;
import org.apache.skywalking.apm.agent.core.context.internal.IpLiteral;
import org.apache.skywalking.apm.agent.core.context.internal.Nullable;
import org.apache.skywalking.apm.agent.core.util.Assert;

/**
 * This represents a span except for its
 * {@link org.apache.skywalking.apm.agent.core.context.propagation.TraceContext}.
 * It is
 * mutable, for late
 * adjustments.
 *
 * <p>While in-flight, the data is synchronized where necessary. When exposed to users, it can be
 * mutated without synchronization.
 */
public final class MutableSpan implements Cloneable {

    public interface TagConsumer<T> {

        /** @see org.apache.skywalking.apm.agent.core.context.Span#tag(String, String) */
        void accept(T target,String key,String value);

    }

    public interface AnnotationConsumer<T> {

        /** @see org.apache.skywalking.apm.agent.core.context.Span#annotate(long, String) */
        void accept(T target,long timestamp,String value);

    }

    public interface TagUpdater {

        /**
         * Returns the same value, an updated one, or null to drop the tag.
         *
         * @see org.apache.skywalking.apm.agent.core.context.Span#tag(String, String)
         */
        @Nullable
        String update(String key,String value);

    }

    public interface AnnotationUpdater {

        /**
         * Returns the same value, an updated one, or null to drop the annotation.
         *
         * @see org.apache.skywalking.apm.agent.core.context.Span#annotate(long, String)
         */
        @Nullable
        String update(long timestamp,String value);

    }

    /*
     * One of these objects is allocated for each in-flight span, so we try to be parsimonious on things
     * like array allocation and object reference size.
     */
    private Kind kind;

    private boolean shared;

    private long startTimestamp,finishTimestamp;

    private String name,localServiceName,localIp,remoteServiceName,remoteIp;

    private int localPort,remotePort;

    /** To reduce the amount of allocation use a pair-indexed list for tag (key, value). */
    private ArrayList<String> tags;

    /** Also use pair indexing for annotations, but type object to store (startTimestamp, value). */
    private ArrayList<Object> annotations;

    private Throwable error;

    public MutableSpan() {
        // this cheats because it will not need to grow unless there are more than 5 tags
        tags = new ArrayList<>();
        // lazy initialize annotations
    }

    /** Returns the {@link org.apache.skywalking.apm.agent.core.context.Span#name(String) span name} or null */
    @Nullable
    public String name() {
        return name;
    }

    /** @see org.apache.skywalking.apm.agent.core.context.Span#name(String) */
    public void name(String name) {
        Assert.notNull(name,"name can not be null");
        this.name = name;
    }

    /** Returns the {@link org.apache.skywalking.apm.agent.core.context.Span#start(long) span start timestamp} or zero */
    public long startTimestamp() {
        return startTimestamp;
    }

    /** @see org.apache.skywalking.apm.agent.core.context.Span#start(long) */
    public void startTimestamp(long startTimestamp) {
        this.startTimestamp = startTimestamp;
    }

    /** Returns the {@link org.apache.skywalking.apm.agent.core.context.Span#finish(long) span finish timestamp} or zero */
    public long finishTimestamp() {
        return finishTimestamp;
    }

    /** @see org.apache.skywalking.apm.agent.core.context.Span#finish(long) */
    public void finishTimestamp(long finishTimestamp) {
        this.finishTimestamp = finishTimestamp;
    }

    /** Returns the {@link org.apache.skywalking.apm.agent.core.context.Span#kind(org.apache.skywalking.apm.agent.core.context.Span.Kind) span kind} or null */
    public Kind kind() {
        return kind;
    }

    /** @see org.apache.skywalking.apm.agent.core.context.Span#kind(org.apache.skywalking.apm.agent.core.context.Span.Kind) */
    public void kind(Kind kind) {
        Assert.notNull(kind,"kind can not be null");
        this.kind = kind;
    }

    /**
     * When null {@link org.apache.skywalking.apm.agent.core.context.Tracing.Builder#localServiceName(String) default} will be used for
     * zipkin.
     */
    @Nullable
    public String localServiceName() {
        return localServiceName;
    }

    /** @see org.apache.skywalking.apm.agent.core.context.Tracing.Builder#localServiceName(String) */
    public void localServiceName(String localServiceName) {
        Assert.hasLength(localServiceName,"localServiceName can not be null");
        this.localServiceName = localServiceName;
    }

    /** When null {@link org.apache.skywalking.apm.agent.core.context.Tracing.Builder#localIp(String) default} will be used for zipkin. */
    @Nullable
    public String localIp() {
        return localIp;
    }

    /** @see #localIp() */
    public boolean localIp(@Nullable String localIp) {
        this.localIp = localIp;
        return true;
    }

    /** When zero {@link org.apache.skywalking.apm.agent.core.context.Tracing.Builder#localIp(String) default} will be used for zipkin. */
    public int localPort() {
        return localPort;
    }

    /** @see #localPort() */
    public void localPort(int localPort) {
        if (localPort > 0xffff) {
            throw new IllegalArgumentException("invalid port " + localPort);
        }
        if (localPort < 0) {
            localPort = 0;
        }
        this.localPort = localPort;
    }

    /** @see org.apache.skywalking.apm.agent.core.context.Span#remoteServiceName(String) */
    @Nullable
    public String remoteServiceName() {
        return remoteServiceName;
    }

    /** @see org.apache.skywalking.apm.agent.core.context.Span#remoteServiceName(String) */
    public void remoteServiceName(String remoteServiceName) {
        Assert.hasLength(remoteServiceName,"remoteServiceName can not be empty");
        this.remoteServiceName = remoteServiceName.toLowerCase(Locale.ROOT);
    }

    /**
     * The text representation of the primary IPv4 or IPv6 address associated with the remote side of
     * this connection. Ex. 192.168.99.100 null if unknown.
     *
     * @see org.apache.skywalking.apm.agent.core.context.Span#remoteIpAndPort(String, int)
     */
    @Nullable
    public String remoteIp() {
        return remoteIp;
    }

    /**
     * Port of the remote IP's socket or 0, if not known.
     *
     * @see java.net.InetSocketAddress#getPort()
     * @see org.apache.skywalking.apm.agent.core.context.Span#remoteIpAndPort(String, int)
     */
    public int remotePort() {
        return remotePort;
    }

    /** @see org.apache.skywalking.apm.agent.core.context.Span#remoteIpAndPort(String, int) */
    public boolean remoteIpAndPort(@Nullable String remoteIp,int remotePort) {
        if (remoteIp == null) {
            return false;
        }
        this.remoteIp = IpLiteral.ipOrNull(remoteIp);
        if (this.remoteIp == null) {
            return false;
        }
        if (remotePort > 0xffff) {
            throw new IllegalArgumentException("invalid port " + remotePort);
        }
        if (remotePort < 0) {
            remotePort = 0;
        }
        this.remotePort = remotePort;
        return true;
    }

    /** @see org.apache.skywalking.apm.agent.core.context.Span#annotate(String) */
    public void annotate(long timestamp,String value) {
        Assert.notNull(value,"value can not be null");
        if (timestamp == 0L) {
            return;
        }
        if (annotations == null) {
            annotations = new ArrayList<>();
        }
        annotations.add(timestamp);
        annotations.add(value);
    }

    /** @see org.apache.skywalking.apm.agent.core.context.Span#error(Throwable) */
    public Throwable error() {
        return error;
    }

    /** @see org.apache.skywalking.apm.agent.core.context.Span#error(Throwable) */
    public void error(Throwable error) {
        this.error = error;
    }

    /** Returns the last value associated with the key or null */
    @Nullable
    public String tag(String key) {
        Assert.hasLength(key,"key can not be empty");
        String result = null;
        for (int i = 0;i < tags.size();i += 2) {
            if (key.equals(tags.get(i))) {
                result = tags.get(i + 1);
            }
        }
        return result;
    }

    /** @see org.apache.skywalking.apm.agent.core.context.Span#tag(String, String) */
    public void tag(String key,String value) {
        Assert.hasLength(key,"key can not be empty");
        Assert.notNull(value,"value can not be null");
        for (int i = 0;i < tags.size();i ++) {
            if (key.equals(tags.get(i))) {
                tags.set(i + 1,value);
                return;
            }
        }
        tags.add(key);
        tags.add(value);
    }

    
    public <T> void forEachTag(TagConsumer<T> tagConsumer,T target) {
        for (int i = 0;i < tags.size();i += 2) {
            tagConsumer.accept(target,tags.get(i),tags.get(i + 1));
        }
    }

    /** Allows you to update values for redaction purposes */
    public void forEachTag(TagUpdater tagUpdater) {
        for (int i = 0;i < tags.size();i += 2) {
            String value = tags.get(i + 1);
            String newValue = tagUpdater.update(tags.get(i),value);
            if (updateOrRemove(tags,i,value,newValue)) {
                i -= 2;
            }
        }
    }

    /**
     * Allows you to copy all data into a different target, such as a different span model or logs.
     */
    public <T> void forEachAnnotation(AnnotationConsumer<T> annotationConsumer,T target) {
        if (annotations == null) {
            return;
        }
        for (int i = 0;i < annotations.size();i ++) {
            long timestamp = (long) annotations.get(i);
            annotationConsumer.accept(target,timestamp,annotations.get(i + 1).toString());
        }
    }

    /** Allows you to update values for redaction purposes */
    public void forEachAnnotation(AnnotationUpdater annotationUpdater) {
        if (annotations == null) {
            return;
        }
        for (int i = 0;i < annotations.size();i ++) {
            String value = annotations.get(i + 1).toString();
            String newValue = annotationUpdater.update((Long) annotations.get(i),value);
            if (updateOrRemove(annotations,i,value,newValue)) {
                i -= 2;
            }
        }
    }

    /** Returns true if the key/value was removed from the pair-indexed list at index {@code i} */
    @SuppressWarnings("unchecked")
    private static boolean updateOrRemove(ArrayList list,int i,Object value,
        @Nullable Object newValue) {
        if (newValue == null) {
            list.remove(i);
            list.remove(i);
            return true;
        } else if (!value.equals(newValue)) {
            list.set(i,newValue);
        }
        return false;
    }

    /** Returns true if the span ID is {@link #setShared() shared} with a remote client. */
    public boolean shared() {
        return shared;
    }

    /**
     * Indicates we are contributing to a span started by another tracer (ex on a different host).
     * Defaults to false.
     *
     *@see org.apache.skywalking.apm.agent.core.context.Tracer#joinSpan(
     * org.apache.skywalking.apm.agent.core.context.propagation.TraceContext)
     * @see zipkin2.Span#shared()
     */
    public void setShared() {
        shared = true;
    }

}
