package org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance;

public interface EnhancedInstance {

    Object getSkyWalkingDynamicField();

    void setSkyWalkingDynamicField(Object value);

}
