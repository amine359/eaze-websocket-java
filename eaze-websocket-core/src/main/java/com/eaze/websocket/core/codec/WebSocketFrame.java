package com.eaze.websocket.core.codec;

import java.nio.ByteBuffer;

public class WebSocketFrame {
    private final boolean fin;
    private final Opcode opcode;
    private final ByteBuffer payload;
    private final boolean masked;
    private final byte[] maskingKey;

    public WebSocketFrame(boolean fin, Opcode opcode, ByteBuffer payload) {
        this(fin, opcode, payload, false, null);
    }

    public WebSocketFrame(boolean fin, Opcode opcode, ByteBuffer payload, boolean masked, byte[] maskingKey) {
        this.fin = fin;
        this.opcode = opcode;
        this.payload = payload;
        this.masked = masked;
        this.maskingKey = maskingKey;
    }

    public boolean isFin() { return fin; }
    public Opcode getOpcode() { return opcode; }
    public ByteBuffer getPayload() { return payload; }
    public boolean isMasked() { return masked; }
    public byte[] getMaskingKey() { return maskingKey; }
}
