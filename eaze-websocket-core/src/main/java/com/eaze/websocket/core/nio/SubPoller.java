package com.eaze.websocket.core.nio;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SubPoller manages a subset of active WebSocket connections using a dedicated Selector
 * and platform thread. This avoids the bottleneck of the single JDK internal poller.
 */
public class SubPoller implements Runnable {
    private final Selector selector;
    private final Queue<NioWebSocketSession> registrationQueue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final Thread thread;

    public SubPoller(int index) throws IOException {
        this.selector = Selector.open();
        this.thread = Thread.ofPlatform()
                .name("Eaze-SubPoller-" + index)
                .start(this);
    }

    public void register(NioWebSocketSession session) {
        registrationQueue.offer(session);
        selector.wakeup();
    }

    @Override
    public void run() {
        while (running.get()) {
            try {
                processRegistrations();
                
                int readyChannels = selector.select(500);
                if (readyChannels == 0) continue;

                Iterator<SelectionKey> it = selector.selectedKeys().iterator();
                while (it.hasNext()) {
                    SelectionKey key = it.next();
                    it.remove();

                    if (key.isValid() && key.isReadable()) {
                        NioWebSocketSession session = (NioWebSocketSession) key.attachment();
                        // Disable interest while processing to avoid concurrent execution for the same session
                        key.interestOps(0);
                        
                        Thread.startVirtualThread(() -> {
                            try {
                                session.handleRead();
                                if (session.isOpen()) {
                                    // Re-enable interest and wakeup selector
                                    key.interestOps(SelectionKey.OP_READ);
                                    selector.wakeup();
                                }
                            } catch (Exception e) {
                                session.onFailure(e);
                            }
                        });
                    }
                }
            } catch (IOException e) {
                if (running.get()) {
                    System.err.println("SubPoller error: " + e.getMessage());
                }
            }
        }
    }

    private void processRegistrations() {
        NioWebSocketSession session;
        while ((session = registrationQueue.poll()) != null) {
            try {
                session.getChannel().configureBlocking(false);
                session.getChannel().register(selector, SelectionKey.OP_READ, session);
            } catch (IOException e) {
                session.onFailure(e);
            }
        }
    }

    public void stop() {
        running.set(false);
        selector.wakeup();
        try {
            selector.close();
        } catch (IOException ignore) {}
    }
}
