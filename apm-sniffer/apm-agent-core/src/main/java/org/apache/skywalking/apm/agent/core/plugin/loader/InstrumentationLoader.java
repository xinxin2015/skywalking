package org.apache.skywalking.apm.agent.core.plugin.loader;

import org.apache.skywalking.apm.agent.core.plugin.AbstractClassEnhancePluginDefine;

import java.util.List;

public interface InstrumentationLoader {

    List<AbstractClassEnhancePluginDefine> load(AgentClassLoader loader);

}
