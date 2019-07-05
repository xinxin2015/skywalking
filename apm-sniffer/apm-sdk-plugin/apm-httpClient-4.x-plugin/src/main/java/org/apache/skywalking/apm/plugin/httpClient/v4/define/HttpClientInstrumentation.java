package org.apache.skywalking.apm.plugin.httpClient.v4.define;

import org.apache.skywalking.apm.agent.core.plugin.interceptor.ConstructorInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;

public abstract class HttpClientInstrumentation extends ClassInstanceMethodsEnhancePluginDefine {

    private static final String INTERCEPT_CLASS = "org.apache.skywalking.apm.plugin.httpClient.v4"
        + ".HttpClientExecuteInterceptor";

    @Override
    protected ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        return null;
    }

    protected String getInstanceMethodsInterceptor() {
        return INTERCEPT_CLASS;
    }
}
