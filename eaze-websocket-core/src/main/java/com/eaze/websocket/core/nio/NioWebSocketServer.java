package com.eaze.websocket.core.nio;

import com.eaze.websocket.core.api.WebSocketListener;
import com.eaze.websocket.core.codec.HandshakeProcessor;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * High-performance WebSocket server optimized for 10M+ concurrent connections.
 * 
 * Architecture:
 * 1. MasterPoller Pool: Multiple platform threads performing blocking accept() and Direct Handshake.
 * 2. SubPoller Pool: Multiple platform threads each with its own Selector to handle established connections.
 * 3. Virtual Threads: Used for message processing only after successful handshake, minimizing context switching.
 */
public class NioWebSocketServer {
    private final WebSocketListener listener;
    private final ServerSocketChannel serverChannel;
    private final int pollerThreads;
    private final int selectorThreads;
    private final AtomicBoolean running = new AtomicBoolean(false);
    
    private final List<Thread> acceptorThreads = new ArrayList<>();
    private final SubPoller[] subPollers;
    private final AtomicInteger nextSelector = new AtomicInteger(0);

    public NioWebSocketServer(int port, WebSocketListener listener) throws IOException {
        this(port, listener, 2, Runtime.getRuntime().availableProcessors());
    }

    public NioWebSocketServer(int port, WebSocketListener listener, int pollerThreads, int selectorThreads) throws IOException {
        this.listener = listener;
        this.pollerThreads = pollerThreads;
        this.selectorThreads = selectorThreads;
        
        this.serverChannel = ServerSocketChannel.open();
        this.serverChannel.configureBlocking(true);
        this.serverChannel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
        this.serverChannel.bind(new InetSocketAddress(port), 65535);

        this.subPollers = new SubPoller[selectorThreads];
        for (int i = 0; i < selectorThreads; i++) {
            this.subPollers[i] = new SubPoller(i);
        }
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            for (int i = 0; i < pollerThreads; i++) {
                Thread t = Thread.ofPlatform()
                        .name("Eaze-MasterPoller-" + i)
                        .start(this::runAcceptor);
                acceptorThreads.add(t);
            }
        }
    }

    private void runAcceptor() {
        while (running.get()) {
            try {
                SocketChannel client = serverChannel.accept();
                if (client != null) {
                    handleNewConnection(client);
                }
            } catch (IOException e) {
                if (running.get()) {
                    System.err.println("Accept error: " + e.getMessage());
                }
            }
        }
    }

    private void handleNewConnection(SocketChannel client) {
        try {
            client.configureBlocking(true);
            client.setOption(StandardSocketOptions.TCP_NODELAY, true);
            client.setOption(StandardSocketOptions.SO_KEEPALIVE, true);

            ByteBuffer buffer = ByteBuffer.allocate(4096);
            int bytesRead = client.read(buffer);
            if (bytesRead <= 0) {
                client.close();
                return;
            }

            buffer.flip();
            int endOfHeaders = NioWebSocketSession.findEndOfHeaders(buffer);
            if (endOfHeaders == -1) {
                // Incomplete handshake - for 10M+ scale, we could hand this off to a 
                // specialized poller, but for now we close to protect the acceptor.
                client.close();
                return;
            }

            Map<String, String> headers = HandshakeProcessor.parseHeaders(buffer, endOfHeaders);
            if (headers.containsKey("sec-websocket-key")) {
                String acceptKey = HandshakeProcessor.createAcceptKey(headers.get("sec-websocket-key"));
                String response = HandshakeProcessor.buildResponse(acceptKey);
                
                ByteBuffer respBuffer = ByteBuffer.wrap(response.getBytes(StandardCharsets.UTF_8));
                while (respBuffer.hasRemaining()) {
                    client.write(respBuffer);
                }
                
                // Advance buffer past headers
                buffer.position(buffer.position() + endOfHeaders);
                
                NioWebSocketSession session = new NioWebSocketSession(client, listener);
                session.setHandshaked(true);
                
                // Pass any leftover data (e.g. first frames)
                if (buffer.hasRemaining()) {
                    session.setInitialBuffer(buffer);
                }
                
                listener.onOpen(session);
                
                // Hand off to a SubPoller
                int idx = Math.abs(nextSelector.getAndIncrement() % selectorThreads);
                subPollers[idx].register(session);
            } else {
                client.close();
            }
        } catch (IOException e) {
            try { client.close(); } catch (IOException ignore) {}
        }
    }

    public void stop() throws IOException {
        running.set(false);
        serverChannel.close();
        for (Thread t : acceptorThreads) {
            t.interrupt();
        }
        for (SubPoller sp : subPollers) {
            sp.stop();
        }
    }

    public WebSocketListener getListener() {
        return listener;
    }

    public boolean isRunning() {
        return running.get();
    }
}
