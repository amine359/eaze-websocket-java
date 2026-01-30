# Implementation & Usage Guide

This guide provides instructions on how to integrate, deploy, and tune the Eaze WebSocket server for production environments.

## 1. Integration API

### Implementing the Listener
The `WebSocketListener` interface is the primary integration point.

```java
public interface WebSocketListener {
    void onOpen(WebSocketSession session);
    void onMessage(WebSocketSession session, String text);
    void onMessage(WebSocketSession session, byte[] data);
    void onClose(WebSocketSession session, int code, String reason);
    void onError(WebSocketSession session, Throwable throwable);
}
```

### Starting the Server
Initialize the `NioWebSocketServer` with a port and your listener.

```java
NioWebSocketServer server = new NioWebSocketServer(8080, myListener);
server.start();
```

### Configuration Parameters
You can tune the thread pools based on your hardware:

```java
// port, listener, acceptorThreads, selectorThreads
NioWebSocketServer server = new NioWebSocketServer(
    8080, 
    myListener, 
    4, // Number of acceptor threads
    Runtime.getRuntime().availableProcessors() // Number of sub-pollers
);
```

## 2. Deployment & OS Tuning

To reach 250,000+ concurrent connections, the underlying Operating System must be tuned.

### Linux (Ubuntu 24 LTS)
Create a script named `tune_os.sh`:

```bash
#!/bin/bash
# Increase file descriptor limits
ulimit -n 1000000
sysctl -w fs.file-max=1000000
sysctl -w fs.nr_open=1000000

# TCP Stack Tuning
sysctl -w net.core.somaxconn=65535
sysctl -w net.ipv4.ip_local_port_range="1024 65535"
sysctl -w net.ipv4.tcp_rmem="1024 4096 16384"
sysctl -w net.ipv4.tcp_wmem="1024 4096 16384"
sysctl -w net.ipv4.tcp_mem="1000000 1000000 1000000"
sysctl -w net.ipv4.tcp_tw_reuse=1
```

Apply persistence by editing `/etc/security/limits.conf`:
```text
* soft nofile 1000000
* hard nofile 1000000
```

### JVM Optimization
Run the application with the following flags for optimal GC performance and Virtual Thread scheduling:

```bash
java \
  -XX:+UseZGC \
  -XX:+ZGenerational \
  -Xmx16G \
  -Xms16G \
  -XX:MaxDirectMemorySize=8G \
  -XX:+AlwaysPreTouch \
  -jar your-app.jar
```

## 3. Client-Side Benchmarking
When testing with 100k+ connections from a single machine, you must bypass the ephemeral port limit (~64k).

**Solution**: Bind the client to multiple local IP aliases.
```bash
sudo ifconfig lo:1 127.0.0.2 up
sudo ifconfig lo:2 127.0.0.3 up
# ... and so on
```
Configure your benchmark tool to cycle through these source IPs.
