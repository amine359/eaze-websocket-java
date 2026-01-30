package com.eaze.websocket.core.buffer;

import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import static org.junit.jupiter.api.Assertions.*;

public class BufferPoolTest {

    @Test
    public void testAcquireAndRelease() {
        ByteBuffer buffer1 = BufferPool.acquire();
        assertNotNull(buffer1);
        assertTrue(buffer1.isDirect());
        assertEquals(8192, buffer1.capacity());

        buffer1.put((byte) 1);
        BufferPool.release(buffer1);

        ByteBuffer buffer2 = BufferPool.acquire();
        assertSame(buffer1, buffer2, "Should reuse the released buffer");
        assertEquals(0, buffer2.position(), "Reused buffer should be cleared");
    }

    @Test
    public void testAllocateLargeBuffer() {
        int largeSize = 10000;
        ByteBuffer buffer = BufferPool.allocate(largeSize);
        assertNotNull(buffer);
        assertTrue(buffer.capacity() >= largeSize);
        assertTrue(buffer.isDirect());
    }

    @Test
    public void testReleaseInvalidBuffer() {
        // Should not throw exception
        BufferPool.release(null);
        
        ByteBuffer heapBuffer = ByteBuffer.allocate(8192);
        BufferPool.release(heapBuffer); // Should not be added to pool because it's not direct
        
        ByteBuffer buffer1 = BufferPool.acquire();
        assertNotSame(heapBuffer, buffer1);
    }
}
