package com.example.demo.hostel.config;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory token buckets limiting how often each user (or IP) can file a
 * complaint: at most CAPACITY per fixed one-minute window. Refilling all
 * tokens at once each minute (refillIntervally) enforces the limit exactly;
 * greedy refill would let up to 9 through within 60 s after a burst.
 * Per instance: fine for a single Render instance.
 */
public class ComplaintRateLimiter {

    static final int CAPACITY = 5;
    static final Duration WINDOW = Duration.ofMinutes(1);

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final TimeMeter timeMeter;

    public ComplaintRateLimiter() {
        this(TimeMeter.SYSTEM_MILLISECONDS);
    }

    // Tests pass a controllable clock
    ComplaintRateLimiter(TimeMeter timeMeter) {
        this.timeMeter = timeMeter;
    }

    /** Takes one token for this key; the probe says whether it was allowed and when to retry. */
    public ConsumptionProbe tryConsume(String key) {
        return buckets.computeIfAbsent(key, ignored -> newBucket()).tryConsumeAndReturnRemaining(1);
    }

    /**
     * Drops buckets that are full again (no request in the last window), so the
     * map only holds recently active users. Keeps memory flat on a 512 MB box.
     */
    @Scheduled(fixedDelay = 10, timeUnit = java.util.concurrent.TimeUnit.MINUTES)
    public void evictIdleBuckets() {
        buckets.entrySet().removeIf(entry -> entry.getValue().getAvailableTokens() >= CAPACITY);
    }

    int trackedKeys() {
        return buckets.size();
    }

    private Bucket newBucket() {
        return Bucket.builder()
                .addLimit(limit -> limit.capacity(CAPACITY).refillIntervally(CAPACITY, WINDOW))
                .withCustomTimePrecision(timeMeter)
                .build();
    }
}
