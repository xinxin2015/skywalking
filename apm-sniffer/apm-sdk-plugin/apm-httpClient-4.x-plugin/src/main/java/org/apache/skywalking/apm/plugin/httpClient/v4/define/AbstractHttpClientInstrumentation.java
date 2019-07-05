package org.apache.skywalking.apm.plugin.httpClient.v4.define;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.match.ClassMatch;
import org.apache.skywalking.apm.agent.core.plugin.match.NameMatch;

public class AbstractHttpClientInstrumentation extends HttpClientInstrumentation {

    private static final String ENHANCE_CLASS = "org.apache.http.impl.client.AbstractHttpClient";

    /**
     * version 4.2, intercept method: execute, intercept
     * public final HttpResponse execute(HttpHost target, HttpRequest request, HttpContext context)
     */
    @Override
    protected InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
        return new InstanceMethodsInterceptPoint[] {
            new InstanceMethodsInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodMatcher() {
                    return ElementMatchers.named("doExecute");
                }

                @Override
                public String getMethodsInterceptor() {
                    return getInstanceMethodsInterceptor();
                }

                @Override
                public boolean isOverrideArgs() {
                    return false;
                }
            }
        };
    }

    @Override
    protected ClassMatch enhanceClass() {
        return NameMatch.byName(ENHANCE_CLASS);
    }
}
