package org.apache.skywalking.apm.agent.core.context.internal;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.security.SecureRandom;
import java.util.Enumeration;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import org.apache.skywalking.apm.agent.core.context.Clock;
import org.apache.skywalking.apm.agent.core.context.Tracer;
import org.apache.skywalking.apm.agent.logging.Log;
import org.apache.skywalking.apm.agent.logging.LogFactory;
import org.jvnet.animal_sniffer.IgnoreJRERequirement;

/**
 * Access to platform-specific features.
 *
 * <p>Note: Logging is centralized here to avoid classloader problems.
 *
 * <p>Originally designed by OkHttp team, derived from {@code okhttp3.internal.platform.Platform}
 */
public abstract class Platform {

    private static final Platform PLATFORM = findPlatform();

    private static final Log logger = LogFactory.getLog(Tracer.class);

    private volatile String linkLocalIp;

    /** Guards {@link InetSocketAddress#getHostString()}, as it isn't available until Java 7 */
    @Nullable
    public abstract String getHostString(InetSocketAddress socketAddress);

    @Nullable
    public String linkLocalIp() {
        // uses synchronized variant of double-checked locking as getting the endpoint can be expensive
        if (linkLocalIp != null) {
            return linkLocalIp;
        }
        synchronized (this) {
            if (linkLocalIp == null) {
                linkLocalIp = produceLinkLocalIp();
            }
        }
        return linkLocalIp;
    }

    private String produceLinkLocalIp() {
        try {
            Enumeration<NetworkInterface> nics = NetworkInterface.getNetworkInterfaces();
            if (nics == null) {
                return null;
            }
            while (nics.hasMoreElements()) {
                NetworkInterface nic = nics.nextElement();
                Enumeration<InetAddress> addresses = nic.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (address.isSiteLocalAddress()) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (Exception ex) {
            // don't crash the caller if there was a problem reading nics.

        }
        return null;
    }

    /**
     * This class uses pseudo-random number generators to provision IDs.
     *
     * <p>This optimizes speed over full coverage of 64-bits, which is why it doesn't share a {@link
     * SecureRandom}. It will use {@link java.util.concurrent.ThreadLocalRandom} unless used in JRE 6
     * which doesn't have the class.
     */
    public abstract long randomLong();

    /**
     * Returns the high 8-bytes for
     * {@link org.apache.skywalking.apm.agent.core.context.Tracing.Builder#traceId128Bit
     * 128-bit
     * trace IDs}.
     *
     * <p>The upper 4-bytes are epoch seconds and the lower 4-bytes are random. This makes it
     * convertible to <a href="http://docs.aws.amazon.com/elasticloadbalancing/latest/application/load-balancer-request-tracing.html"></a>Amazon
     * X-Ray trace ID format v1</a>.
     */
    public abstract long nextTraceIdHigh();

    public Clock clock() {
        return new Clock() {
            @Override
            public long currentTimeMicroseconds() {
                return System.currentTimeMillis() * 1000;
            }

            @Override
            public String toString() {
                return "System.currentTimeMillis()";
            }
        };
    }

    public static Platform get() {
        return PLATFORM;
    }

    public void log(String msg,@Nullable Throwable t) {
        if (logger.isDebugEnabled()) {
            logger.debug(msg,t);
        }
    }

    public void log(String msg,Object param,@Nullable Throwable t) {
        if (logger.isDebugEnabled()) {
            LogRecord lr = new LogRecord(Level.FINE,msg);
            Object[] params = new Object[]{param};
            lr.setParameters(params);
            if (t != null) {
                lr.setThrown(t);
            }
            logger.debug(lr);
        }
    }

    /** Attempt to match the host runtime to a capable Platform implementation. */
    private static Platform findPlatform() {
        Platform jre7 = Jre7.buildIfSupport();
        if (jre7 != null) {
            return jre7;
        }
        // compatible with JRE 6
        return new Jre6();
    }

    static class Jre7 extends Platform {

        static Jre7 buildIfSupport() {
            // Find JRE7 new methods.
            try {
                Class.forName("java.util.concurrent.ThreadLocalRandom");
                return new Jre7();
            } catch (ClassNotFoundException ex) {
                // pre JRE7
            }
            return null;
        }

        @Override
        @IgnoreJRERequirement
        public String getHostString(InetSocketAddress socketAddress) {
            return socketAddress.getHostString();
        }

        @Override
        @IgnoreJRERequirement
        public long randomLong() {
            return java.util.concurrent.ThreadLocalRandom.current().nextLong();
        }

        @Override
        @IgnoreJRERequirement
        public long nextTraceIdHigh() {
            return java.util.concurrent.ThreadLocalRandom.current().nextInt();
        }

        @Override
        public String toString() {
            return "Jre7{}";
        }
    }

    static class Jre6 extends Platform {

        final Random prng;

        Jre6() {
            this.prng = new Random(System.nanoTime());
        }

        @Override
        public String getHostString(InetSocketAddress socketAddress) {
            return socketAddress.getAddress().getHostAddress();
        }

        @Override
        public long randomLong() {
            return prng.nextLong();
        }

        @Override
        public long nextTraceIdHigh() {
            return Platform.nextTraceIdHigh(prng.nextInt());
        }

        @Override
        public String toString() {
            return "Jre6";
        }
    }

    private static long nextTraceIdHigh(int random) {
        long epochSeconds = System.currentTimeMillis() / 1000;
        return (epochSeconds & 0xffffffffL) << 32 |
            (random & 0xffffffffL);
    }

}
