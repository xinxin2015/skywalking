package org.apache.skywalking.apm.agent.core.context.internal.handler;

import java.util.Objects;
import org.apache.skywalking.apm.agent.core.context.ErrorParser;
import org.apache.skywalking.apm.agent.core.context.handler.MutableSpan;
import org.apache.skywalking.apm.agent.core.context.handler.MutableSpan.AnnotationConsumer;
import org.apache.skywalking.apm.agent.core.context.handler.MutableSpan.TagConsumer;
import org.apache.skywalking.apm.agent.core.context.internal.Nullable;
import org.apache.skywalking.apm.agent.core.util.Assert;
import zipkin2.Endpoint;
import zipkin2.Span;
import zipkin2.Span.Builder;

// internal until we figure out how the api should sit.
public final class MutableSpanConverter {

    private final ErrorParser errorParser;
    private final String localServiceName;
    @Nullable
    private final String localIp;
    private final int localPort;
    private final Endpoint localEndpoint;

    public MutableSpanConverter(ErrorParser errorParser,String localServiceName,String localIp,
        int localPort) {
        Assert.notNull(errorParser,"errorParser can not be null");
        Assert.notNull(localServiceName,"localServiceName can not be null");
        this.errorParser = errorParser;
        this.localServiceName = localServiceName;
        this.localIp = localIp;
        this.localPort = localPort;
        this.localEndpoint =
            Endpoint.newBuilder().serviceName(localServiceName).ip(localIp).port(localPort).build();
    }

    void convert(MutableSpan span,Span.Builder result) {
        result.name(span.name());

        long start = span.startTimestamp(),finish = span.finishTimestamp();
        result.timestamp(start);

        if (start != 0 && finish != 0) {
            result.duration(Math.max(finish - start,1));
        }

        // use ordinal comparison to defend against version skew
        org.apache.skywalking.apm.agent.core.context.Span.Kind kind = span.kind();
        if (kind != null && kind.ordinal() < Span.Kind.values().length) {
            result.kind(Span.Kind.values()[kind.ordinal()]);
        }

        addLocalEndpoint(span.localServiceName(), span.localIp(), span.localPort(), result);
        String remoteServiceName = span.remoteServiceName(), remoteIp = span.remoteIp();
        if (remoteServiceName != null || remoteIp != null) {
            result.remoteEndpoint(zipkin2.Endpoint.newBuilder()
                .serviceName(remoteServiceName)
                .ip(remoteIp)
                .port(span.remotePort())
                .build());
        }

        String errorTag = span.tag("error");
        if (errorTag == null && span.error() != null) {
            errorParser.error(span.error(), span);
        }

        span.forEachTag(Consumer.INSTANCE,result);
        span.forEachAnnotation(Consumer.INSTANCE,result);
        if (span.shared()) {
            result.shared(true);
        }
    }

    // avoid re-allocating an endpoint when we have the same data
    private void addLocalEndpoint(String serviceName,@Nullable String ip,int port,Span.Builder span) {
        if (serviceName == null) {
            serviceName = localServiceName;
        }
        if (ip == null) {
            ip = localIp;
        }
        if (port <= 0) {
            port = 0;
        }
        if (localServiceName.equals(serviceName)
            && (Objects.equals(localIp, ip))
            && localPort == port) {
            span.localEndpoint(localEndpoint);
        } else {
            span.localEndpoint(Endpoint.newBuilder().serviceName(serviceName).ip(ip).port(port).build());
        }
    }

    enum Consumer implements TagConsumer<Span.Builder>, AnnotationConsumer<Span.Builder> {
        INSTANCE;

        @Override
        public void accept(Builder target, String key, String value) {
            target.putTag(key,value);
        }

        @Override
        public void accept(Builder target, long timestamp, String value) {
            target.addAnnotation(timestamp,value);
        }
    }

}
