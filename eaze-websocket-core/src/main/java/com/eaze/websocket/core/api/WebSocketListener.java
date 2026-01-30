package com.eaze.websocket.core.api;

import com.eaze.websocket.core.codec.WebSocketFrame;

public interface WebSocketListener {
    void onOpen(WebSocketSession session);
    void onMessage(WebSocketSession session, String text);
    void onMessage(WebSocketSession session, byte[] data);
    void onClose(WebSocketSession session, int code, String reason);
    void onError(WebSocketSession session, Throwable cause);
}
