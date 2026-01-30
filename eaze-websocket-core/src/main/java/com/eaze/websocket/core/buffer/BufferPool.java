package com.eaze.websocket.core.buffer;

import java.nio.ByteBuffer;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class BufferPool {
    // Since we now use lazy allocation (only for active connections),
    // we can afford larger buffers to avoid frequent allocateDirect calls.
    private static final int BUFFER_SIZE = 8192; 
    private static final Queue<ByteBuffer> pool = new ConcurrentLinkedQueue<>();

    public static ByteBuffer acquire() {
        ByteBuffer buffer = pool.poll();
        if (buffer == null) {
            return ByteBuffer.allocateDirect(BUFFER_SIZE);
        }
        buffer.clear();
        return buffer;
    }

    public static void release(ByteBuffer buffer) {
        if (buffer != null && buffer.isDirect() && buffer.capacity() == BUFFER_SIZE) {
            pool.offer(buffer);
        }
    }
    
    public static ByteBuffer allocate(int size) {
        if (size <= BUFFER_SIZE) {
            return acquire();
        }
        // For larger messages, we still use allocateDirect, 
        // but we could implement a multi-tiered pool if needed.
        return ByteBuffer.allocateDirect(size);
    }
}
