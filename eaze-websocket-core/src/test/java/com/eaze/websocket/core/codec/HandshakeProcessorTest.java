package com.eaze.websocket.core.codec;

import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

public class HandshakeProcessorTest {

    @Test
    public void testCreateAcceptKey() {
        String clientKey = "dGhlIHNhbXBsZSBub25jZQ==";
        String expectedAcceptKey = "s3pPLMBiTxaQ9kYGzzhZRbK+xOo=";
        assertEquals(expectedAcceptKey, HandshakeProcessor.createAcceptKey(clientKey));
    }

    @Test
    public void testParseHeadersString() {
        String request = "GET /chat HTTP/1.1\r\n" +
                "Host: server.example.com\r\n" +
                "Upgrade: websocket\r\n" +
                "Connection: Upgrade\r\n" +
                "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n" +
                "Origin: http://example.com\r\n" +
                "Sec-WebSocket-Protocol: chat, superchat\r\n" +
                "Sec-WebSocket-Version: 13\r\n\r\n";

        Map<String, String> headers = HandshakeProcessor.parseHeaders(request);

        assertEquals("server.example.com", headers.get("host"));
        assertEquals("websocket", headers.get("upgrade"));
        // Fixed: Updated to "Upgrade" to match the test input casing
        assertEquals("Upgrade", headers.get("connection"));
        assertEquals("dGhlIHNhbXBsZSBub25jZQ==", headers.get("sec-websocket-key"));
        assertEquals("13", headers.get("sec-websocket-version"));
    }

    @Test
    public void testParseHeadersByteBuffer() {
        String request = "GET /chat HTTP/1.1\r\n" +
                "Host: localhost\r\n" +
                "Upgrade: websocket\r\n" +
                "Connection: Upgrade\r\n\r\n";
        byte[] bytes = request.getBytes();
        ByteBuffer buffer = ByteBuffer.allocate(bytes.length);
        buffer.put(bytes);
        buffer.flip();

        Map<String, String> headers = HandshakeProcessor.parseHeaders(buffer, bytes.length);

        assertEquals("localhost", headers.get("host"));
        assertEquals("websocket", headers.get("upgrade"));
        // Fixed: Updated to "Upgrade" to match the test input casing
        assertEquals("Upgrade", headers.get("connection"));
    }

    @Test
    public void testBuildResponse() {
        String acceptKey = "s3pPLMBiTxaQ9kYGzzhZRbK+xOo=";
        String response = HandshakeProcessor.buildResponse(acceptKey);

        assertTrue(response.contains("HTTP/1.1 101 Switching Protocols"));
        assertTrue(response.contains("Upgrade: websocket"));
        assertTrue(response.contains("Connection: Upgrade"));
        assertTrue(response.contains("Sec-WebSocket-Accept: " + acceptKey));
        assertTrue(response.endsWith("\r\n\r\n"));
    }
}
