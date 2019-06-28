package org.apache.skywalking.apm.agent.core.boot;

public class ServiceConflictException extends RuntimeException {

    public ServiceConflictException(String message) {
        super(message);
    }
}
