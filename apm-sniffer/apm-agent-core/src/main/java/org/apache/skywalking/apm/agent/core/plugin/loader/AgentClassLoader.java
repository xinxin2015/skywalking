package org.apache.skywalking.apm.agent.core.plugin.loader;

import org.apache.skywalking.apm.agent.core.boot.AgentPackageNotFoundException;
import org.apache.skywalking.apm.agent.core.boot.AgentPackagePath;
import org.apache.skywalking.apm.agent.core.plugin.PluginBootstrap;
import org.apache.skywalking.apm.agent.logging.Log;
import org.apache.skywalking.apm.agent.logging.LogFactory;

import java.io.*;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;


/**
 * The <code>AgentClassLoader</code> represents a classloader,
 * which is in charge of finding plugins and interceptors.
 *
 */
public class AgentClassLoader extends ClassLoader {

    private static final Log logger = LogFactory.getLog(AgentClassLoader.class);

    static {
        tryRegisterAsParallelCapable();
    }

    /**
     * The default class loader for the agent.
     */
    private static AgentClassLoader DEFAULT_LOADER;

    private List<File> classPath;

    private List<Jar> allJars;

    private ReentrantLock jarScanLock = new ReentrantLock();

    /**
     * Functional Description: solve the classloader dead lock when jvm start
     * only support JDK7+, since ParallelCapable appears in JDK7+
     */
    private static void tryRegisterAsParallelCapable() {
        Method[] methods = ClassLoader.class.getDeclaredMethods();
        for (Method method : methods) {
            String methodName = method.getName();
            if ("registerAsParallelCapable".equalsIgnoreCase(methodName)) {
                try {
                    method.setAccessible(true);
                    method.invoke(null);
                } catch (Exception ex) {
                    logger.warn("can not invoke ClassLoader.registerAsParallelCapable()", ex);
                }
            }
        }
    }

    public static AgentClassLoader getDefault() {
        return DEFAULT_LOADER;
    }


    /**
     * Init the default
     */
    public static void initDefaultLoader() throws AgentPackageNotFoundException {
        if (DEFAULT_LOADER == null) {
            synchronized (AgentClassLoader.class) {
                DEFAULT_LOADER = new AgentClassLoader(PluginBootstrap.class.getClassLoader());
            }
        }
    }

    public AgentClassLoader(ClassLoader parent) throws AgentPackageNotFoundException {
        super(parent);
        File agentDictionary = AgentPackagePath.getPath();
        classPath = new LinkedList<>();
        classPath.add(new File(agentDictionary, "plugins"));
        classPath.add(new File(agentDictionary, "activations"));
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        List<Jar> allJars = getAllJars();
        String path = name.replace('.','/').concat(".class");
        for (Jar jar : allJars) {
            JarEntry entry = jar.jarFile.getJarEntry(path);
            if (entry != null) {
                try {
                    URL classFileUrl = new URL("jar:file:" + jar.sourceFile.getAbsolutePath() +
                            "!/" + path);
                    byte[] data;
                    BufferedInputStream is = null;
                    ByteArrayOutputStream baos = null;
                    try {
                        is = new BufferedInputStream(classFileUrl.openStream());
                        baos = new ByteArrayOutputStream();
                        int ch = 0;
                        while ((ch = is.read()) != -1) {
                            baos.write(ch);
                        }
                        data = baos.toByteArray();
                    } finally {
                        if (is != null) {
                            try {
                                is.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                        if (baos != null) {
                            try {
                                baos.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                    return defineClass(name,data,0,data.length);
                } catch (IOException ex) {
                    logger.error("find class fail.",ex);
                }
            }
        }
        throw new ClassNotFoundException("Can not find " + name);
    }

    @Override
    protected URL findResource(String name) {
        List<Jar> allJars = getAllJars();
        for (Jar jar : allJars) {
            JarEntry entry = jar.jarFile.getJarEntry(name);
            if (entry != null) {
                try {
                    return new URL("jar:file:" + jar.sourceFile.getAbsolutePath() + "!/" + name);
                } catch (MalformedURLException ignored) {
                }
            }
        }
        return null;
    }

    @Override
    protected Enumeration<URL> findResources(String name) throws IOException {
        List<URL> allResources = new LinkedList<>();
        List<Jar> allJars = getAllJars();
        for (Jar jar : allJars) {
            JarEntry entry = jar.jarFile.getJarEntry(name);
            if (entry != null) {
                allResources.add(new URL("jar:file:" + jar.sourceFile.getAbsolutePath() + "!/" + name));
            }
        }

        final Iterator<URL> iterator = allResources.iterator();
        return new Enumeration<URL>() {
            @Override
            public boolean hasMoreElements() {
                return iterator.hasNext();
            }

            @Override
            public URL nextElement() {
                return iterator.next();
            }
        };
    }

    private List<Jar> getAllJars() {
        if (allJars == null) {
            jarScanLock.lock();
            try {
                if (allJars == null) {
                    allJars = new LinkedList<>();
                    for (File path : classPath) {
                        if (path.exists() && path.isDirectory()) {
                            String[] jarFileNames = path.list(new FilenameFilter() {
                                @Override
                                public boolean accept(File dir, String name) {
                                    return name.endsWith(".jar");
                                }
                            });
                            if (jarFileNames != null) {
                                for (String fileName : jarFileNames) {
                                    try {
                                        File file = new File(path, fileName);
                                        Jar jar = new Jar(new JarFile(file), file);
                                        allJars.add(jar);
                                        logger.info(file.toString() + " loaded");
                                    } catch (IOException ex) {
                                        logger.error(fileName + " jar file cannot be resolved.",
                                                ex);
                                    }
                                }
                            }
                        }
                    }
                }
            } finally {
                jarScanLock.unlock();
            }
        }
        return allJars;
    }

    private class Jar {

        private JarFile jarFile;

        private File sourceFile;

        private Jar(JarFile jarFile, File sourceFile) {
            this.jarFile = jarFile;
            this.sourceFile = sourceFile;
        }
    }
}
