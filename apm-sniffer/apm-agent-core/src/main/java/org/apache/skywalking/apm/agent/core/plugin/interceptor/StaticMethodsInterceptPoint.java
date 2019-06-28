package org.apache.skywalking.apm.agent.core.plugin.interceptor;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * One of the three "Intercept Point".
 * "Intercept Point" is a definition about where and how intercept happens.
 * In this "Intercept Point", the definition targets class's static methods, and the interceptor.
 * <p>
 * ref to two others: {@link ConstructorInterceptPoint} and {@link InstanceMethodsInterceptPoint}
 * <p>
 */
public interface StaticMethodsInterceptPoint {

    ElementMatcher<MethodDescription> getMethodsMatcher();

    String getMethodsInterceptor();

    boolean isOverrideArgs();

}
