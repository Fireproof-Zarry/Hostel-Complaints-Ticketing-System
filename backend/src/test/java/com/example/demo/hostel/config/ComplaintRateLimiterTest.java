package com.example.demo.hostel.config;

import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ComplaintRateLimiterTest {

    /** A clock the test moves by hand. */
    private static final class FakeClock implements TimeMeter {
        private long nanos;

        @Override
        public long currentTimeNanos() {
            return nanos;
        }

        @Override
        public boolean isWallClockBased() {
            return false;
        }

        void advance(Duration duration) {
            nanos += duration.toNanos();
        }
    }

    private final FakeClock clock = new FakeClock();
    private final ComplaintRateLimiter limiter = new ComplaintRateLimiter(clock);

    @Test
    void allowsFiveRequestsPerMinuteThenBlocks() {
        for (int i = 0; i < 5; i++) {
            assertThat(limiter.tryConsume("user:a").isConsumed()).as("request %d", i + 1).isTrue();
        }

        ConsumptionProbe sixth = limiter.tryConsume("user:a");
        assertThat(sixth.isConsumed()).isFalse();
        assertThat(Duration.ofNanos(sixth.getNanosToWaitForRefill())).isEqualTo(Duration.ofMinutes(1));
    }

    @Test
    void neverAllowsMoreThanFiveWithinAMinute() {
        for (int i = 0; i < 5; i++) {
            limiter.tryConsume("user:a");
        }
        // Unlike greedy refill, nothing trickles back in before the minute is up
        clock.advance(Duration.ofSeconds(59));
        assertThat(limiter.tryConsume("user:a").isConsumed()).isFalse();
    }

    @Test
    void refillsAfterTheMinute() {
        for (int i = 0; i < 5; i++) {
            limiter.tryConsume("user:a");
        }
        clock.advance(Duration.ofMinutes(1));

        for (int i = 0; i < 5; i++) {
            assertThat(limiter.tryConsume("user:a").isConsumed()).isTrue();
        }
        assertThat(limiter.tryConsume("user:a").isConsumed()).isFalse();
    }

    @Test
    void eachUserHasTheirOwnLimit() {
        for (int i = 0; i < 5; i++) {
            limiter.tryConsume("user:a");
        }

        assertThat(limiter.tryConsume("user:a").isConsumed()).isFalse();
        assertThat(limiter.tryConsume("user:b").isConsumed()).isTrue();
    }

    @Test
    void evictsOnlyIdleBuckets() {
        limiter.tryConsume("user:idle");
        clock.advance(Duration.ofMinutes(1));
        limiter.tryConsume("user:active");

        limiter.evictIdleBuckets();

        // The idle user's bucket refilled completely and was dropped; the active one is kept
        assertThat(limiter.trackedKeys()).isEqualTo(1);
        assertThat(limiter.tryConsume("user:active").getRemainingTokens()).isEqualTo(3);
    }
}
