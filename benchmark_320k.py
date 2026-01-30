import asyncio
import socket
import sys
import time
import base64
import os

# resource is Unix-only (not available on Windows).
# We handle the ImportError so the script runs on Windows.
try:
    import resource
except ImportError:
    resource = None

# ==========================================
# Configuration
# ==========================================
TARGET_HOST = '127.0.0.1'
TARGET_PORT = 8081
TARGET_CONNECTIONS = 320000

# To achieve 320k connections, you need multiple source IPs because of the 64k ephemeral port limit per IP.
# Configure loopback aliases on Linux using 'ip' command:
# sudo ip addr add 127.0.0.2/8 dev lo
# sudo ip addr add 127.0.0.3/8 dev lo
# sudo ip addr add 127.0.0.4/8 dev lo
# sudo ip addr add 127.0.0.5/8 dev lo
# SOURCE_IPS = ['127.0.0.1']
CANDIDATE_IPS = ['127.0.0.1', '127.0.0.2', '127.0.0.3', '127.0.0.4', '127.0.0.5']
SOURCE_IPS = []

# Validate which IPs are available
for ip in CANDIDATE_IPS:
    try:
        # strict=True checks if the IP is assigned to an interface
        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        s.bind((ip, 0))
        s.close()
        SOURCE_IPS.append(ip)
    except OSError:
        pass

if not SOURCE_IPS:
    print("Error: No source IPs available to bind.")
    sys.exit(1)

print(f"Detected {len(SOURCE_IPS)} available source IPs: {SOURCE_IPS}")
if len(SOURCE_IPS) * 64000 < TARGET_CONNECTIONS:
    print(f"WARNING: Available source IPs may not support {TARGET_CONNECTIONS} connections due to ephemeral port limits.")
    print(f"         You can create aliases:")
    print(f"         sudo ip addr add 127.0.0.2/8 dev lo")
    print(f"         sudo ip addr add 127.0.0.3/8 dev lo")
    print(f"         sudo ip addr add 127.0.0.4/8 dev lo")
    print(f"         sudo ip addr add 127.0.0.5/8 dev lo")

BATCH_SIZE = 10000
DELAY_BETWEEN_BATCHES = 0.05
CONNECT_TIMEOUT = 10

# ==========================================

stats = {
    'connected': 0,
    'failed': 0,
    'closed': 0
}

async def connect_client(idx, semaphore):
    async with semaphore:
        source_ip = SOURCE_IPS[idx % len(SOURCE_IPS)]
        sock = None
        try:
            # Create socket manually to bind to source IP
            sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)

            # Bind to specific source IP to utilize more ports
            try:
                sock.bind((source_ip, 0))
            except OSError as e:
                # If we run out of ports on this IP, this will fail
                # print(f"Bind failed for {source_ip}: {e}")
                raise e

            sock.setblocking(False)

            await asyncio.wait_for(
                asyncio.get_running_loop().sock_connect(sock, (TARGET_HOST, TARGET_PORT)),
                timeout=CONNECT_TIMEOUT
            )

            # Simple HTTP Upgrade request
            key = base64.b64encode(os.urandom(16)).decode('utf-8')
            request = (
                f"GET / HTTP/1.1\r\n"
                f"Host: {TARGET_HOST}:{TARGET_PORT}\r\n"
                f"Upgrade: websocket\r\n"
                f"Connection: Upgrade\r\n"
                f"Sec-WebSocket-Key: {key}\r\n"
                f"Sec-WebSocket-Version: 13\r\n"
                f"\r\n"
            )

            await asyncio.get_running_loop().sock_sendall(sock, request.encode())

            # Read response
            try:
                response = await asyncio.wait_for(
                    asyncio.get_running_loop().sock_recv(sock, 4096),
                    timeout=CONNECT_TIMEOUT
                )
            except asyncio.TimeoutError:
                sock.close()
                stats['failed'] += 1
                return None

            if b"101 Switching Protocols" in response:
                stats['connected'] += 1
                return sock
            else:
                sock.close()
                stats['failed'] += 1
                return None

        except Exception as e:
            if sock:
                sock.close()
            stats['failed'] += 1
            return None

async def monitor():
    start_time = time.time()
    while True:
        await asyncio.sleep(1)
        elapsed = time.time() - start_time
        print(f"Time: {elapsed:.1f}s | Connected: {stats['connected']} | Failed: {stats['failed']}")
        if stats['connected'] + stats['failed'] >= TARGET_CONNECTIONS:
            # Continue monitoring if we want to watch for drops, but for now just stop loop
            # meaningful monitoring usually continues
            pass

async def main():
    # Attempt to increase limits (only if resource module is loaded)
    if resource:
        try:
            soft, hard = resource.getrlimit(resource.RLIMIT_NOFILE)
            resource.setrlimit(resource.RLIMIT_NOFILE, (hard, hard))
            print(f"File descriptor limit: {hard}")
            if hard < TARGET_CONNECTIONS:
                print(f"WARNING: File descriptor limit ({hard}) is lower than target connections ({TARGET_CONNECTIONS}).")
                print("         Please increase 'nofile' limit in /etc/security/limits.conf")
        except Exception as e:
            print(f"Could not set file descriptor limit: {e}")
    else:
        print("Windows detected (or resource module missing): Skipping ulimit checks.")

    # Semaphore to limit concurrent handshakes (not total connections)
    semaphore = asyncio.Semaphore(5000)

    tasks = []

    monitor_task = asyncio.create_task(monitor())

    print(f"Starting {TARGET_CONNECTIONS} connections to {TARGET_HOST}:{TARGET_PORT}...")

    # We will gather results in batches to avoid holding 100k tasks in memory if that's an issue,
    # but more importantly to process them as we go.
    # However, 'gather' waits for all.
    # Let's just launch them.

    active_sockets = []

    # To avoid memory issues with 100k pending tasks, we can use a bounded approach
    # if python overhead is high, but 100k tasks is feasible in recent python versions.

    for i in range(TARGET_CONNECTIONS):
        task = asyncio.create_task(connect_client(i, semaphore))
        tasks.append(task)

        # Rate limiting of task creation
        if len(tasks) % BATCH_SIZE == 0:
            await asyncio.sleep(DELAY_BETWEEN_BATCHES)

    print("All connection tasks initiated. Waiting for completion...")

    results = await asyncio.gather(*tasks)

    for sock in results:
        if sock:
            active_sockets.append(sock)

    print(f"Benchmark establishment phase finished.")
    print(f"Total established: {len(active_sockets)}")
    print("Holding connections open for 60 seconds...")

    # Keep them alive
    try:
        await asyncio.sleep(60)
    except KeyboardInterrupt:
        pass

    print("Closing connections...")
    for s in active_sockets:
        try:
            s.close()
        except:
            pass

if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        print("\nInterrupted.")
