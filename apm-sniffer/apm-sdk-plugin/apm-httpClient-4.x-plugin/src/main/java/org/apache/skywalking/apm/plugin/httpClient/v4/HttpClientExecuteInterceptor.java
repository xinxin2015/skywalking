package org.apache.skywalking.apm.plugin.httpClient.v4;

import java.lang.reflect.Method;
import java.net.InetAddress;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.skywalking.apm.agent.core.context.Span;
import org.apache.skywalking.apm.agent.core.context.Span.Kind;
import org.apache.skywalking.apm.agent.core.context.TracingManager;
import org.apache.skywalking.apm.agent.core.context.propagation.Propagation.Setter;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.agent.logging.Log;
import org.apache.skywalking.apm.agent.logging.LogFactory;

public class HttpClientExecuteInterceptor implements InstanceMethodsAroundInterceptor {

    private static final Log logger = LogFactory.getLog(HttpClientExecuteInterceptor.class);

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, MethodInterceptResult result) throws Throwable {

        if (allArguments[0] == null || allArguments[1] == null) {
            // illegal args, can't trace. ignore.
            return;
        }

        final HttpHost httpHost = (HttpHost) allArguments[0];
        final HttpRequest httpRequest = (HttpRequest) allArguments[1];

        Span span =
            TracingManager.createExitSpan(TracingManager.getInstance().propagation().injector(
                new Setter<HttpRequest, String>() {
                    @Override
                    public void put(HttpRequest carrier, String key, String value) {
                        carrier.setHeader(key, value);
                    }
                }), httpRequest);

        request(httpRequest,httpHost,span);
    }

    private void request(HttpRequest request, HttpHost httpHost, Span span) {
        if (span.isNoop()) {
            return;
        }
        parseTargetAddress(httpHost,span);
        span.kind(Kind.CLIENT);
        span.customizer().name(request.getRequestLine().getMethod());
        span.customizer().tag("http.method", request.getRequestLine().getMethod());
        span.customizer().tag("http.path", request.getRequestLine().getUri());
        span.customizer().tag("http.client","httpClient");
    }

    private void parseTargetAddress(HttpHost host, Span span) {
        InetAddress address = host.getAddress();
        if (address != null) {
            if (span.remoteIpAndPort(address.getHostAddress(), host.getPort())) {
                return;
            }
        }
        span.remoteIpAndPort(host.getHostName(), host.getPort());
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Object ret) throws Throwable {
        if (allArguments[0] == null || allArguments[1] == null) {
            return ret;
        }

        if (ret != null) {
            HttpResponse response = (HttpResponse) ret;
            StatusLine statusLine = response.getStatusLine();
            if (statusLine != null) {
                int statusCode = statusLine.getStatusCode();
                Span span = TracingManager.activeSpan();
                if (statusCode >= 400) {
                    span.tag("http.status_code",String.valueOf(statusCode));
                }
            }
        }

        TracingManager.stopSpan();
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method,
        Object[] allArguments, Class<?>[] argumentsTypes, Throwable t) {
        Span span = TracingManager.activeSpan();
        span.error(t);
    }
}
