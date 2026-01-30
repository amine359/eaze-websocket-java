package com.eaze.websocket.experiments;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

public class MaxVirtualThreadsDetection {

    private final AtomicLong successfulThreads = new AtomicLong(0);
    private final AtomicLong failedThreads = new AtomicLong(0);

    /**
     * Tests how many virtual threads can be created and run concurrently.
     * Threads perform a simple sleep to simulate work.
     */
    public void testMaxVirtualThreads(int targetThreadCount, long sleepMillis) {
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(targetThreadCount);

        System.out.println("Attempting to create " + targetThreadCount + " virtual threads...");

        for (int i = 0; i < targetThreadCount; i++) {
            try {
                Thread.startVirtualThread(() -> {
                    try {
                        startLatch.await();
                        Thread.sleep(sleepMillis);
                        successfulThreads.incrementAndGet();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        completionLatch.countDown();
                    }
                });
            } catch (OutOfMemoryError e) {
                failedThreads.incrementAndGet();
                completionLatch.countDown();
            }
        }

        startLatch.countDown(); // Release all threads
        try {
            completionLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        printResults();
    }

    /**
     * Incrementally tests to find the approximate maximum virtual thread count.
     */
    public long findMaxVirtualThreads(int batchSize, long sleepMillis) {
        System.out.println("Finding max virtual threads (batch size: " + batchSize + ")...");
        long totalCreated = 0;

        while (true) {
            try {
                CountDownLatch latch = new CountDownLatch(batchSize);
                for (int i = 0; i < batchSize; i++) {
                    Thread.startVirtualThread(() -> {
                        try {
                            Thread.sleep(sleepMillis);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } finally {
                            latch.countDown();
                        }
                    });
                    totalCreated++;
                }
                latch.await();
                System.out.println("Created " + totalCreated + " threads so far...");
            } catch (OutOfMemoryError e) {
                System.out.println("Hit memory limit at ~" + totalCreated + " threads");
                break;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        return totalCreated;
    }

    private void printResults() {
        System.out.println("=== Results ===");
        System.out.println("Successful threads: " + successfulThreads.get());
        System.out.println("Failed threads: " + failedThreads.get());
    }

    public static void main(String[] args) {
        MaxVirtualThreadsDetection detector = new MaxVirtualThreadsDetection();

        // Test 1: Try creating a specific number of threads
        detector.testMaxVirtualThreads(100_000_0_0, 100);

        // Test 2: Find approximate maximum
        // long max = detector.findMaxVirtualThreads(10_000, 50);
        // System.out.println("Approximate max: " + max);
    }
}
