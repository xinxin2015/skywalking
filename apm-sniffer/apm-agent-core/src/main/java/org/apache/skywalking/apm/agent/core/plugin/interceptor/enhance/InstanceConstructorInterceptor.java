package org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance;

public interface InstanceConstructorInterceptor {

    void onConstruct(EnhancedInstance objInst,Object[] allArguments);

}
