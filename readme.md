# Eaze WebSocket Java

Eaze WebSocket is a high-performance, ultra-scalable WebSocket server implementation for Java, engineered to handle millions of concurrent connections with minimal resource overhead. 

Built on a custom Multi-Poller NIO architecture and leveraging Java's Project Loom (Virtual Threads), Eaze WebSocket achieves industry-leading scalability on commodity hardware.

## 🚀 Key Features

*   **Extreme Scalability**: Proven to maintain **250,000+ stable concurrent connections** on a single machine.
*   **Multi-Poller NIO**: Distributed I/O handling across multiple Selectors to eliminate synchronization bottlenecks.
*   **Virtual Thread Integration**: Offloads message processing to lightweight virtual threads for maximum concurrency without the overhead of platform threads.
*   **Efficient Memory Management**: Integrated `BufferPool` using Direct ByteBuffers to minimize GC pressure and allocation latency.
*   **Zero-Dependency Core**: The core library is lightweight and has no external runtime dependencies.
*   **Low Latency**: Optimized for real-time applications requiring rapid message delivery.

## 🛡️ Protocol Compliance

Eaze WebSocket is fully compliant with **RFC 6455**, the standard for the WebSocket protocol. It successfully passes the rigorous test suites, ensuring seamless interoperability with all modern browsers and WebSocket clients.

## 📊 Performance at a Glance

In rigorous testing on an Intel Core i7-14700K (20 cores) with 32GB RAM running Ubuntu 24 LTS:

| Metric | Result |
| :--- | :--- |
| **Stable Concurrent Connections** | 250,000 |
| **Peak Connections (OS Limit)** | 300,000+ |
| **System Memory Usage (250k)** | ~5 GB |
| **CPU Idle (at 250k)** | ~85% |

## 🛠️ Quick Start

### Installation

Clone the repository and build using Gradle:

```bash
git clone https://github.com/your-repo/eaze-websocket-java.git
cd eaze-websocket-java
./gradlew build
```

### Basic Usage

```java
import com.eaze.websocket.core.api.WebSocketListener;
import com.eaze.websocket.core.api.WebSocketSession;
import com.eaze.websocket.core.nio.NioWebSocketServer;

public class MyServer {
    public static void main(String[] args) throws Exception {
        NioWebSocketServer server = new NioWebSocketServer(8080, new WebSocketListener() {
            @Override
            public void onOpen(WebSocketSession session) {
                System.out.println("Connected: " + session.getRemoteAddress());
            }

            @Override
            public void onMessage(WebSocketSession session, String text) {
                try {
                    session.send("Echo: " + text);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onClose(WebSocketSession session, int code, String reason) {
                System.out.println("Closed: " + session.getRemoteAddress());
            }
        });

        server.start();
        System.out.println("Server started on port 8080");
    }
}
```

## 📖 Documentation

For detailed information, please refer to the following:

*   [Technical Specification](specification.md) - Architecture and Concurrency Model.
*   [Technology Stack](tech_stack.md) - Dependencies and Requirements.
*   [Usage Guide](usages.md) - Deployment and API Reference.
*   [Full Documentation & Benchmarks](documentation.md) - Performance analysis and OS tuning.

## ⚖️ License

This project is licensed under the MIT License.

---
Developed and maintained by [eazewebit.com](https://eazewebit.com).
