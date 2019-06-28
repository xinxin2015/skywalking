package org.apache.skywalking.apm.agent.core.context.internal;

public final class RecyclableBuffers {

    private static final ThreadLocal<char[]> ID_BUFFER = new ThreadLocal<>();

    /**
     * Returns a {@link ThreadLocal} reused {@code char[]} for use when decoding bytes into an ID
     * hex string. The buffer should be immediately copied into a {@link String} after decoding
     * within the same method.
     */
    public static char[] idBuffer() {
        char[] idBuffer = ID_BUFFER.get();
        if (idBuffer == null) {
            idBuffer = new char[32];
            ID_BUFFER.set(idBuffer);
        }
        return idBuffer;
    }

    private RecyclableBuffers() {
    }
}
