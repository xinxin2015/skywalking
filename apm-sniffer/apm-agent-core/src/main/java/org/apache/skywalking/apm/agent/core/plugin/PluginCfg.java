package org.apache.skywalking.apm.agent.core.plugin;

import org.apache.skywalking.apm.agent.core.plugin.exception.IllegalPluginDefineException;
import org.apache.skywalking.apm.agent.logging.Log;
import org.apache.skywalking.apm.agent.logging.LogFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public enum PluginCfg {

    INSTANCE;

    private static final Log logger = LogFactory.getLog(PluginCfg.class);

    private List<PluginDefine> pluginClassList = new ArrayList<>();

    void load(InputStream input) throws IOException {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(input));
            String pluginDefine;
            while ((pluginDefine = reader.readLine()) != null) {
                try {
                    if (pluginDefine.trim().length() == 0 || pluginDefine.startsWith("#")) {
                        continue;
                    }
                    PluginDefine define = PluginDefine.build(pluginDefine);
                    pluginClassList.add(define);
                } catch (IllegalPluginDefineException ex) {
                    logger.error("Failed to format plugin(" + pluginDefine + ") define.");
                }
            }
        } finally {
            input.close();
        }
    }

    public List<PluginDefine> getPluginClassList() {
        return pluginClassList;
    }
}
