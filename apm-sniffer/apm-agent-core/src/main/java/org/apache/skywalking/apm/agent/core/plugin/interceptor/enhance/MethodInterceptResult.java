package org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance;

import java.lang.reflect.Method;

/**
 * This is a method return value manipulator. When a interceptor's method, such as
 * {@link InstanceMethodsAroundInterceptor#beforeMethod(EnhancedInstance, Method, Object[], Class[], MethodInterceptResult)} (org.apache.skywalking.apm.agent.core.plugin.interceptor.EnhancedClassInstanceContext,
 * has this as a method argument, the interceptor can manipulate
 * the method's return value. <p> The new value set to this object, by {@link MethodInterceptResult#defineReturnValue(Object)},
 * will override the origin return value.
 *
 */
public class MethodInterceptResult {

    private boolean isContinue = true;

    private Object ret = null;

    public void defineReturnValue(Object ret) {
        this.isContinue = false;
        this.ret = ret;
    }

    public boolean isContinue() {
        return isContinue;
    }

    Object _ret() {
        return ret;
    }
}
