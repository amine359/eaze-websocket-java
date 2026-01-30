# Technology Stack & Requirements

The Eaze WebSocket project is built on modern Java technologies to ensure maximum performance and maintainability.

## 1. Core Technologies

| Component | Technology | Description |
| :--- | :--- | :--- |
| **Language** | Java 21+ | Utilizes Virtual Threads (Project Loom) and modern API features. |
| **I/O Engine** | Java NIO | Non-blocking I/O for scalable network communication. |
| **Concurrency** | Project Loom | Lightweight virtual threads for massive concurrency. |
| **Build System** | Gradle | Dependency management and build automation. |

## 2. Dependencies

The core library (`eaze-websocket-core`) is designed to be **zero-dependency**, ensuring it can be easily integrated into any project without version conflicts.

### Development Dependencies
*   **JUnit 5**: For unit and integration testing.
*   **Python 3**: Used for benchmarking and stress testing scripts.

## 3. System Requirements

### Minimum Requirements
*   **JDK**: Java 21 or higher.
*   **RAM**: 2GB (for small-scale deployments).
*   **OS**: Linux, Windows, or macOS.

### High-Scale Deployment Requirements (250k+ Connections)
To achieve the benchmarks documented in this project, the following environment is recommended:

*   **OS**: Ubuntu 24.04 LTS (Optimized for high file descriptor counts).
*   **CPU**: High-performance multi-core processor (e.g., Intel Core i7-14700K or better).
*   **RAM**: 16GB+ (32GB recommended for 300k+ connections).
*   **Network**: High-bandwidth network interface with tuned TCP stack.

## 4. Hardware Validation Environment

The performance benchmarks were validated on the following hardware:

*   **Processor**: Intel Core i7-14700K (20 Cores: 8P + 12E, 28 Threads).
*   **Memory**: 32GB DDR5 RAM.
*   **Storage**: NVMe SSD.
*   **Operating System**: Ubuntu 24.04 LTS.
