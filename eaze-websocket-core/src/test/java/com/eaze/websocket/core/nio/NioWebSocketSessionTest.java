package com.eaze.websocket.core.nio;

import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import static org.junit.jupiter.api.Assertions.*;

public class NioWebSocketSessionTest {

    @Test
    public void testFindEndOfHeaders() {
        String request = "GET /chat HTTP/1.1\r\nHost: localhost\r\n\r\nNextData";
        ByteBuffer buffer = ByteBuffer.wrap(request.getBytes());
        
        int end = NioWebSocketSession.findEndOfHeaders(buffer);
        String headersPart = request.substring(0, end);
        
        assertTrue(headersPart.endsWith("\r\n\r\n"));
        assertEquals("GET /chat HTTP/1.1\r\nHost: localhost\r\n\r\n", headersPart);
    }

    @Test
    public void testFindEndOfHeadersPartial() {
        String request = "GET /chat HTTP/1.1\r\nHost: localhost\r\n"; // Missing one \r\n
        ByteBuffer buffer = ByteBuffer.wrap(request.getBytes());
        
        int end = NioWebSocketSession.findEndOfHeaders(buffer);
        assertEquals(-1, end);
    }

    @Test
    public void testFindEndOfHeadersEmpty() {
        ByteBuffer buffer = ByteBuffer.allocate(0);
        assertEquals(-1, NioWebSocketSession.findEndOfHeaders(buffer));
    }
}
