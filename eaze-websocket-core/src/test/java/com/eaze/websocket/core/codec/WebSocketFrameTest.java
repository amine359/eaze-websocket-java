package com.eaze.websocket.core.codec;

import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import static org.junit.jupiter.api.Assertions.*;

public class WebSocketFrameTest {

    @Test
    public void testConstructorAndGetters() {
        ByteBuffer payload = ByteBuffer.wrap(new byte[]{1, 2, 3});
        WebSocketFrame frame = new WebSocketFrame(true, Opcode.BINARY, payload);
        
        assertTrue(frame.isFin());
        assertEquals(Opcode.BINARY, frame.getOpcode());
        assertEquals(payload, frame.getPayload());
        assertFalse(frame.isMasked());
        assertNull(frame.getMaskingKey());
    }

    @Test
    public void testMaskedConstructor() {
        ByteBuffer payload = ByteBuffer.wrap(new byte[]{1, 2, 3});
        byte[] mask = new byte[]{0, 1, 2, 3};
        WebSocketFrame frame = new WebSocketFrame(false, Opcode.TEXT, payload, true, mask);
        
        assertFalse(frame.isFin());
        assertEquals(Opcode.TEXT, frame.getOpcode());
        assertEquals(payload, frame.getPayload());
        assertTrue(frame.isMasked());
        assertArrayEquals(mask, frame.getMaskingKey());
    }
}
