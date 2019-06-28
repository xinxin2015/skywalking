package org.apache.skywalking.apm.agent.core.plugin.interceptor;


/**
 * this interface for those who only want to enhance declared method in case of some unexpected issue,
 * such as spring controller
 *
 */
public interface DeclaredInstanceMethodsInterceptPoint extends InstanceMethodsInterceptPoint {
}
