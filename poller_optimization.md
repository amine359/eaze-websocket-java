# MasterPoller Optimization Analysis & Solution

## 1. Performance Analysis

Based on the provided metrics:
- **Total Connection Attempts**: 320,000
- **Total Failures**: 32,501 (~10.16% failure rate)
- **Total Duration**: 1,662.8 seconds
- **Effective Throughput**: ~172.9 successful connections/sec

### Identified Bottlenecks

1.  **Single-Threaded Acceptor (MasterPoller) Saturation**: 
    The current implementation uses a single platform thread (`WebSocket-Acceptor`) to perform `serverChannel.accept()`. While `accept()` is generally fast, the overhead of spawning a virtual thread and the sequential nature of the loop creates a bottleneck during high-concurrency bursts (e.g., the benchmark's 10,000 connections per 50ms).
    
2.  **OS Backlog Overflow**: 
    The `ServerSocketChannel` is configured with a backlog of 65,535. During bursts of 200,000 connections/sec, if the single acceptor thread cannot drain the queue faster than it fills, the OS begins rejecting new SYN packets. This is the primary cause of the 32,501 failures.

3.  **JDK Internal Poller Contention**:
    In Java 21+, virtual threads performing blocking I/O are managed by a single internal `Poller` platform thread (identified as `MasterPoller` in thread dumps). For 320,000 concurrent connections, the overhead of the underlying `epoll_wait` (on Linux) or `IOCP` (on Windows) and the subsequent unparking of virtual threads can overwhelm a single CPU core.

---

## 2. Proposed Solution: Multi-Threaded MasterPoller Architecture

To resolve these bottlenecks, we will transition the connection acceptance logic into a scalable, multi-threaded "MasterPoller" architecture.

### Key Changes

1.  **Multi-Acceptor Pool**: 
    Instead of one `acceptorThread`, the server will maintain a pool of platform threads, all competing to `accept()` connections from the same `ServerSocketChannel`. Modern operating systems (Linux, Windows) efficiently handle multiple threads calling `accept` on the same socket (the "Thundering Herd" problem is largely mitigated in modern kernels).

2.  **Configurable Parallelism**:
    The number of `MasterPoller` threads will be configurable via the `NioWebSocketServer` constructor, allowing tuning based on the available CPU cores and expected connection rates.

3.  **Thread Safety**:
    - `ServerSocketChannel.accept()` is thread-safe.
    - The `running` flag (AtomicBoolean) ensures all poller threads can be shut down safely.
    - Virtual thread handoff remains isolated per connection.

---

## 3. Technical Design

### Constructor Update
The `NioWebSocketServer` constructor will be updated to accept a `pollerThreads` parameter.

```java
public NioWebSocketServer(int port, WebSocketListener listener, int pollerThreads) throws IOException {
    this.listener = listener;
    this.pollerThreads = pollerThreads;
    // ... channel initialization ...
}
```

### Multi-Threaded Startup
The `start()` method will initialize a pool of threads.

```java
public void start() {
    if (running.compareAndSet(false, true)) {
        for (int i = 0; i < pollerThreads; i++) {
            Thread.ofPlatform()
                    .name("MasterPoller-" + i)
                    .start(this::runAcceptor);
        }
    }
}
```

### Resource Management
The `stop()` method must be updated to track and interrupt all poller threads to ensure a clean shutdown.

---

## 4. Expected Impact

1.  **Improved Success Rate**: 
    By having multiple threads draining the OS backlog, the probability of the backlog filling up during bursts is significantly reduced. We expect the failure rate to drop from ~10% to near 0% under the same load.

2.  **Higher Connection Throughput**: 
    The server will be able to handle much higher connection arrival rates (Connection-Per-Second), fully utilizing multi-core systems during the connection establishment phase.

3.  **Reduced Latency**: 
    The time between a client initiating a TCP handshake and the server completing the WebSocket handshake will decrease, as the "accept-to-handoff" delay is minimized.

---

## 5. Further Optimization Recommendations

While multi-threading the acceptor solves the immediate connection failure issue, for 10M+ connections, the following should also be considered:
- **Multiple Selectors**: Transitioning to a model where connections are distributed across multiple `Selectors` (each in its own platform thread) to avoid the single internal JDK `Poller` bottleneck.
- **Direct Handshake**: Performing the initial HTTP handshake within the `MasterPoller` thread before handing off to a virtual thread to further reduce context switching for short-lived or failed handshakes.
