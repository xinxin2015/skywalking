package org.apache.skywalking.apm.agent.core.plugin.interceptor;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;


/**
 * One of the three "Intercept Point".
 * "Intercept Point" is a definition about where and how intercept happens.
 * In this "Intercept Point", the definition targets class's constructors, and the interceptor.
 * <p>
 * ref to two others: {@link StaticMethodsInterceptPoint} and {@link InstanceMethodsInterceptPoint}
 * <p>
 */
public interface ConstructorInterceptPoint {

    ElementMatcher<MethodDescription> getConstructorMatcher();

    /**
     * @return represents a class name, the class instance must be a instance of {@link
     * org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor}
     */
    String getConstructorInterceptor();

}
