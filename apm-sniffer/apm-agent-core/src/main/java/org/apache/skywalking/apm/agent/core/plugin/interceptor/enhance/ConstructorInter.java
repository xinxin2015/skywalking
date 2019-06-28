package org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance;

import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.This;
import org.apache.skywalking.apm.agent.core.plugin.PluginException;
import org.apache.skywalking.apm.agent.core.plugin.loader.InterceptorInstanceLoader;
import org.apache.skywalking.apm.agent.logging.Log;
import org.apache.skywalking.apm.agent.logging.LogFactory;

/**
 * The actual byte-buddy's interceptor to intercept constructor methods.
 * In this class, it provide a bridge between byte-buddy and sky-walking plugin.
 *
 */
public class ConstructorInter {

    private static final Log logger = LogFactory.getLog(ConstructorInter.class);

    /**
     * An {@link InstanceConstructorInterceptor}
     * This name should only stay in {@link String}, the real {@link Class} type will trigger classloader failure.
     * If you want to know more, please check on books about Classloader or Classloader appointment mechanism.
     */
    private InstanceConstructorInterceptor interceptor;

    /**
     * @param constructorInterceptorClassName class full name.
     */
    public ConstructorInter(String constructorInterceptorClassName,ClassLoader classLoader) throws PluginException {
        try {
            interceptor = InterceptorInstanceLoader.load(constructorInterceptorClassName,
                classLoader);
        } catch (Throwable ex) {
            throw new PluginException("Can't create InstanceConstructorInterceptor.",ex);
        }
    }

    /**
     * Intercept the target constructor.
     *
     * @param obj target class instance.
     * @param allArguments all constructor arguments
     */
    @RuntimeType
    public void interceptor(@This Object obj,@AllArguments Object[] allArguments) {
        try {
            EnhancedInstance targetObject = (EnhancedInstance)obj;

            interceptor.onConstruct(targetObject,allArguments);
        } catch (Throwable ex) {
            logger.error("ConstructorInter failure.",ex);
        }
    }

}
