package com.eaze.websocket.core.ssl;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class SslHandler {
    private final SSLEngine sslEngine;
    private final ByteBuffer netReadBuffer;
    private final ByteBuffer netWriteBuffer;
    private final ByteBuffer appReadBuffer;

    public SslHandler(SSLEngine sslEngine) {
        this.sslEngine = sslEngine;
        this.netReadBuffer = ByteBuffer.allocateDirect(sslEngine.getSession().getPacketBufferSize());
        this.netWriteBuffer = ByteBuffer.allocateDirect(sslEngine.getSession().getPacketBufferSize());
        this.appReadBuffer = ByteBuffer.allocateDirect(sslEngine.getSession().getApplicationBufferSize());
    }

    // This would contain complex wrap/unwrap logic for SSLEngine
    // For a high-performance library, this needs to be meticulously implemented.
    // I'll provide a simplified structure.
    
    public ByteBuffer unwrap(ByteBuffer src) throws SSLException {
        appReadBuffer.clear();
        SSLEngineResult result = sslEngine.unwrap(src, appReadBuffer);
        // Handle result status (NEED_TASK, NEED_WRAP, etc.)
        appReadBuffer.flip();
        return appReadBuffer;
    }

    public ByteBuffer wrap(ByteBuffer src) throws SSLException {
        netWriteBuffer.clear();
        sslEngine.wrap(src, netWriteBuffer);
        netWriteBuffer.flip();
        return netWriteBuffer;
    }
}
