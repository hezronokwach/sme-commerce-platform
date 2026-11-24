package com.hezron.smecommerce.assistant.internal.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/** The per-minute limit on its own, with a clock the test moves by hand. */
class QuestionRateLimiterTests {

    private final MovableClock clock = new MovableClock();
    private final QuestionRateLimiter limiter = new QuestionRateLimiter(2, clock);

    @Test
    void aCallerIsStoppedAtTheLimit() {
        assertThat(limiter.tryAcquire("a")).isTrue();
        assertThat(limiter.tryAcquire("a")).isTrue();
        assertThat(limiter.tryAcquire("a")).isFalse();
    }

    @Test
    void oneCallerDoesNotUseUpAnothersLimit() {
        limiter.tryAcquire("a");
        limiter.tryAcquire("a");

        assertThat(limiter.tryAcquire("b")).isTrue();
    }

    @Test
    void aNewMinuteStartsAgain() {
        limiter.tryAcquire("a");
        limiter.tryAcquire("a");

        clock.move(Duration.ofSeconds(59));
        assertThat(limiter.tryAcquire("a")).isFalse();

        clock.move(Duration.ofSeconds(1));
        assertThat(limiter.tryAcquire("a")).isTrue();
    }

    private static final class MovableClock extends Clock {

        private Instant now = Instant.parse("2026-01-01T08:00:00Z");

        void move(Duration by) {
            now = now.plus(by);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

}
