package com.eaze.websocket.core.codec;

import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

public class FrameCodecTest {

    @Test
    public void testEncodeDecodeTextFrame() {
        String message = "Hello, WebSocket!";
        ByteBuffer payload = ByteBuffer.wrap(message.getBytes(StandardCharsets.UTF_8));
        WebSocketFrame frame = new WebSocketFrame(true, Opcode.TEXT, payload);

        ByteBuffer encoded = FrameCodec.encode(frame);
        WebSocketFrame decoded = FrameCodec.decode(encoded);

        assertNotNull(decoded);
        assertTrue(decoded.isFin());
        assertEquals(Opcode.TEXT, decoded.getOpcode());
        assertEquals(message, StandardCharsets.UTF_8.decode(decoded.getPayload()).toString());
    }

    @Test
    public void testEncodeDecodeBinaryFrame() {
        byte[] data = new byte[]{0x01, 0x02, 0x03, 0x04};
        ByteBuffer payload = ByteBuffer.wrap(data);
        WebSocketFrame frame = new WebSocketFrame(true, Opcode.BINARY, payload);

        ByteBuffer encoded = FrameCodec.encode(frame);
        WebSocketFrame decoded = FrameCodec.decode(encoded);

        assertNotNull(decoded);
        assertTrue(decoded.isFin());
        assertEquals(Opcode.BINARY, decoded.getOpcode());
        
        byte[] decodedData = new byte[decoded.getPayload().remaining()];
        decoded.getPayload().get(decodedData);
        assertArrayEquals(data, decodedData);
    }

    @Test
    public void testMasking() {
        String message = "Masked Message";
        byte[] maskingKey = new byte[]{0x11, 0x22, 0x33, 0x44};
        ByteBuffer payload = ByteBuffer.wrap(message.getBytes(StandardCharsets.UTF_8));
        WebSocketFrame frame = new WebSocketFrame(true, Opcode.TEXT, payload, true, maskingKey);

        ByteBuffer encoded = FrameCodec.encode(frame);
        WebSocketFrame decoded = FrameCodec.decode(encoded);

        assertNotNull(decoded);
        assertTrue(decoded.isMasked());
        assertArrayEquals(maskingKey, decoded.getMaskingKey());
        assertEquals(message, StandardCharsets.UTF_8.decode(decoded.getPayload()).toString());
    }

    @Test
    public void testExtendedPayload126() {
        byte[] data = new byte[200]; // > 125
        for (int i = 0; i < data.length; i++) data[i] = (byte) i;
        
        ByteBuffer payload = ByteBuffer.wrap(data);
        WebSocketFrame frame = new WebSocketFrame(true, Opcode.BINARY, payload);

        ByteBuffer encoded = FrameCodec.encode(frame);
        WebSocketFrame decoded = FrameCodec.decode(encoded);

        assertNotNull(decoded);
        assertEquals(200, decoded.getPayload().remaining());
        byte[] decodedData = new byte[decoded.getPayload().remaining()];
        decoded.getPayload().get(decodedData);
        assertArrayEquals(data, decodedData);
    }

    @Test
    public void testExtendedPayload127() {
        byte[] data = new byte[70000]; // > 65535
        for (int i = 0; i < data.length; i++) data[i] = (byte) (i % 256);
        
        ByteBuffer payload = ByteBuffer.wrap(data);
        WebSocketFrame frame = new WebSocketFrame(true, Opcode.BINARY, payload);

        ByteBuffer encoded = FrameCodec.encode(frame);
        WebSocketFrame decoded = FrameCodec.decode(encoded);

        assertNotNull(decoded);
        assertEquals(70000, decoded.getPayload().remaining());
        byte[] decodedData = new byte[decoded.getPayload().remaining()];
        decoded.getPayload().get(decodedData);
        assertArrayEquals(data, decodedData);
    }

    @Test
    public void testControlFrames() {
        // Ping
        WebSocketFrame ping = new WebSocketFrame(true, Opcode.PING, ByteBuffer.allocate(0));
        ByteBuffer encodedPing = FrameCodec.encode(ping);
        WebSocketFrame decodedPing = FrameCodec.decode(encodedPing);
        assertEquals(Opcode.PING, decodedPing.getOpcode());

        // Pong
        WebSocketFrame pong = new WebSocketFrame(true, Opcode.PONG, ByteBuffer.allocate(0));
        ByteBuffer encodedPong = FrameCodec.encode(pong);
        WebSocketFrame decodedPong = FrameCodec.decode(encodedPong);
        assertEquals(Opcode.PONG, decodedPong.getOpcode());

        // Close
        WebSocketFrame close = new WebSocketFrame(true, Opcode.CLOSE, ByteBuffer.allocate(0));
        ByteBuffer encodedClose = FrameCodec.encode(close);
        WebSocketFrame decodedClose = FrameCodec.decode(encodedClose);
        assertEquals(Opcode.CLOSE, decodedClose.getOpcode());
    }

    @Test
    public void testPartialFrameDecoding() {
        ByteBuffer buffer = ByteBuffer.allocate(10);
        buffer.put((byte) 0x81); // Text frame, FIN
        buffer.put((byte) 0x05); // Length 5, no mask
        buffer.put("Hel".getBytes(StandardCharsets.UTF_8)); // Only 3 bytes of "Hello"
        buffer.flip();

        WebSocketFrame decoded = FrameCodec.decode(buffer);
        assertNull(decoded, "Should return null for incomplete payload");
        assertEquals(0, buffer.position(), "Buffer position should be reset");
    }

    @Test
    public void testPartialHeaderDecoding() {
        // Only 1 byte available
        ByteBuffer buffer = ByteBuffer.allocate(1);
        buffer.put((byte) 0x81);
        buffer.flip();
        assertNull(FrameCodec.decode(buffer));
        assertEquals(0, buffer.position());

        // 126 length but only 1 byte of length available
        buffer = ByteBuffer.allocate(3);
        buffer.put((byte) 0x81);
        buffer.put((byte) 126);
        buffer.put((byte) 0x00); // Only 1 byte of the 2-byte length
        buffer.flip();
        assertNull(FrameCodec.decode(buffer));
        assertEquals(0, buffer.position());

        // 127 length but only 7 bytes of length available
        buffer = ByteBuffer.allocate(9);
        buffer.put((byte) 0x81);
        buffer.put((byte) 127);
        for(int i=0; i<7; i++) buffer.put((byte) 0);
        buffer.flip();
        assertNull(FrameCodec.decode(buffer));
        assertEquals(0, buffer.position());

        // Mask bit set but no masking key
        buffer = ByteBuffer.allocate(2);
        buffer.put((byte) 0x81);
        buffer.put((byte) 0x85); // Masked, length 5
        buffer.flip();
        assertNull(FrameCodec.decode(buffer));
        assertEquals(0, buffer.position());
    }

    @Test
    public void testFinBit() {
        WebSocketFrame frame = new WebSocketFrame(false, Opcode.TEXT, ByteBuffer.wrap("Part 1".getBytes()));
        ByteBuffer encoded = FrameCodec.encode(frame);
        WebSocketFrame decoded = FrameCodec.decode(encoded);
        assertFalse(decoded.isFin());
    }
}
