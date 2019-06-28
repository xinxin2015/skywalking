package org.apache.skywalking.apm.agent.core.plugin;

import org.apache.skywalking.apm.agent.core.plugin.loader.AgentClassLoader;
import org.apache.skywalking.apm.agent.logging.Log;
import org.apache.skywalking.apm.agent.logging.LogFactory;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

public class PluginResourcesResolver {

    private static final Log logger = LogFactory.getLog(PluginResourcesResolver.class);

    public List<URL> getResource() {
        List<URL> cfgUrlPaths = new ArrayList<>();
        Enumeration<URL> urls;

        try {
            urls = AgentClassLoader.getDefault().getResources("skywalking-plugin.def");

            while (urls.hasMoreElements()) {
                URL pluginUrl = urls.nextElement();
                cfgUrlPaths.add(pluginUrl);
                logger.info("find skywalking plugin define in " + pluginUrl);
            }
            return cfgUrlPaths;
        } catch (IOException ex) {
            logger.error("read resource failure.",ex);
        }
        return null;
    }
}
