package org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance;

import java.lang.reflect.Method;
import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.Morph;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import org.apache.skywalking.apm.agent.core.plugin.loader.InterceptorInstanceLoader;
import org.apache.skywalking.apm.agent.logging.Log;
import org.apache.skywalking.apm.agent.logging.LogFactory;


/**
 * The actual byte-buddy's interceptor to intercept class instance methods.
 * In this class, it provide a bridge between byte-buddy and sky-walking plugin.
 *
 */
public class StaticMethodsInterWithOverrideArgs {

    private static final Log logger = LogFactory.getLog(StaticMethodsInterWithOverrideArgs.class);

    /**
     * A class full name, and instanceof {@link StaticMethodsAroundInterceptor}
     * This name should only stay in {@link String}, the real {@link Class} type will trigger classloader failure.
     * If you want to know more, please check on books about Classloader or Classloader appointment mechanism.
     */
    private String staticMethodsAroundInterceptorClassName;

    /**
     * Set the name of {@link StaticMethodsInterWithOverrideArgs#staticMethodsAroundInterceptorClassName}
     *
     * @param staticMethodsAroundInterceptorClassName class full name.
     */
    public StaticMethodsInterWithOverrideArgs(String staticMethodsAroundInterceptorClassName) {
        this.staticMethodsAroundInterceptorClassName = staticMethodsAroundInterceptorClassName;
    }

    /**
     * Intercept the target static method.
     *
     * @param clazz target class
     * @param allArguments all method arguments
     * @param method method description.
     * @param zuper the origin call ref.
     * @return the return value of target static method.
     * @throws Exception only throw exception because of zuper.call() or unexpected exception in sky-walking ( This is a
     * bug, if anything triggers this condition ).
     */
    @RuntimeType
    public Object intercept(@Origin Class<?> clazz, @AllArguments Object[] allArguments,
        @Origin Method method,
        @Morph OverrideCallable zuper) throws Throwable {
        StaticMethodsAroundInterceptor interceptor =
            InterceptorInstanceLoader.load(staticMethodsAroundInterceptorClassName,
                clazz.getClassLoader());

        MethodInterceptResult result = new MethodInterceptResult();

        try {
            interceptor.beforeMethod(clazz, method, allArguments, method.getParameterTypes(),
                result);
        } catch (Throwable ex) {
            logger.error("class [" + clazz + "] before static method + [" + method.getName() + "]" +
                " intercept failure.", ex);
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
                interceptor.handleMethodException(clazz, method, allArguments,
                    method.getParameterTypes(), ex);
            } catch (Throwable t) {
                logger.error("class [" + clazz + "] handle static method [" + method.getName() +
                    "] exception failure.", t);
            }
            throw ex;
        } finally {
            try {
                ret = interceptor.afterMethod(clazz, method, allArguments,
                    method.getParameterTypes(), ret);
            } catch (Throwable ex) {
                logger.error("class [" + clazz + "] after static method [" + method.getName() +
                    "] intercept failure.", ex);
            }
        }
        return ret;
    }

}
