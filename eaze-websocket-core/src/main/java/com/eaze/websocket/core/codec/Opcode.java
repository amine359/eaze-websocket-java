package com.eaze.websocket.core.codec;

public enum Opcode {
    CONTINUATION(0x0),
    TEXT(0x1),
    BINARY(0x2),
    CLOSE(0x8),
    PING(0x9),
    PONG(0xA);

    private final int code;

    Opcode(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static Opcode fromCode(int code) {
        for (Opcode opcode : values()) {
            if (opcode.code == code) {
                return opcode;
            }
        }
        return null;
    }

    public boolean isControl() {
        return code >= 0x8;
    }
}
