package org.apache.skywalking.apm.agent.core.plugin;


import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import org.apache.skywalking.apm.agent.core.plugin.match.ClassMatch;
import org.apache.skywalking.apm.agent.core.util.StringUtils;
import org.apache.skywalking.apm.agent.logging.Log;
import org.apache.skywalking.apm.agent.logging.LogFactory;


/**
 * Basic abstract class of all sky-walking auto-instrumentation plugins.
 * <p>
 * It provides the outline of enhancing the target class.
 * If you want to know more about enhancing, you should go to see {@link ClassEnhancePluginDefine}
 */
public abstract class AbstractClassEnhancePluginDefine {

    protected static final Log logger = LogFactory.getLog(AbstractClassEnhancePluginDefine.class);


    /**
     * Main entrance of enhancing the class.
     *
     * @param typeDescription target class description.
     * @param builder byte-buddy's builder to manipulate target class's bytecode.
     * @param classLoader load the given transformClass
     * @return the new builder, or <code>null</code> if not be enhanced.
     * @throws PluginException when set builder failure.
     */
    public DynamicType.Builder<?> define(TypeDescription typeDescription,
                                         DynamicType.Builder<?> builder, ClassLoader classLoader,
                                         EnhanceContext context) {
        String interceptorDefineClassName = this.getClass().getName();
        String transformClassName = typeDescription.getTypeName();
        if (StringUtils.isEmpty(transformClassName)) {
            logger.warn("classname of being intercepted is not defined by " + interceptorDefineClassName + ".");
            return null;
        }

        logger.debug("prepare to enhance class " + transformClassName + " by " + interceptorDefineClassName + ".");

        String[] witnessClasses = witnessClasses();
        if (witnessClasses != null) {
            for (String witnessClass : witnessClasses) {
                if (!WitnessClassFinder.INSTANCE.exist(witnessClass, classLoader)) {
                    logger.warn("enhance class " + transformClassName + " by plugin " + interceptorDefineClassName + " is " + "not " + "working. Because " + "witness class " + witnessClass + " is not existed.");
                    return null;
                }
            }
        }

        // find origin class source code for interceptor
        DynamicType.Builder<?> newClassBuilder = this.enhance(typeDescription,builder,classLoader
                ,context);

        context.initializationStageCompleted();
        logger.debug("enhance class " + transformClassName + " by " + interceptorDefineClassName + " completely.");
        return newClassBuilder;
    }

    protected abstract DynamicType.Builder<?> enhance(TypeDescription typeDefinition,
                                                      DynamicType.Builder<?> newClassBuilder,
                                                      ClassLoader classLoader,
                                                      EnhanceContext context) throws PluginException;

    protected abstract ClassMatch enhanceClass();

    protected String[] witnessClasses() {
        return new String[]{};
    }

}
