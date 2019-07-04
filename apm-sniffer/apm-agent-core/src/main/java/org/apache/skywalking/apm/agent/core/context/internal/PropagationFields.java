package org.apache.skywalking.apm.agent.core.context.internal;

import java.util.Map;
import org.apache.skywalking.apm.agent.core.context.propagation.TraceContext;
import org.apache.skywalking.apm.agent.core.util.Assert;

public abstract class PropagationFields {

    private long traceId,spanId;

    public abstract String get(String name);

    public abstract void put(String name,String value);

    abstract void putAllIfAbsent(PropagationFields parent);

    public abstract Map<String,String> toMap();

    final boolean tryToClaim(long traceId,long spanId) {
        synchronized (this) {
            if (this.traceId == 0L) {
                this.traceId = traceId;
                this.spanId = spanId;
                return true;
            }
            return this.traceId == traceId && this.spanId == spanId;
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + toMap();
    }

    public static String get(TraceContext context,String name,
        Class<? extends PropagationFields> type) {
        Assert.notNull(context,"context can not be null");
        Assert.notNull(name,"name can not be null");
        PropagationFields fields = context.findExtra(type);
        return fields != null ? fields.get(name) : null;
    }

    public static void put(TraceContext context,String name,String value,Class<?
        extends PropagationFields> type) {
        Assert.notNull(context,"context can not be null");
        Assert.notNull(name,"name can not be null");
        Assert.notNull(value,"value can not be null");
        PropagationFields fields = context.findExtra(type);
        if (fields == null) {
            return;
        }
        fields.put(name,value);
    }
}
