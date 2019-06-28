package org.apache.skywalking.apm.agent.core.plugin.loader;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;
import org.apache.skywalking.apm.agent.core.boot.AgentPackageNotFoundException;

public class InterceptorInstanceLoader {

    private static ConcurrentMap<String, Object> INSTANCE_CACHE = new ConcurrentHashMap<>();

    private static ReentrantLock INSTANCE_LOAD_LOCK = new ReentrantLock();

    private static Map<ClassLoader, ClassLoader> EXTEND_PLUGIN_CLASSLOADERS = new HashMap<>();

    @SuppressWarnings("unchecked")
    public static <T> T load(String className,
        ClassLoader targetClassLoader) throws AgentPackageNotFoundException,
        ClassNotFoundException, IllegalAccessException, InstantiationException {
        if (targetClassLoader == null) {
            targetClassLoader = InterceptorInstanceLoader.class.getClassLoader();
        }
        String instanceKey =
            className + "_OF_" + targetClassLoader.getClass().getName() + "@" +
                Integer.toHexString(targetClassLoader.hashCode());
        Object inst = INSTANCE_CACHE.get(instanceKey);
        if (inst == null) {
            INSTANCE_LOAD_LOCK.lock();
            try {
                ClassLoader pluginLoader = EXTEND_PLUGIN_CLASSLOADERS.get(targetClassLoader);
                if (pluginLoader == null) {
                    pluginLoader = new AgentClassLoader(targetClassLoader);
                    EXTEND_PLUGIN_CLASSLOADERS.put(targetClassLoader, pluginLoader);
                }
                inst = Class.forName(className, true, pluginLoader).newInstance();
            } finally {
                INSTANCE_LOAD_LOCK.unlock();
            }

            if (inst != null) {
                INSTANCE_CACHE.put(instanceKey, inst);
            }
        }
        return (T)inst;
    }
}
