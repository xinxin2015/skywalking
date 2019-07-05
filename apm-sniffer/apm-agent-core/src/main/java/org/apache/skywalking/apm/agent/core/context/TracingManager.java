package org.apache.skywalking.apm.agent.core.context;

import org.apache.skywalking.apm.agent.core.boot.BootService;
import org.apache.skywalking.apm.agent.core.context.propagation.B3Propagation;
import org.apache.skywalking.apm.agent.core.context.propagation.ExtraFieldPropagation;
import org.apache.skywalking.apm.agent.core.context.propagation.ThreadLocalCurrentTraceContext;
import org.apache.skywalking.apm.agent.core.context.propagation.TraceContext.Extractor;
import org.apache.skywalking.apm.agent.core.context.propagation.TraceContext.Injector;
import org.apache.skywalking.apm.agent.logging.Log;
import org.apache.skywalking.apm.agent.logging.LogFactory;

public class TracingManager implements BootService {

    private static final Log logger = LogFactory.getLog(TracingManager.class);

    private static Tracing INSTANCE;

    public static Tracing getInstance() {
        if (INSTANCE == null) {
            synchronized (TracingManager.class) {
                if (INSTANCE == null) {
                    INSTANCE = Tracing.newBuilder().localServiceName("hello-world")
                        .propagationFactory(
                            ExtraFieldPropagation.newFactory(B3Propagation.FACTORY, "user-name"))
                        .currentTraceContext(ThreadLocalCurrentTraceContext.create())
                        .build();
                }
            }
        }
        return INSTANCE;
    }

    public static <C> Span createEntrySpan(Extractor<C> extractor,C carrier) {
        Tracer tracer = getInstance().tracer();
        return tracer.createEntrySpan(extractor,carrier);
    }

    public static Span activeSpan() {
        Tracer tracer = getInstance().tracer();
        return tracer.activeSpan();
    }

    public static void stopSpan() {
        stopSpan(activeSpan());
    }

    public static void stopSpan(Span span) {
        Tracer tracer = getInstance().tracer();
        if (tracer.stopSpan(span)) {
            getInstance().remove();
        }
    }

    public static <C> Span createExitSpan(Injector<C> injector,C carrier) {
        Tracer tracer = getInstance().tracer();
        return tracer.createExitSpan(injector,carrier);
    }

    @Override
    public void prepare() throws Throwable {

    }

    @Override
    public void boot() throws Throwable {

    }

    @Override
    public void onComplete() throws Throwable {

    }

    @Override
    public void shutdown() throws Throwable {

    }
}
