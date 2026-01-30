package com.eaze.websocket.core.codec;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class OpcodeTest {

    @Test
    public void testFromCode() {
        assertEquals(Opcode.CONTINUATION, Opcode.fromCode(0x0));
        assertEquals(Opcode.TEXT, Opcode.fromCode(0x1));
        assertEquals(Opcode.BINARY, Opcode.fromCode(0x2));
        assertEquals(Opcode.CLOSE, Opcode.fromCode(0x8));
        assertEquals(Opcode.PING, Opcode.fromCode(0x9));
        assertEquals(Opcode.PONG, Opcode.fromCode(0xA));
        assertNull(Opcode.fromCode(0x3)); // Reserved
        assertNull(Opcode.fromCode(0xF)); // Reserved
    }

    @Test
    public void testIsControl() {
        assertFalse(Opcode.CONTINUATION.isControl());
        assertFalse(Opcode.TEXT.isControl());
        assertFalse(Opcode.BINARY.isControl());
        assertTrue(Opcode.CLOSE.isControl());
        assertTrue(Opcode.PING.isControl());
        assertTrue(Opcode.PONG.isControl());
    }
}
