package com.hezron.smecommerce.payments.internal.application;

import com.hezron.smecommerce.payments.PaymentProviderName;
import com.hezron.smecommerce.payments.internal.provider.PaymentProvider;
import com.hezron.smecommerce.payments.internal.provider.PaymentRequest;
import com.hezron.smecommerce.payments.internal.provider.StartResult;
import com.hezron.smecommerce.payments.internal.provider.StatusResult;
import java.time.Clock;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Every call to a provider goes through here (ADR 0014), for two protections:
 * <ul>
 *   <li><b>The circuit breaker</b>, one per provider: while a provider is down, nobody waits for it.</li>
 *   <li><b>Limited retries, only where repeating is safe.</b> A status query is a read, so it is tried
 *       up to twice more. A start is never repeated here: it may have reached the provider (a PIN
 *       prompt on the phone), so a start with no answer becomes UNKNOWN and reconciliation finds out.</li>
 * </ul>
 * Timeouts belong to each provider's HTTP client (card: app.payments.card.timeout).
 */
@Component
@EnableConfigurationProperties(ProviderCallProperties.class)
class ProviderCalls {

    /** What the customer reads when the breaker is open. The payment is FAILED, retryable. */
    static final String PROVIDER_TROUBLE = "Payments are having trouble right now. Please try again in a minute.";

    private final ProviderCallProperties settings;
    private final Clock clock;
    private final Map<PaymentProviderName, ProviderCircuitBreaker> breakers = new EnumMap<>(PaymentProviderName.class);

    @Autowired
    ProviderCalls(ProviderCallProperties settings) {
        this(settings, Clock.systemUTC());
    }

    /** For tests: a clock they can move forward. */
    ProviderCalls(ProviderCallProperties settings, Clock clock) {
        this.settings = settings;
        this.clock = clock;
    }

    /**
     * Starts a payment, at most once.
     *
     * @return REJECTED (retryable) without calling, if the provider's breaker is open: we know nothing
     *         was sent, so this is an honest "no", not UNKNOWN
     * @throws RuntimeException if the provider didn't answer; the caller records UNKNOWN
     */
    StartResult start(PaymentProvider provider, PaymentRequest request) {
        ProviderCircuitBreaker breaker = breakerFor(provider.name());
        if (!breaker.allowCall()) {
            return StartResult.rejected(PROVIDER_TROUBLE);
        }
        try {
            StartResult result = provider.start(request);
            breaker.recordSuccess();
            return result;
        } catch (RuntimeException noAnswer) {
            breaker.recordFailure();
            throw noAnswer;
        }
    }

    /**
     * Asks how a payment ended, trying again a little later if the provider doesn't answer.
     *
     * @throws ProviderUnavailableException if the provider's breaker is open (it wasn't called)
     * @throws RuntimeException             if every try failed
     */
    StatusResult queryStatus(PaymentProvider provider, String providerReference) {
        ProviderCircuitBreaker breaker = breakerFor(provider.name());
        if (!breaker.allowCall()) {
            throw new ProviderUnavailableException(provider.name().name());
        }

        RuntimeException lastProblem = null;
        int tries = settings.queryRetryWaits().size() + 1;
        for (int attempt = 0; attempt < tries; attempt++) {
            if (attempt > 0) {
                pause(settings.queryRetryWaits().get(attempt - 1));
            }
            try {
                StatusResult result = provider.queryStatus(providerReference);
                breaker.recordSuccess();
                return result;
            } catch (RuntimeException e) {
                lastProblem = e;
            }
        }
        // All tries together count as one failed call.
        breaker.recordFailure();
        throw lastProblem;
    }

    /** The breaker's state, for tests and logs. */
    ProviderCircuitBreaker.State breakerState(PaymentProviderName provider) {
        return breakerFor(provider).state();
    }

    private synchronized ProviderCircuitBreaker breakerFor(PaymentProviderName provider) {
        ProviderCircuitBreaker breaker = breakers.get(provider);
        if (breaker == null) {
            breaker = new ProviderCircuitBreaker(provider.name(), settings.failuresToOpen(), settings.openFor(), clock);
            breakers.put(provider, breaker);
        }
        return breaker;
    }

    private static void pause(Duration wait) {
        try {
            Thread.sleep(wait);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting to ask the provider again", e);
        }
    }

}
