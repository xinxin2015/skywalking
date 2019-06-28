package org.apache.skywalking.apm.agent.core.boot;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import org.apache.skywalking.apm.agent.core.plugin.loader.AgentClassLoader;
import org.apache.skywalking.apm.agent.logging.Log;
import org.apache.skywalking.apm.agent.logging.LogFactory;


/**
 * The <code>ServiceManager</code> bases on {@link ServiceLoader},
 * load all {@link BootService} implementations.
 *
 */
public enum ServiceManager {

    INSTANCE;

    private static final Log logger = LogFactory.getLog(ServiceManager.class);

    private Map<Class,BootService> bootedServices = Collections.emptyMap();

    public void boot() {
        bootedServices = loadAllServices();

        prepare();
        startup();
        onComplete();
    }

    public void shutdown() {
        for (BootService bootService : bootedServices.values()) {
            try {
                bootService.shutdown();
            } catch (Throwable ex) {
                logger.error("ServiceManager try to pre-start [" + bootService.getClass().getName() + "] fail");
            }
        }
    }

    private Map<Class,BootService> loadAllServices() {
        Map<Class,BootService> bootedServices = new LinkedHashMap<>();
        List<BootService> allServices = new LinkedList<>();
        load(allServices);
        for (BootService bootService : allServices) {
            Class<? extends BootService> bootServiceClass = bootService.getClass();
            boolean isDefaultImplementor =
                bootServiceClass.isAnnotationPresent(DefaultImplementor.class);
            if (isDefaultImplementor) {
                if (!bootedServices.containsKey(bootServiceClass)) {
                    bootedServices.put(bootServiceClass,bootService);
                }
            } else {
                OverrideImplementor overrideImplementor =
                    bootServiceClass.getAnnotation(OverrideImplementor.class);
                if (overrideImplementor == null) {
                    if (!bootedServices.containsKey(bootServiceClass)) {
                        bootedServices.put(bootServiceClass,bootService);
                    } else {
                        throw new ServiceConflictException("Duplicate service define for :" + bootServiceClass);
                    }
                } else {
                    Class<? extends BootService> targetService = overrideImplementor.value();
                    if (bootedServices.containsKey(targetService)) {
                        boolean presentDefault =
                            bootedServices.get(targetService).getClass().isAnnotationPresent(DefaultImplementor.class);
                        if (presentDefault) {
                            bootedServices.put(targetService,bootService);
                        } else {
                            throw new ServiceConflictException("Service " + bootServiceClass + " overrides conflict, " +
                                "exist more than one service want to override :" + targetService);
                        }
                    } else {
                        bootedServices.put(targetService,bootService);
                    }
                }
            }
        }
        return bootedServices;
    }

    private void prepare() {
        for (BootService bootService : bootedServices.values()) {
            try {
                bootService.prepare();
            } catch (Throwable ex) {
                logger.error("ServiceManager try to pre-start [" + bootService.getClass().getName() + "] fail.");
            }
        }
    }

    private void startup() {
        for (BootService bootService : bootedServices.values()) {
            try {
                bootService.boot();
            } catch (Throwable ex) {
                logger.error("ServiceManager try to pre-start [" + bootService.getClass().getName() + "] fail.");
            }
        }
    }

    private void onComplete() {
        for (BootService bootService : bootedServices.values()) {
            try {
                bootService.onComplete();
            } catch (Throwable ex) {
                logger.error("ServiceManager try to pre-start [" + bootService.getClass().getName() + "] fail.");
            }
        }
    }

    /**
     * Find a {@link BootService} implementation, which is already started.
     *
     * @param serviceClass class name.
     * @param <T> {@link BootService} implementation class.
     * @return {@link BootService} instance
     */
    @SuppressWarnings("unchecked")
    public <T extends BootService> T findService(Class<T> serviceClass) {
        return (T)bootedServices.get(serviceClass);
    }

    void load(List<BootService> allServices) {
        for (BootService bootService : ServiceLoader.load(BootService.class,
            AgentClassLoader.getDefault())) {
            allServices.add(bootService);
        }
    }

}
