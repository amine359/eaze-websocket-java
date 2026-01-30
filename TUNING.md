# High-Scale WebSocket Server Tuning Guide

## 1. Kernel Tuning (Linux)
To handle 10 million+ concurrent connections, you must increase file descriptor limits and tune the TCP stack.

Save the following as `tune_kernel.sh` and run as root:

```bash
#!/bin/bash

# Max open files
ulimit -n 10000000

# Kernel limits
sysctl -w fs.file-max=10000000
sysctl -w fs.nr_open=10000000

# Network Core
sysctl -w net.core.somaxconn=65535
sysctl -w net.core.netdev_max_backlog=65535
sysctl -w net.core.rmem_max=16777216
sysctl -w net.core.wmem_max=16777216

# TCP Tuning
sysctl -w net.ipv4.tcp_max_syn_backlog=65535
sysctl -w net.ipv4.tcp_fin_timeout=15
sysctl -w net.ipv4.tcp_tw_reuse=1
sysctl -w net.ipv4.ip_local_port_range="1024 65000"
sysctl -w net.ipv4.tcp_rmem="4096 87380 16777216"
sysctl -w net.ipv4.tcp_wmem="4096 65536 16777216"
sysctl -w net.ipv4.tcp_keepalive_time=600
sysctl -w net.ipv4.tcp_keepalive_intvl=60
sysctl -w net.ipv4.tcp_keepalive_probes=3
sysctl -w net.ipv4.tcp_mem="10000000 10000000 10000000"

echo "Kernel tuning applied."
```

Make sure to add these to `/etc/sysctl.conf` and `/etc/security/limits.conf` for persistence.

## 2. JVM Tuning
For 10m connections, GC pauses are the enemy. Use ZGC (Generational) in Java 21+.

Run the server with:

```bash
java \
  --enable-preview \
  -XX:+UseZGC \
  -XX:+ZGenerational \
  -Xmx32G \
  -Xms32G \
  -XX:MaxDirectMemorySize=16G \
  -XX:+AlwaysPreTouch \
  -Djdk.virtualThreadScheduler.parallelism=32 \
  -jar eaze-websocket-server.jar
```

### Notes:
*   `-XX:+ZGenerational`: Essential for high throughput with ZGC in newer JDKs.
*   `-Xmx32G`: Each connection takes minimal heap (a few KB). 10m * 2KB = ~20GB. Leave room for buffers.
*   `-XX:MaxDirectMemorySize=16G`: Large space for DirectByteBuffer pool.


## OS Tuning (Linux)

Add/modify these settings in `/etc/sysctl.conf` and run `sysctl -p`:

```ini
# Increase max file descriptors
fs.file-max = 10000000
fs.nr_open = 10000000

# Increase port range for clients
net.ipv4.ip_local_port_range = 1024 65535

# TCP memory tuning
net.ipv4.tcp_mem = 10000000 10000000 10000000
net.ipv4.tcp_rmem = 1024 4096 16384
net.ipv4.tcp_wmem = 1024 4096 16384

# Increase backlog
net.core.somaxconn = 65535
net.ipv4.tcp_max_syn_backlog = 65535

# Enable fast recycling of sockets
net.ipv4.tcp_tw_reuse = 1
```

Also, update `/etc/security/limits.conf`:
```text
* soft nofile 10000000
* hard nofile 10000000
```

## Client-Side IO Tuning (For Benchmarking)

When running benchmarks from a single client machine, you are limited to ~64,000 outgoing connections per source IP address due to ephemeral port exhaustion (TCP limits). To simulate more connections (e.g., 100k+), you must configure multiple source IP addresses (aliases) on the loopback interface.

**Command to add aliases (Linux):**

```bash
sudo ifconfig lo:1 127.0.0.2 netmask 255.0.0.0 up
sudo ifconfig lo:2 127.0.0.3 netmask 255.0.0.0 up
sudo ifconfig lo:3 127.0.0.4 netmask 255.0.0.0 up
```

Ensure your benchmark tool is configured to bind to these different source IPs round-robin.

## OS Tuning (Windows)

For Windows environments, adjust the following Registry keys:

1.  **MaxUserPort**: `HKEY_LOCAL_MACHINE\SYSTEM\CurrentControlSet\Services\Tcpip\Parameters\MaxUserPort` -> Set to `65534`.
2.  **TcpTimedWaitDelay**: `HKEY_LOCAL_MACHINE\SYSTEM\CurrentControlSet\Services\Tcpip\Parameters\TcpTimedWaitDelay` -> Set to `30`.

## Application Level

*   **Avoid Blocking**: Ensure the `WebSocketListener` implementations do not perform heavy blocking operations. If they do, they are already offloaded to Virtual Threads, but excessive blocking will still consume resources.
*   **Logging**: Reduce logging levels. `System.out.println` is synchronized and will become a major bottleneck at scale.
