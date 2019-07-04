package org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance;

import org.apache.skywalking.apm.agent.core.plugin.interceptor.StaticMethodsInterceptPoint;

/**
 * Plugins, which only need enhance class static methods. Actually, inherit from {@link
 * ClassInstanceMethodsEnhancePluginDefine} has no differences with inherit from {@link ClassEnhancePluginDefine}. Just
 * override {@link ClassEnhancePluginDefine#getStaticMethodsInterceptPoints}, and return NULL, which means
 * nothing to enhance.
 *
 * @author wusheng
 */
public abstract class ClassInstanceMethodsEnhancePluginDefine extends ClassEnhancePluginDefine {

    /**
     * @return null, means enhance no static methods.
     */
    @Override
    protected StaticMethodsInterceptPoint[] getStaticMethodsInterceptPoints() {
        return null;
    }
}
