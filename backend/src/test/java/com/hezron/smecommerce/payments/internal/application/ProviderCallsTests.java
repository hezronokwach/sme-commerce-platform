package com.hezron.smecommerce.payments.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hezron.smecommerce.payments.PaymentProviderName;
import com.hezron.smecommerce.payments.internal.provider.CallbackResult;
import com.hezron.smecommerce.payments.internal.provider.PaymentProvider;
import com.hezron.smecommerce.payments.internal.provider.PaymentRequest;
import com.hezron.smecommerce.payments.internal.provider.StartResult;
import com.hezron.smecommerce.payments.internal.provider.StatusResult;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Which calls are repeated (only the safe ones) and how the breaker is fed. No Spring, no database. */
class ProviderCallsTests {

    private final ProviderCircuitBreakerTests.MovableClock clock = new ProviderCircuitBreakerTests.MovableClock();
    // Retry waits of 0 so the test doesn't sleep; 2 failures open the breaker.
    private final ProviderCalls calls = new ProviderCalls(
            new ProviderCallProperties(2, Duration.ofSeconds(30), List.of(Duration.ZERO, Duration.ZERO)), clock);
    private final FlakyProvider provider = new FlakyProvider();
    private final PaymentRequest request = new PaymentRequest(UUID.randomUUID(), 5000, "KES", "254712345670", "MM-000001");

    @Test
    void aStatusQueryIsTriedUpToThreeTimes() {
        provider.queryFailuresLeft = 2;

        StatusResult result = calls.queryStatus(provider, "ref-1");

        assertThat(result.outcome()).isEqualTo(StatusResult.Outcome.SUCCEEDED);
        assertThat(provider.queryCalls).isEqualTo(3);
        assertThat(calls.breakerState(PaymentProviderName.FAKE)).isEqualTo(ProviderCircuitBreaker.State.CLOSED);
    }

    @Test
    void threeFailedTriesCountAsOneFailedCall() {
        provider.queryFailuresLeft = 3;

        assertThatThrownBy(() -> calls.queryStatus(provider, "ref-1")).isInstanceOf(IllegalStateException.class);

        assertThat(provider.queryCalls).isEqualTo(3);
        assertThat(calls.breakerState(PaymentProviderName.FAKE)).isEqualTo(ProviderCircuitBreaker.State.CLOSED);
    }

    @Test
    void aStartIsNeverRepeated() {
        provider.startFails = true;

        assertThatThrownBy(() -> calls.start(provider, request)).isInstanceOf(IllegalStateException.class);

        // It may have reached the provider (a PIN prompt on the phone): the caller records UNKNOWN instead.
        assertThat(provider.startCalls).isEqualTo(1);
    }

    @Test
    void whileTheBreakerIsOpenAStartIsRefusedHonestlyWithoutCallingTheProvider() {
        provider.startFails = true;
        tryToStart();
        tryToStart();
        assertThat(calls.breakerState(PaymentProviderName.FAKE)).isEqualTo(ProviderCircuitBreaker.State.OPEN);

        StartResult result = calls.start(provider, request);

        assertThat(result.outcome()).isEqualTo(StartResult.Outcome.REJECTED);
        assertThat(result.reason()).isEqualTo(ProviderCalls.PROVIDER_TROUBLE);
        assertThat(provider.startCalls).isEqualTo(2);
    }

    @Test
    void whileTheBreakerIsOpenAStatusQueryIsSkipped() {
        provider.startFails = true;
        tryToStart();
        tryToStart();

        assertThatThrownBy(() -> calls.queryStatus(provider, "ref-1")).isInstanceOf(ProviderUnavailableException.class);
        assertThat(provider.queryCalls).isEqualTo(0);
    }

    @Test
    void anAnswerOfNoStillMeansTheProviderIsUp() {
        provider.startRefuses = true;
        tryToStart();
        tryToStart();

        assertThat(calls.breakerState(PaymentProviderName.FAKE)).isEqualTo(ProviderCircuitBreaker.State.CLOSED);
    }

    private void tryToStart() {
        try {
            calls.start(provider, request);
        } catch (IllegalStateException expected) {
            // the provider didn't answer
        }
    }

    /** A provider that fails as often as the test says. */
    private static final class FlakyProvider implements PaymentProvider {

        int queryFailuresLeft;
        int queryCalls;
        boolean startFails;
        boolean startRefuses;
        int startCalls;

        @Override
        public PaymentProviderName name() {
            return PaymentProviderName.FAKE;
        }

        @Override
        public StartResult start(PaymentRequest request) {
            startCalls = startCalls + 1;
            if (startFails) {
                throw new IllegalStateException("timed out");
            }
            if (startRefuses) {
                return StartResult.rejected("Bad phone number");
            }
            return StartResult.accepted("ref-" + startCalls);
        }

        @Override
        public StatusResult queryStatus(String providerReference) {
            queryCalls = queryCalls + 1;
            if (queryFailuresLeft > 0) {
                queryFailuresLeft = queryFailuresLeft - 1;
                throw new IllegalStateException("timed out");
            }
            return new StatusResult(StatusResult.Outcome.SUCCEEDED, "R1", "0", null);
        }

        @Override
        public Optional<CallbackResult> parseCallback(String rawBody) {
            return Optional.empty();
        }
    }

}
