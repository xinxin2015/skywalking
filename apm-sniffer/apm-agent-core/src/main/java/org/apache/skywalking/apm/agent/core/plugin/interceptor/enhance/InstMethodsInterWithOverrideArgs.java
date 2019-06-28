package org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance;

import java.lang.reflect.Method;
import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.Morph;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.This;
import org.apache.skywalking.apm.agent.core.plugin.PluginException;
import org.apache.skywalking.apm.agent.core.plugin.loader.InterceptorInstanceLoader;
import org.apache.skywalking.apm.agent.logging.Log;
import org.apache.skywalking.apm.agent.logging.LogFactory;

/**
 * The actual byte-buddy's interceptor to intercept class instance methods.
 * In this class, it provide a bridge between byte-buddy and sky-walking plugin.
 *
 */
public class InstMethodsInterWithOverrideArgs {

    private static final Log logger = LogFactory.getLog(InstMethodsInterWithOverrideArgs.class);

    /**
     * An {@link InstanceMethodsAroundInterceptor}
     * This name should only stay in {@link String}, the real {@link Class} type will trigger classloader failure.
     * If you want to know more, please check on books about Classloader or Classloader appointment mechanism.
     */
    private InstanceMethodsAroundInterceptor interceptor;

    /**
     * @param instanceMethodsAroundInterceptorClassName class full name.
     */
    public InstMethodsInterWithOverrideArgs(String instanceMethodsAroundInterceptorClassName,
        ClassLoader classLoader) {
        try {
            interceptor =
                InterceptorInstanceLoader.load(instanceMethodsAroundInterceptorClassName,
                    classLoader);
        } catch (Throwable t) {
            throw new PluginException("Can't create InstanceMethodsAroundInterceptor.", t);
        }
    }

    /**
     * Intercept the target instance method.
     *
     * @param obj target class instance.
     * @param allArguments all method arguments
     * @param method method description.
     * @param zuper the origin call ref.
     * @return the return value of target instance method.
     * @throws Exception only throw exception because of zuper.call() or unexpected exception in sky-walking ( This is a
     * bug, if anything triggers this condition ).
     */
    @RuntimeType
    public Object interceptor(@This Object obj,@AllArguments Object[] allArguments,
        @Origin Method method,@Morph OverrideCallable zuper) throws Throwable {
        EnhancedInstance targetObject = (EnhancedInstance)obj;

        MethodInterceptResult result = new MethodInterceptResult();

        try {
            interceptor.beforeMethod(targetObject,method,allArguments,method.getParameterTypes(),
                result);
        } catch (Throwable ex) {
            logger.error("class [" + obj.getClass() + "] before method [" + method.getName() + "]" +
                " interceptor failure.",ex);
        }

        Object ret = null;

        try {
            if (!result.isContinue()) {
                ret = result._ret();
            } else {
                ret = zuper.call(allArguments);
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
                ret = interceptor.afterMethod(targetObject,method,allArguments,method.getParameterTypes(),ret);
            } catch (Throwable ex) {
                logger.error("class [" + obj.getClass() + "] after method [" + method.getName() + "] interceptor failure.",ex);
            }
        }
        return ret;
    }

}
