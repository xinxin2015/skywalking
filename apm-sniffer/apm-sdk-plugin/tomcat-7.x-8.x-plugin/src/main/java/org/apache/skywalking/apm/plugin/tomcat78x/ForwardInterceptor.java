package org.apache.skywalking.apm.plugin.tomcat78x;

import java.lang.reflect.Method;
import org.apache.skywalking.apm.agent.core.context.Span;
import org.apache.skywalking.apm.agent.core.context.TracingManager;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.agent.logging.Log;
import org.apache.skywalking.apm.agent.logging.LogFactory;

public class ForwardInterceptor implements InstanceMethodsAroundInterceptor,
    InstanceConstructorInterceptor {

    private static final Log logger = LogFactory.getLog(ForwardInterceptor.class);

    @Override
    public void onConstruct(EnhancedInstance objInst, Object[] allArguments) {
        objInst.setSkyWalkingDynamicField(allArguments[1]);
    }

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, MethodInterceptResult result) throws Throwable {
        Span span = TracingManager.activeSpan();
        span.tag("forward.url",objInst.getSkyWalkingDynamicField() == null ? "" :
            String.valueOf(objInst.getSkyWalkingDynamicField()));
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Object ret) throws Throwable {
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method,
        Object[] allArguments, Class<?>[] argumentsTypes, Throwable t) {

    }
}
