package com.eaze.websocket.core.api;

import java.io.IOException;

public interface WebSocketSession {
    void send(String text) throws IOException;
    void send(byte[] data) throws IOException;
    void close(int code, String reason) throws IOException;
    boolean isOpen();
    String getRemoteAddress();
}
