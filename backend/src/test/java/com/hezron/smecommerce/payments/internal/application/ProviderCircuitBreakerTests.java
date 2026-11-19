package com.hezron.smecommerce.payments.internal.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/** The breaker on its own, with a clock the test moves forward by hand. No Spring, no database. */
class ProviderCircuitBreakerTests {

    private final MovableClock clock = new MovableClock();
    private final ProviderCircuitBreaker breaker = new ProviderCircuitBreaker("FAKE", 5, Duration.ofSeconds(30), clock);

    @Test
    void fiveFailuresInARowOpenIt() {
        failTimes(4);
        assertThat(breaker.allowCall()).isTrue();

        failTimes(1);

        assertThat(breaker.state()).isEqualTo(ProviderCircuitBreaker.State.OPEN);
        assertThat(breaker.allowCall()).isFalse();
    }

    @Test
    void aSuccessStartsTheCountAgain() {
        failTimes(4);
        breaker.recordSuccess();
        failTimes(4);

        assertThat(breaker.state()).isEqualTo(ProviderCircuitBreaker.State.CLOSED);
    }

    @Test
    void afterTheWaitExactlyOneTrialCallGoesThrough() {
        failTimes(5);

        clock.move(Duration.ofSeconds(29));
        assertThat(breaker.allowCall()).isFalse();

        clock.move(Duration.ofSeconds(1));
        assertThat(breaker.allowCall()).isTrue();
        // Everyone else keeps waiting while the trial call is out.
        assertThat(breaker.allowCall()).isFalse();
    }

    @Test
    void aTrialCallThatWorksClosesIt() {
        failTimes(5);
        clock.move(Duration.ofSeconds(30));
        breaker.allowCall();

        breaker.recordSuccess();

        assertThat(breaker.state()).isEqualTo(ProviderCircuitBreaker.State.CLOSED);
        assertThat(breaker.allowCall()).isTrue();
    }

    @Test
    void aTrialCallThatFailsOpensItForAnotherWait() {
        failTimes(5);
        clock.move(Duration.ofSeconds(30));
        breaker.allowCall();

        breaker.recordFailure();

        assertThat(breaker.state()).isEqualTo(ProviderCircuitBreaker.State.OPEN);
        clock.move(Duration.ofSeconds(29));
        assertThat(breaker.allowCall()).isFalse();
        clock.move(Duration.ofSeconds(1));
        assertThat(breaker.allowCall()).isTrue();
    }

    private void failTimes(int times) {
        for (int i = 0; i < times; i++) {
            breaker.recordFailure();
        }
    }

    /** A clock that only moves when told to. */
    static final class MovableClock extends Clock {

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
