package com.eaze.websocket.demo;

import com.eaze.websocket.core.api.WebSocketListener;
import com.eaze.websocket.core.api.WebSocketSession;
import com.eaze.websocket.core.nio.NioWebSocketServer;

import java.io.IOException;

public class WebSocketDemoServer {
    static void main(String[] args) throws IOException {
        NioWebSocketServer server = new NioWebSocketServer(8081, new WebSocketListener() {
            @Override
            public void onOpen(WebSocketSession session) {
                //System.out.println("New connection: " + session.getRemoteAddress());
                try {
                    session.send("Welcome to Eaze WebSocket Server!");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onMessage(WebSocketSession session, String text) {
                // System.out.println("Received text: " + text);
                try {
                    session.send("Echo: " + text);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onMessage(WebSocketSession session, byte[] data) {
                //System.out.println("Received binary data of length: " + data.length);
            }

            @Override
            public void onClose(WebSocketSession session, int code, String reason) {
                //System.out.println("Closed: " + session.getRemoteAddress() + " Code: " + code);
            }

            @Override
            public void onError(WebSocketSession session, Throwable cause) {
                //System.err.println("Error on " + session.getRemoteAddress() + ": " + cause.getMessage());
            }
        });

        server.start();
        System.out.println("WebSocket Server started on ws://localhost:8081");

        try {
             Thread.currentThread().join();
        } catch (InterruptedException e) {
             Thread.currentThread().interrupt();
        }
    }
}
