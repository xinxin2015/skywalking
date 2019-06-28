package org.apache.skywalking.apm.agent.core.plugin.interceptor;

import org.apache.skywalking.apm.agent.core.plugin.PluginException;

public class EnhanceException extends PluginException {

    public EnhanceException(String message) {
        super(message);
    }

    public EnhanceException(String message, Throwable cause) {
        super(message, cause);
    }
}
