package org.apache.skywalking.apm.agent.core.plugin;

import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.ClassEnhancePluginDefine;

/**
 * The <code>EnhanceContext</code> represents the context or status for processing a class.
 *
 * Based on this context, the plugin core {@link ClassEnhancePluginDefine}
 * knows how to process the specific steps for every particular plugin.
 *
 */
public class EnhanceContext {

    private boolean isEnhanced = false;

    /**
     * The object has already been enhanced or extended.
     * e.g. added the new field, or implemented the new interface
     */
    private boolean objectExtended = false;

    public boolean isEnhanced() {
        return isEnhanced;
    }

    public void initializationStageCompleted() {
        isEnhanced = true;
    }

    public boolean isObjectExtended() {
        return objectExtended;
    }

    public void extendObjectCompleted() {
        objectExtended = true;
    }
}
