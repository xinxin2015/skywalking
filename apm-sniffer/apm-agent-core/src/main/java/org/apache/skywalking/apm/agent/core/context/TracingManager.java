package org.apache.skywalking.apm.agent.core.context;

import org.apache.skywalking.apm.agent.core.boot.BootService;
import org.apache.skywalking.apm.agent.core.context.propagation.B3Propagation;
import org.apache.skywalking.apm.agent.core.context.propagation.ExtraFieldPropagation;
import org.apache.skywalking.apm.agent.core.context.propagation.ThreadLocalCurrentTraceContext;
import org.apache.skywalking.apm.agent.core.context.propagation.TraceContext.Extractor;

public class TracingManager implements BootService {

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
        return getInstance().tracer().createSpan(extractor,carrier);
    }

    public static Span activeSpan() {
        return getInstance().tracer().activeSpan();
    }

    public static void stopSpan() {
        stopSpan(activeSpan());
    }

    public static void stopSpan(Span span) {
        if (getInstance().tracer().stopSpan(span)) {
            getInstance().remove();
        }
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
