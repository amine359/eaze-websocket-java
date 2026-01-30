package com.eaze.websocket.core.codec;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class HandshakeProcessor {
    private static final String GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    
    private static final Queue<MessageDigest> DIGEST_POOL = new ConcurrentLinkedQueue<>();
    private static final int MAX_POOL_SIZE = Runtime.getRuntime().availableProcessors() * 2;

    private static MessageDigest getDigest() {
        MessageDigest md = DIGEST_POOL.poll();
        if (md == null) {
            try {
                return MessageDigest.getInstance("SHA-1");
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
        }
        md.reset();
        return md;
    }

    private static void releaseDigest(MessageDigest md) {
        if (DIGEST_POOL.size() < MAX_POOL_SIZE) {
            DIGEST_POOL.offer(md);
        }
    }

    public static String createAcceptKey(String clientKey) {
        String input = clientKey + GUID;
        MessageDigest md = getDigest();
        try {
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } finally {
            releaseDigest(md);
        }
    }

    public static Map<String, String> parseHeaders(String request) {
        Map<String, String> headers = new HashMap<>();
        int start = 0;
        int end;
        while ((end = request.indexOf("\r\n", start)) != -1) {
            String line = request.substring(start, end);
            start = end + 2;

            if (line.isEmpty()) {
                continue;
            }

            int colonIndex = line.indexOf(":");
            if (colonIndex != -1) {
                String key = line.substring(0, colonIndex).trim().toLowerCase();
                String value = line.substring(colonIndex + 1).trim();
                headers.put(key, value);
            }
        }
        return headers;
    }

    /**
     * Optimized header parsing that works directly on ByteBuffer to reduce String allocations.
     */
    public static Map<String, String> parseHeaders(ByteBuffer buffer, int length) {
        Map<String, String> headers = new HashMap<>();
        int limit = buffer.position() + length;
        int pos = buffer.position();

        // Skip the first line (GET /... HTTP/1.1)
        pos = findNewLine(buffer, pos, limit);
        if (pos == -1) return headers;
        pos += 2;

        while (pos < limit) {
            int lineEnd = findNewLine(buffer, pos, limit);
            if (lineEnd == -1 || lineEnd == pos) break;

            int colonIndex = -1;
            for (int i = pos; i < lineEnd; i++) {
                if (buffer.get(i) == ':') {
                    colonIndex = i;
                    break;
                }
            }

            if (colonIndex != -1) {
                String key = decodeString(buffer, pos, colonIndex - pos).trim().toLowerCase();
                String value = decodeString(buffer, colonIndex + 1, lineEnd - (colonIndex + 1)).trim();
                headers.put(key, value);
            }
            pos = lineEnd + 2;
        }
        return headers;
    }

    private static int findNewLine(ByteBuffer buffer, int start, int limit) {
        for (int i = start; i < limit - 1; i++) {
            if (buffer.get(i) == '\r' && buffer.get(i + 1) == '\n') {
                return i;
            }
        }
        return -1;
    }

    private static String decodeString(ByteBuffer buffer, int offset, int length) {
        if (length <= 0) return "";
        byte[] bytes = new byte[length];
        int oldPos = buffer.position();
        buffer.position(offset);
        buffer.get(bytes);
        buffer.position(oldPos);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static String buildResponse(String acceptKey) {
        return "HTTP/1.1 101 Switching Protocols\r\n" +
               "Upgrade: websocket\r\n" +
               "Connection: Upgrade\r\n" +
               "Sec-WebSocket-Accept: " + acceptKey + "\r\n\r\n";
    }
}
