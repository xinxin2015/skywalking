package org.apache.skywalking.apm.plugin.tomcat78x.define;

import static org.apache.skywalking.apm.agent.core.plugin.match.NameMatch.byName;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.ConstructorInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import org.apache.skywalking.apm.agent.core.plugin.match.ClassMatch;

public class TomcatInstrumentation extends ClassInstanceMethodsEnhancePluginDefine {

    private static final String ENHANCE_CLASS = "org.apache.catalina.core.StandardHostValve";

    private static final String INVOKE_INTERCEPT_CLASS = "org.apache.skywalking.apm.plugin" +
        ".tomcat78x.TomcatInvokeInterceptor";

    private static final String EXCEPTION_INTERCEPT_CLASS = "org.apache.skywalking.apm.plugin" +
        ".tomcat78x.TomcatExceptionInterceptor";

    @Override
    protected ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        return null;
    }

    @Override
    protected InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
        return new InstanceMethodsInterceptPoint[]{
            new InstanceMethodsInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodMatcher() {
                    return ElementMatchers.named("invoke");
                }

                @Override
                public String getMethodsInterceptor() {
                    return INVOKE_INTERCEPT_CLASS;
                }

                @Override
                public boolean isOverrideArgs() {
                    return false;
                }
            }, new InstanceMethodsInterceptPoint() {
            @Override
            public ElementMatcher<MethodDescription> getMethodMatcher() {
                return ElementMatchers.named("throwable");
            }

            @Override
            public String getMethodsInterceptor() {
                return EXCEPTION_INTERCEPT_CLASS;
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
        return byName(ENHANCE_CLASS);
    }
}
