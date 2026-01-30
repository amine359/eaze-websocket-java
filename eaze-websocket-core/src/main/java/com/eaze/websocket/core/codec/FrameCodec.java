package com.eaze.websocket.core.codec;

import com.eaze.websocket.core.buffer.BufferPool;
import java.nio.ByteBuffer;

public class FrameCodec {

    public static WebSocketFrame decode(ByteBuffer buffer) {
        if (buffer.remaining() < 2) return null;

        buffer.mark();
        byte b1 = buffer.get();
        boolean fin = (b1 & 0x80) != 0;
        Opcode opcode = Opcode.fromCode(b1 & 0x0F);

        byte b2 = buffer.get();
        boolean masked = (b2 & 0x80) != 0;
        long payloadLen = b2 & 0x7F;

        if (payloadLen == 126) {
            if (buffer.remaining() < 2) { buffer.reset(); return null; }
            payloadLen = buffer.getShort() & 0xFFFF;
        } else if (payloadLen == 127) {
            if (buffer.remaining() < 8) { buffer.reset(); return null; }
            payloadLen = buffer.getLong();
        }

        byte[] maskingKey = null;
        if (masked) {
            if (buffer.remaining() < 4) { buffer.reset(); return null; }
            maskingKey = new byte[4];
            buffer.get(maskingKey);
        }

        if (buffer.remaining() < payloadLen) {
            buffer.reset();
            return null;
        }

        // Potential optimization: Use a pooled byte array or a ReadOnlyBuffer view
        byte[] payloadData = new byte[(int) payloadLen];
        buffer.get(payloadData);

        if (masked) {
            for (int i = 0; i < payloadData.length; i++) {
                payloadData[i] ^= maskingKey[i % 4];
            }
        }

        return new WebSocketFrame(fin, opcode, ByteBuffer.wrap(payloadData), masked, maskingKey);
    }

    public static ByteBuffer encode(WebSocketFrame frame) {
        ByteBuffer payload = frame.getPayload();
        int payloadLen = payload.remaining();
        int headerLen = 2 + (payloadLen <= 125 ? 0 : (payloadLen <= 65535 ? 2 : 8)) + (frame.isMasked() ? 4 : 0);

        // Optimization: Use BufferPool instead of allocateDirect
        ByteBuffer buffer = BufferPool.allocate(headerLen + payloadLen);

        byte b1 = (byte) (frame.isFin() ? 0x80 : 0x00);
        b1 |= (byte) (frame.getOpcode().getCode() & 0x0F);
        buffer.put(b1);

        byte b2 = (byte) (frame.isMasked() ? 0x80 : 0x00);
        if (payloadLen <= 125) {
            b2 |= (byte) payloadLen;
            buffer.put(b2);
        } else if (payloadLen <= 65535) {
            b2 |= 126;
            buffer.put(b2);
            buffer.putShort((short) payloadLen);
        } else {
            b2 |= 127;
            buffer.put(b2);
            buffer.putLong(payloadLen);
        }

        if (frame.isMasked()) {
            buffer.put(frame.getMaskingKey());
            byte[] maskingKey = frame.getMaskingKey();
            int pos = payload.position();
            for (int i = 0; i < payloadLen; i++) {
                buffer.put((byte) (payload.get(pos + i) ^ maskingKey[i % 4]));
            }
        } else {
            buffer.put(payload.duplicate());
        }

        buffer.flip();
        return buffer;
    }
}
