package org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance;

import java.lang.reflect.Method;
import java.util.concurrent.Callable;
import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.SuperCall;
import net.bytebuddy.implementation.bind.annotation.This;
import org.apache.skywalking.apm.agent.core.plugin.PluginException;
import org.apache.skywalking.apm.agent.core.plugin.loader.InterceptorInstanceLoader;
import org.apache.skywalking.apm.agent.logging.Log;
import org.apache.skywalking.apm.agent.logging.LogFactory;

public class InstMethodsInter {

    private static final Log logger = LogFactory.getLog(InstMethodsInter.class);

    private InstanceMethodsAroundInterceptor interceptor;

    public InstMethodsInter(String instanceMethodsAroundInterceptorClassName,
        ClassLoader classLoader) {
        try {
            interceptor =
                InterceptorInstanceLoader.load(instanceMethodsAroundInterceptorClassName,
                    classLoader);
        } catch (Throwable ex) {
            throw new PluginException("Can't create InstanceMethodsAroundInterceptor.", ex);
        }
    }

    @RuntimeType
    public Object interceptor(@This Object obj,@AllArguments Object[] allArguments,@SuperCall
        Callable<?> zuper,@Origin Method method) throws Throwable {
        EnhancedInstance targetObject = (EnhancedInstance)obj;

        MethodInterceptResult result = new MethodInterceptResult();

        try {
            interceptor.beforeMethod(targetObject,method,allArguments,method.getParameterTypes(),
                result);
        } catch (Throwable ex) {
            logger.error("class [" + obj.getClass() + "] before method [" + method.getName() + "]" +
                " interceptor failure.");
        }

        Object ret = null;
        try {
            if (!result.isContinue()) {
                ret = result._ret();
            } else {
                ret = zuper.call();
            }
        } catch (Throwable ex) {
            try {
                interceptor.handleMethodException(targetObject,method,allArguments,
                    method.getParameterTypes(),ex);
            } catch (Throwable t) {
                logger.error("class [" + obj.getClass() + "] handle method [" + method.getName() + "] exception failure.",t);
            }
            throw ex;
        } finally {
            try {
                ret = interceptor.afterMethod(targetObject,method,allArguments,
                    method.getParameterTypes(),ret);
            } catch (Throwable ex) {
                logger.error("class [" + obj.getClass() + "] after method [" + method.getName() + "] interceptor failure.",ex);
            }
        }
        return ret;
    }
}
