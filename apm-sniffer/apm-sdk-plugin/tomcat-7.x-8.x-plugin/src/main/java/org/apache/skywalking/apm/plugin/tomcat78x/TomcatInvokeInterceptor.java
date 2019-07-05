package org.apache.skywalking.apm.plugin.tomcat78x;

import java.lang.reflect.Method;
import java.net.URI;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.skywalking.apm.agent.core.context.Span;
import org.apache.skywalking.apm.agent.core.context.Span.Kind;
import org.apache.skywalking.apm.agent.core.context.SpanCustomizer;
import org.apache.skywalking.apm.agent.core.context.Tracer;
import org.apache.skywalking.apm.agent.core.context.TracingManager;
import org.apache.skywalking.apm.agent.core.context.propagation.CurrentTraceContext;
import org.apache.skywalking.apm.agent.core.context.propagation.CurrentTraceContext.Scope;
import org.apache.skywalking.apm.agent.core.context.propagation.Propagation.Getter;
import org.apache.skywalking.apm.agent.core.context.propagation.TraceContext.Extractor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.agent.logging.Log;
import org.apache.skywalking.apm.agent.logging.LogFactory;

public class TomcatInvokeInterceptor implements InstanceMethodsAroundInterceptor {

    private static final Log logger = LogFactory.getLog(TomcatInvokeInterceptor.class);

    private final Extractor<HttpServletRequest> extractor;

    public TomcatInvokeInterceptor() {
        extractor = TracingManager.getInstance().propagation().extractor(
            new Getter<HttpServletRequest, String>() {
                @Override
                public String get(HttpServletRequest carrier, String key) {
                    return carrier.getHeader(key);
                }
            });
    }

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, MethodInterceptResult result) throws Throwable {

        HttpServletRequest request = (HttpServletRequest) allArguments[0];

        handleReceived(extractor, request);
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Object ret) throws Throwable {
        HttpServletResponse response = (HttpServletResponse) allArguments[1];
        handleFinish(response,TracingManager.activeSpan());
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method,
        Object[] allArguments, Class<?>[] argumentsTypes, Throwable t) {
        handleException(t);
    }

    private void handleException(Throwable t) {
        Span span = TracingManager.activeSpan();
        span.error(t);
    }

    private void handleFinish(HttpServletResponse response, Span span) {
        if (span.isNoop()) {
            return;
        }
        span.customizer().tag("http.status_code", String.valueOf(response.getStatus()));
        TracingManager.stopSpan();
    }

    private void handleReceived(Extractor<HttpServletRequest> extractor,
        HttpServletRequest request) {
        Span nextSpan = TracingManager.createEntrySpan(extractor, request);
        if (nextSpan.isNoop()) {
            return;
        }
        nextSpan.kind(Kind.SERVER);
        parseClientIpAndPort(request, nextSpan);
        request(request, nextSpan.customizer());
    }

    private boolean parseClientIpAndPort(HttpServletRequest request, Span span) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor == null) {
            return false;
        }
        int indexOfComma = forwardedFor.indexOf(',');
        if (indexOfComma != -1) {
            forwardedFor = forwardedFor.substring(0, indexOfComma);
        }
        return span.remoteIpAndPort(forwardedFor, 0);
    }

    private void request(HttpServletRequest request, SpanCustomizer spanCustomizer) {
        spanCustomizer.name(request.getMethod());
        String methodName = request.getMethod();
        if (methodName != null) {
            spanCustomizer.tag("http.method", methodName);
        }
        String path = path(request);
        if (path != null) {
            spanCustomizer.tag("http.path", path);
        }
    }

    private String url(HttpServletRequest request) {
        StringBuffer url = request.getRequestURL();
        if (request.getQueryString() != null && !request.getQueryString().isEmpty()) {
            url.append('?').append(request.getQueryString());
        }
        return url.toString();
    }

    private String path(HttpServletRequest request) {
        String url = url(request);
        return URI.create(url).getPath();
    }
}
