package com.edgarlens.sec;

/**
 * Blocking rate limiter that spaces calls evenly so a client never exceeds
 * {@code permitsPerSecond}. Thread-safe: concurrent callers each reserve the next
 * free slot and sleep until it arrives.
 */
public class RateLimiter {

    private final long intervalNanos;
    private long nextFreeSlotNanos;

    public RateLimiter(double permitsPerSecond) {
        if (permitsPerSecond <= 0) {
            throw new IllegalArgumentException("permitsPerSecond must be positive");
        }
        this.intervalNanos = (long) (1_000_000_000L / permitsPerSecond);
        this.nextFreeSlotNanos = System.nanoTime();
    }

    public void acquire() throws InterruptedException {
        long waitNanos;
        synchronized (this) {
            long now = System.nanoTime();
            long slot = Math.max(now, nextFreeSlotNanos);
            nextFreeSlotNanos = slot + intervalNanos;
            waitNanos = slot - now;
        }
        if (waitNanos > 0) {
            Thread.sleep(waitNanos / 1_000_000, (int) (waitNanos % 1_000_000));
        }
    }
}
