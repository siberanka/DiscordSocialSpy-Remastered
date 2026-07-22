package net.siberanka.discordsocialspy.util;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerRateLimiter {

    private final ConcurrentHashMap<UUID, Bucket> buckets = new ConcurrentHashMap<>();
    private volatile int capacity;
    private volatile double refillPerSecond;

    public PlayerRateLimiter(int capacity, double refillPerSecond) {
        reconfigure(capacity, refillPerSecond);
    }

    public boolean tryAcquire(UUID playerId) {
        return buckets.computeIfAbsent(playerId, ignored -> new Bucket(capacity)).tryAcquire(capacity, refillPerSecond);
    }

    public void remove(UUID playerId) {
        buckets.remove(playerId);
    }

    public void clear() {
        buckets.clear();
    }

    public void reconfigure(int newCapacity, double newRefillPerSecond) {
        capacity = newCapacity;
        refillPerSecond = newRefillPerSecond;
        buckets.clear();
    }

    private static final class Bucket {
        private double tokens;
        private long lastRefill;

        private Bucket(int capacity) {
            tokens = capacity;
            lastRefill = System.nanoTime();
        }

        private synchronized boolean tryAcquire(int capacity, double refillPerSecond) {
            long now = System.nanoTime();
            double elapsedSeconds = (now - lastRefill) / 1_000_000_000.0;
            tokens = Math.min(capacity, tokens + elapsedSeconds * refillPerSecond);
            lastRefill = now;
            if (tokens < 1.0) {
                return false;
            }
            tokens -= 1.0;
            return true;
        }
    }
}
