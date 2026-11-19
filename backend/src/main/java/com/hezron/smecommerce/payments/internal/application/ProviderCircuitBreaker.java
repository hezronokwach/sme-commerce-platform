package com.hezron.smecommerce.payments.internal.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A circuit breaker for one provider (ADR 0014): when a provider is down, stop calling it for a while
 * instead of making every checkout wait for a timeout.
 * <ul>
 *   <li><b>CLOSED</b> (normal): every call goes through. After {@code failuresToOpen} failed calls in a
 *       row it opens.</li>
 *   <li><b>OPEN</b>: nobody calls the provider for {@code openFor}.</li>
 *   <li><b>TRYING</b> (after that wait): exactly one trial call is let through. If it works the breaker
 *       closes; if not it opens again for another {@code openFor}.</li>
 * </ul>
 * Every caller first asks {@link #allowCall()}, then reports how the call went. The methods are
 * synchronized: payments run on several threads at once, and they all share one breaker per provider.
 */
class ProviderCircuitBreaker {

    private static final Logger log = LoggerFactory.getLogger(ProviderCircuitBreaker.class);

    enum State {
        CLOSED, OPEN, TRYING
    }

    private final String providerName;
    private final int failuresToOpen;
    private final Duration openFor;
    private final Clock clock;

    private State state = State.CLOSED;
    private int failuresInARow;
    private Instant openedAt;

    ProviderCircuitBreaker(String providerName, int failuresToOpen, Duration openFor, Clock clock) {
        this.providerName = providerName;
        this.failuresToOpen = failuresToOpen;
        this.openFor = openFor;
        this.clock = clock;
    }

    /** May the provider be called now? A true while OPEN means: you are the one trial call. */
    synchronized boolean allowCall() {
        if (state == State.CLOSED) {
            return true;
        }
        if (state == State.TRYING) {
            return false; // the trial call is still out; everyone else waits for its result
        }
        // OPEN
        Instant tryAgainAt = openedAt.plus(openFor);
        if (clock.instant().isBefore(tryAgainAt)) {
            return false;
        }
        state = State.TRYING;
        log.info("Payment provider {}: letting one trial call through", providerName);
        return true;
    }

    /** The provider answered (whatever the answer was). */
    synchronized void recordSuccess() {
        if (state != State.CLOSED) {
            log.info("Payment provider {} answers again; calls resume", providerName);
        }
        state = State.CLOSED;
        failuresInARow = 0;
    }

    /** The provider didn't answer (timeout, connection refused, server error). */
    synchronized void recordFailure() {
        if (state == State.TRYING) {
            open();
            return;
        }
        failuresInARow = failuresInARow + 1;
        if (state == State.CLOSED && failuresInARow >= failuresToOpen) {
            open();
        }
    }

    synchronized State state() {
        return state;
    }

    private void open() {
        state = State.OPEN;
        openedAt = clock.instant();
        log.warn("Payment provider {} isn't answering; no calls to it for {}", providerName, openFor);
    }

}
