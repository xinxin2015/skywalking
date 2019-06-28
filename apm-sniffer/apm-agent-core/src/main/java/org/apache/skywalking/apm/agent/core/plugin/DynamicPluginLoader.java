package org.apache.skywalking.apm.agent.core.plugin;

import org.apache.skywalking.apm.agent.core.plugin.loader.AgentClassLoader;
import org.apache.skywalking.apm.agent.core.plugin.loader.InstrumentationLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;


/**
 * The plugin can be inserted into the kernel by implementing this spi return PluginDefine list.
 *
 */
public enum DynamicPluginLoader {

    INSTANCE;

    public List<AbstractClassEnhancePluginDefine> load(AgentClassLoader classLoader) {
        List<AbstractClassEnhancePluginDefine> all = new ArrayList<>();
        for (InstrumentationLoader instrumentationLoader :
                ServiceLoader.load(InstrumentationLoader.class,classLoader)) {
            List<AbstractClassEnhancePluginDefine> plugins =
                    instrumentationLoader.load(classLoader);
            if (plugins != null && !plugins.isEmpty()) {
                all.addAll(plugins);
            }
        }
        return all;
    }

}
