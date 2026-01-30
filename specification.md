# Technical Specification: Eaze WebSocket Architecture

This document details the architectural design and technical implementation of the Eaze WebSocket server.

## 1. Architectural Overview

Eaze WebSocket is designed from the ground up for high-concurrency scenarios. It avoids the common pitfalls of traditional NIO implementations by distributing the workload across multiple specialized components.

### 1.1 Multi-Poller Design
Standard Java NIO implementations often rely on a single `Selector` to manage all connections. At high scales (100k+ connections), the internal synchronization of the `Selector` becomes a bottleneck. Eaze WebSocket utilizes a **Multi-Poller architecture**:

*   **MasterPollers**: A pool of threads dedicated to `accept()` operations and the initial HTTP-to-WebSocket handshake. This ensures that new connection attempts are never blocked by existing traffic.
*   **SubPollers**: Multiple independent poller threads, each managing its own `Selector` and a subset of active connections. This sharding strategy parallelizes I/O readiness checks and significantly reduces lock contention.

### 1.2 Concurrency Model: Hybrid NIO + Virtual Threads
Eaze WebSocket leverages **Java 21+ Virtual Threads** to achieve a "one-thread-per-message" processing model without the memory overhead of platform threads.

1.  **I/O Notification**: A `SubPoller` thread detects that a channel is ready for reading.
2.  **Virtual Thread Handoff**: The `SubPoller` immediately spawns a Virtual Thread to handle the read operation and message decoding.
3.  **Non-Blocking Selection**: While the Virtual Thread processes the message, the `SubPoller` continues to check other connections. The interest for the active connection is temporarily disabled to prevent concurrent reads on the same socket.
4.  **Re-Registration**: Once processing is complete, the Virtual Thread re-enables the interest for the socket in the `SubPoller`'s `Selector`.

## 2. Networking Layer

### 2.1 Buffer Management
To minimize GC overhead, the system employs a **Direct Buffer Pool**:
*   **Zero-Copy Intent**: By using `ByteBuffer.allocateDirect()`, the server interacts directly with the OS network stack, avoiding unnecessary data copying between JVM heap and native memory.
*   **Pooled Resources**: `ByteBuffer` instances are recycled through a `ConcurrentLinkedQueue`-based pool, preventing the expensive allocation of direct memory for every message.

### 2.2 Framing & Codec
The `FrameCodec` implementation provides efficient encoding and decoding of WebSocket frames as per RFC 6455. It handles:
*   Text and Binary frames.
*   Masking (for client-to-server messages).
*   Control frames (Ping, Pong, Close).
*   Fragmentation (continuation frames).

## 3. Scalability Characteristics

### 3.1 Connection Lifecycle
*   **Handshake**: Performed synchronously by `MasterPollers` to ensure only valid WebSocket connections enter the `SubPoller` pools.
*   **Keep-Alive**: Leverages TCP Keep-Alive and WebSocket Ping/Pong to maintain long-lived connections through middleboxes.
*   **Graceful Shutdown**: Ensures all connections are closed with the appropriate status codes before the server stops.

### 3.2 Performance Bottleneck Mitigation
*   **Context Switching**: Minimized by using a fixed number of platform threads for polling and lightweight virtual threads for logic.
*   **Lock Contention**: Reduced by sharding connections across `SubPollers` and using thread-safe non-blocking queues for registration.
*   **Memory Footprint**: Each connection's state is kept minimal. Buffers are only held during active I/O.
