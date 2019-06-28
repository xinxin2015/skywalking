package org.apache.skywalking.apm.agent;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.utility.JavaModule;
import org.apache.skywalking.apm.agent.core.boot.AgentPackageNotFoundException;
import org.apache.skywalking.apm.agent.core.boot.ServiceManager;
import org.apache.skywalking.apm.agent.core.plugin.AbstractClassEnhancePluginDefine;
import org.apache.skywalking.apm.agent.core.plugin.EnhanceContext;
import org.apache.skywalking.apm.agent.core.plugin.PluginBootstrap;
import org.apache.skywalking.apm.agent.core.plugin.PluginFinder;
import org.apache.skywalking.apm.agent.logging.Log;
import org.apache.skywalking.apm.agent.logging.LogFactory;

import java.lang.instrument.Instrumentation;
import java.util.List;

import static net.bytebuddy.matcher.ElementMatchers.*;

public class SkyWalkingAgent {

    private static final Log logger = LogFactory.getLog(SkyWalkingAgent.class);

    public static void premain(String agentArgs, Instrumentation instrumentation) {
        final PluginFinder pluginFinder;
        try {
            pluginFinder = new PluginFinder(new PluginBootstrap().loadPlugins());
        } catch (AgentPackageNotFoundException ex) {
            logger.error("Locate agent.jar failure. Shutting down.", ex);
            return;
        } catch (Exception ex) {
            logger.error("Skywalking agent initialized failure. Shutting down.", ex);
            return;
        }

        final ByteBuddy byteBuddy = new ByteBuddy();

        new AgentBuilder.Default(byteBuddy)
            .ignore(nameStartsWith("net.bytebuddy.")
                .or(nameStartsWith("org.slf4j."))
                .or(nameStartsWith("org.apache.logging."))
                .or(nameStartsWith("org.groovy."))
                .or(nameContains("javassist"))
                .or(nameContains(".asm."))
                .or(nameStartsWith("sun.reflect"))
                .or(allSkyWalkingAgentExcludeToolkit())
                .or(ElementMatchers.<TypeDescription>isSynthetic()))
            .type(pluginFinder.buildMatch())
            .transform(new Transformer(pluginFinder))
            .with(new Listener())
            .installOn(instrumentation);

        try {
            ServiceManager.INSTANCE.boot();
        } catch (Throwable ex) {
            logger.error("Skywalking agent boot failure.",ex);
        }

        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override public void run() {
                ServiceManager.INSTANCE.shutdown();
            }
        },"skywalking service shutdown thread"));
    }

    private static class Transformer implements AgentBuilder.Transformer {

        private final PluginFinder pluginFinder;

        private Transformer(PluginFinder pluginFinder) {
            this.pluginFinder = pluginFinder;
        }

        @Override
        public DynamicType.Builder<?> transform(DynamicType.Builder<?> builder,
            TypeDescription typeDescription,
            ClassLoader classLoader, JavaModule module) {
            List<AbstractClassEnhancePluginDefine> pluginDefines =
                pluginFinder.find(typeDescription);
            if (pluginDefines.size() > 0) {
                DynamicType.Builder<?> newBuilder = builder;
                EnhanceContext context = new EnhanceContext();
                for (AbstractClassEnhancePluginDefine define : pluginDefines) {
                    DynamicType.Builder<?> possibleNewBuilder = define.define(typeDescription,
                        newBuilder, classLoader, context);
                    if (possibleNewBuilder != null) {
                        newBuilder = possibleNewBuilder;
                    }
                }
                if (context.isEnhanced()) {
                    logger.debug("Finish the prepare stage for " + typeDescription.getName() + ".");
                }
                return newBuilder;
            }
            return null;
        }
    }

    private static ElementMatcher.Junction<NamedElement> allSkyWalkingAgentExcludeToolkit() {
        return nameStartsWith("org.apache.skywalking.").and(not(nameStartsWith("org.apache" +
            ".skywalking.apm.toolkit.")));
    }

    private static class Listener implements AgentBuilder.Listener {

        @Override
        public void onDiscovery(String typeName, ClassLoader classLoader, JavaModule module,
            boolean loaded) {

        }

        @Override
        public void onTransformation(TypeDescription typeDescription, ClassLoader classLoader,
            JavaModule module, boolean loaded, DynamicType dynamicType) {
            if (logger.isDebugEnabled()) {
                logger.debug("On transformation class " + typeDescription.getName() + ".");
            }
        }

        @Override
        public void onIgnored(TypeDescription typeDescription, ClassLoader classLoader,
            JavaModule module, boolean loaded) {

        }

        @Override
        public void onError(String typeName, ClassLoader classLoader, JavaModule module,
            boolean loaded, Throwable throwable) {
            logger.error("Enhance class " + typeName + " error.", throwable);
        }

        @Override
        public void onComplete(String typeName, ClassLoader classLoader, JavaModule module,
            boolean loaded) {

        }
    }

}
