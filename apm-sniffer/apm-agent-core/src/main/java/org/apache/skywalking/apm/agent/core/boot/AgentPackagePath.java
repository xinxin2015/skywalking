package org.apache.skywalking.apm.agent.core.boot;

import org.apache.skywalking.apm.agent.logging.Log;
import org.apache.skywalking.apm.agent.logging.LogFactory;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;

public class AgentPackagePath {

    private static final Log logger = LogFactory.getLog(AgentPackagePath.class);

    private static File AGENT_PACKAGE_PATH;

    public static File getPath() throws AgentPackageNotFoundException {
        if (AGENT_PACKAGE_PATH == null) {
            AGENT_PACKAGE_PATH = findPath();
        }
        return AGENT_PACKAGE_PATH;
    }

    private static File findPath() throws AgentPackageNotFoundException {
        String classResourcePath = AgentPackagePath.class.getName().replaceAll("\\.","/") +
                ".class";

        URL resource = ClassLoader.getSystemClassLoader().getResource(classResourcePath);
        if (resource != null) {
            String urlString = resource.toString();

            logger.debug("The beacon class location is " + urlString + ".");

            int insidePathIndex = urlString.indexOf("!");
            boolean isInJar = insidePathIndex > -1;
            if (isInJar) {
                urlString = urlString.substring(urlString.indexOf("file:"),insidePathIndex);
                File agentJarFile = null;
                try {
                    agentJarFile = new File(new URL(urlString).toURI());
                } catch (MalformedURLException | URISyntaxException ex) {
                    logger.error("Can not locate agent jar file by url:" + urlString,ex);
                }
                if (agentJarFile != null && agentJarFile.exists()) {
                    return agentJarFile.getParentFile();
                }
            } else {
                String classLocation = urlString.substring(urlString.indexOf("file:"),
                        urlString.length() - classResourcePath.length());
                return new File(classLocation);
            }
        }

        logger.error("Can not locate agent jar file.");
        throw new AgentPackageNotFoundException("Can not locate agent jar file.");
    }

}
