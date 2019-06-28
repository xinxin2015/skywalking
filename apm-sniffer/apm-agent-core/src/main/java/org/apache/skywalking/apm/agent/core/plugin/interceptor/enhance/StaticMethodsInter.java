package org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance;

import java.lang.reflect.Method;
import java.util.concurrent.Callable;
import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.SuperCall;
import org.apache.skywalking.apm.agent.core.plugin.loader.InterceptorInstanceLoader;
import org.apache.skywalking.apm.agent.logging.Log;
import org.apache.skywalking.apm.agent.logging.LogFactory;

public class StaticMethodsInter {

    private static final Log logger = LogFactory.getLog(StaticMethodsInter.class);

    private String staticMethodsAroundInterceptorClassName;

    public StaticMethodsInter(String staticMethodsAroundInterceptorClassName) {
        this.staticMethodsAroundInterceptorClassName = staticMethodsAroundInterceptorClassName;
    }

    @RuntimeType
    public Object intercept(@Origin Class<?> clazz,@AllArguments Object[] allAruments,
        @Origin Method method,@SuperCall Callable<?> zuper) throws Throwable {
        StaticMethodsAroundInterceptor interceptor =
            InterceptorInstanceLoader.load(staticMethodsAroundInterceptorClassName,
                clazz.getClassLoader());

        MethodInterceptResult result = new MethodInterceptResult();
        try {
            interceptor.beforeMethod(clazz,method,allAruments,method.getParameterTypes(),result);
        } catch (Throwable ex) {
            logger.error("class [" + clazz + "] before static method [" + method.getName() + "] " +
                "intercept failure.",ex);
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
                interceptor.handleMethodException(clazz,method,allAruments,
                    method.getParameterTypes(),ex);
            } catch (Throwable t) {
                logger.error("class [" + clazz + "] handle static method [" + method.getName() +
                    "] exception failure.",t);
            }
            throw ex;
        } finally {
            try {
                ret = interceptor.afterMethod(clazz,method,allAruments,method.getParameterTypes()
                    ,ret);
            } catch (Throwable ex) {
                logger.error("class [" + clazz + "] after static method [" + method.getName() +
                    "] intercept failure.",ex);
            }
        }
        return ret;
    }

}
