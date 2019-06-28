package org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance;

public interface OverrideCallable {

    Object call(Object[] args);

}
