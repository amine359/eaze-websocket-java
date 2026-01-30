package com.eaze.websocket.core.nio;

import com.eaze.websocket.core.api.WebSocketListener;
import com.eaze.websocket.core.api.WebSocketSession;
import com.eaze.websocket.core.buffer.BufferPool;
import com.eaze.websocket.core.codec.FrameCodec;
import com.eaze.websocket.core.codec.HandshakeProcessor;
import com.eaze.websocket.core.codec.Opcode;
import com.eaze.websocket.core.codec.WebSocketFrame;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * WebSocket session that handles messaging.
 * Optimized for multi-selector architecture where reads are triggered by a Selector
 * and processed in virtual threads.
 */
public class NioWebSocketSession implements WebSocketSession {
    private final SocketChannel channel;
    private final WebSocketListener listener;
    private final AtomicBoolean open = new AtomicBoolean(true);
    private boolean handshaked = false;
    
    private ByteBuffer stickyBuffer = null;
    private final ReentrantLock writeLock = new ReentrantLock();

    public NioWebSocketSession(SocketChannel channel, WebSocketListener listener) {
        this.channel = channel;
        this.listener = listener;
    }

    /**
     * Handles incoming data when the Selector detects the channel is readable.
     * This is executed in a virtual thread.
     */
    public void handleRead() {
        if (!open.get()) return;

        ByteBuffer readBuffer = BufferPool.acquire();
        try {
            // Restore any leftover data from previous read
            if (stickyBuffer != null) {
                readBuffer.put(stickyBuffer);
                stickyBuffer = null;
            }

            int bytesRead = channel.read(readBuffer);
            if (bytesRead == -1) {
                close(1000, "EOF");
                return;
            }

            if (bytesRead == 0 && readBuffer.position() == 0) {
                return;
            }

            readBuffer.flip();

            if (!handshaked) {
                if (!doHandshake(readBuffer)) {
                    // Incomplete handshake, save buffer and wait for more data
                    if (readBuffer.hasRemaining()) {
                        saveRemainingData(readBuffer);
                    }
                    return;
                }
            }

            if (handshaked) {
                processFrames(readBuffer);
            }

            // Save any remaining data for next iteration
            if (readBuffer.hasRemaining()) {
                saveRemainingData(readBuffer);
            }
        } catch (IOException e) {
            onFailure(e);
        } finally {
            BufferPool.release(readBuffer);
        }
    }

    public void onFailure(Throwable t) {
        if (open.get()) {
            listener.onError(this, t);
            try {
                close(1006, t.getMessage());
            } catch (IOException ignore) {}
        }
    }

    private void saveRemainingData(ByteBuffer buffer) {
        stickyBuffer = ByteBuffer.allocate(buffer.remaining());
        stickyBuffer.put(buffer);
        stickyBuffer.flip();
    }
    
    public void setInitialBuffer(ByteBuffer buffer) {
        if (buffer != null && buffer.hasRemaining()) {
            saveRemainingData(buffer);
        }
    }

    private boolean doHandshake(ByteBuffer buffer) throws IOException {
        int endOfHeaders = findEndOfHeaders(buffer);
        if (endOfHeaders == -1) {
            return false;
        }

        Map<String, String> headers = HandshakeProcessor.parseHeaders(buffer, endOfHeaders);
        buffer.position(buffer.position() + endOfHeaders);

        if (headers.containsKey("sec-websocket-key")) {
            String acceptKey = HandshakeProcessor.createAcceptKey(headers.get("sec-websocket-key"));
            String response = HandshakeProcessor.buildResponse(acceptKey);

            ByteBuffer respBuffer = ByteBuffer.wrap(response.getBytes(StandardCharsets.UTF_8));
            while (respBuffer.hasRemaining()) {
                channel.write(respBuffer);
            }

            handshaked = true;
            listener.onOpen(this);

            if (buffer.hasRemaining()) {
                processFrames(buffer);
            }
            return true;
        } else {
            close(400, "Missing Sec-WebSocket-Key");
            return false;
        }
    }

    public static int findEndOfHeaders(ByteBuffer buffer) {
        int pos = buffer.position();
        int limit = buffer.limit();
        for (int i = pos; i <= limit - 4; i++) {
            if (buffer.get(i) == '\r' && buffer.get(i + 1) == '\n' &&
                buffer.get(i + 2) == '\r' && buffer.get(i + 3) == '\n') {
                return i - pos + 4;
            }
        }
        return -1;
    }

    private void processFrames(ByteBuffer buffer) throws IOException {
        while (open.get()) {
            WebSocketFrame frame = FrameCodec.decode(buffer);
            if (frame == null) {
                return;
            }

            if (frame.isFin()) {
                Opcode op = frame.getOpcode();
                if (op == Opcode.TEXT) {
                    String text = StandardCharsets.UTF_8.decode(frame.getPayload()).toString();
                    listener.onMessage(this, text);
                } else if (op == Opcode.BINARY) {
                    byte[] bytes = new byte[frame.getPayload().remaining()];
                    frame.getPayload().get(bytes);
                    listener.onMessage(this, bytes);
                } else if (op == Opcode.CLOSE) {
                    close(1000, "Client Close");
                    return;
                } else if (op == Opcode.PING) {
                    sendPong(frame.getPayload());
                }
            }
        }
    }

    @Override
    public void send(String text) throws IOException {
        if (!open.get()) return;
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        WebSocketFrame frame = new WebSocketFrame(true, Opcode.TEXT, ByteBuffer.wrap(bytes), false, null);
        sendFrame(frame);
    }

    @Override
    public void send(byte[] data) throws IOException {
        if (!open.get()) return;
        WebSocketFrame frame = new WebSocketFrame(true, Opcode.BINARY, ByteBuffer.wrap(data), false, null);
        sendFrame(frame);
    }

    private void sendPong(ByteBuffer payload) throws IOException {
        WebSocketFrame frame = new WebSocketFrame(true, Opcode.PONG, payload, false, null);
        sendFrame(frame);
    }

    private void sendFrame(WebSocketFrame frame) throws IOException {
        ByteBuffer encoded = FrameCodec.encode(frame);
        try {
            writeLock.lock();
            try {
                if (!open.get() && frame.getOpcode() != Opcode.CLOSE) return;
                while (encoded.hasRemaining()) {
                    int written = channel.write(encoded);
                    if (written == 0 && encoded.hasRemaining()) {
                        Thread.yield();
                    }
                }
            } finally {
                writeLock.unlock();
            }
        } finally {
            BufferPool.release(encoded);
        }
    }

    @Override
    public void close(int code, String reason) throws IOException {
        if (open.compareAndSet(true, false)) {
            try {
                byte[] reasonBytes = reason.getBytes(StandardCharsets.UTF_8);
                ByteBuffer payload = ByteBuffer.allocate(2 + reasonBytes.length);
                payload.putShort((short) code);
                payload.put(reasonBytes);
                payload.flip();
                sendFrame(new WebSocketFrame(true, Opcode.CLOSE, payload));
            } catch (Exception ignore) {
            } finally {
                try {
                    listener.onClose(this, code, reason);
                } finally {
                    channel.close();
                }
            }
        }
    }

    @Override
    public boolean isOpen() {
        return open.get();
    }

    public SocketChannel getChannel() {
        return channel;
    }

    public void setHandshaked(boolean handshaked) {
        this.handshaked = handshaked;
    }

    @Override
    public String getRemoteAddress() {
        try {
            return channel.getRemoteAddress().toString();
        } catch (IOException e) {
            return "unknown";
        }
    }
}
