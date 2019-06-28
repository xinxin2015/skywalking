package org.apache.skywalking.apm.agent.core.boot;


/**
 * The <code>BootService</code> is an interface to all remote, which need to boot when plugin mechanism begins to
 * work.
 * {@link #boot()} will be called when <code>BootService</code> start up.
 *
 */
public interface BootService {

    void prepare() throws Throwable;

    void boot() throws Throwable;

    void onComplete() throws Throwable;

    void shutdown() throws Throwable;

}
