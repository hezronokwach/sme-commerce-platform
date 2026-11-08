package com.hezron.smecommerce.payments.internal.provider;

import com.hezron.smecommerce.payments.PaymentProviderName;
import com.hezron.smecommerce.payments.internal.application.PaymentCallbackService;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * A pretend provider (Guide 5.9): no real money, and the outcome is picked by the phone's last digit
 * (see FakeOutcome).
 * <p>
 * It behaves like a real one: start() answers ACCEPTED with a reference straight away, and a moment
 * later the "provider" calls us back. A real provider would POST the callback to our webhook; the
 * fake hands the same JSON body to PaymentCallbackService, which is exactly what the webhook will do.
 * So the code that applies results is the same for fake and real payments.
 */
@Component
class FakePaymentProvider implements PaymentProvider {

    private static final Logger log = LoggerFactory.getLogger(FakePaymentProvider.class);

    private final PaymentCallbackService callbacks;
    private final JsonMapper jsonMapper;
    private final Duration callbackDelay;

    /**
     * One background thread that runs each "callback" after the delay. Not a Spring bean on purpose:
     * a scheduler bean would replace the thread pool Spring uses for event listeners.
     */
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    /**
     * {@code @Lazy}: PaymentCallbackService needs the providers (to read callbacks), and this provider
     * needs PaymentCallbackService (to deliver them). @Lazy hands us a stand-in that finds the real
     * service on first use, which breaks that circle.
     */
    FakePaymentProvider(@Lazy PaymentCallbackService callbacks, JsonMapper jsonMapper,
            @Value("${app.payments.fake.callback-delay:3s}") Duration callbackDelay) {
        this.callbacks = callbacks;
        this.jsonMapper = jsonMapper;
        this.callbackDelay = callbackDelay;
    }

    @Override
    public PaymentProviderName name() {
        return PaymentProviderName.FAKE;
    }

    @Override
    public StartResult start(PaymentRequest request) {
        FakeOutcome outcome = FakeOutcome.forPhone(request.payerPhone());
        // The outcome goes into the reference too, so queryStatus can answer without a database.
        String reference = "FAKE-" + outcome.ordinal() + "-" + UUID.randomUUID().toString().substring(0, 13);

        if (outcome == FakeOutcome.NEVER_ANSWERS) {
            log.info("Fake payment {} accepted; it will never call back", reference);
            return StartResult.accepted(reference);
        }

        String receipt = null;
        if (outcome == FakeOutcome.SUCCEEDS) {
            receipt = "FK" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        }
        FakeCallbackBody body = new FakeCallbackBody(reference, outcome.resultCode(), receipt,
                request.amountMinor(), outcome.description());
        String rawBody = jsonMapper.writeValueAsString(body);

        scheduler.schedule(new SendCallback(rawBody), callbackDelay.toMillis(), TimeUnit.MILLISECONDS);
        log.info("Fake payment {} accepted; outcome {} in {}", reference, outcome, callbackDelay);
        return StartResult.accepted(reference);
    }

    @Override
    public StatusResult queryStatus(String providerReference) {
        FakeOutcome outcome = outcomeFromReference(providerReference);
        if (outcome == FakeOutcome.SUCCEEDS) {
            return new StatusResult(StatusResult.Outcome.SUCCEEDED, "FK-QUERY", outcome.resultCode());
        }
        if (outcome == FakeOutcome.NEVER_ANSWERS) {
            return new StatusResult(StatusResult.Outcome.PENDING, null, null);
        }
        return new StatusResult(StatusResult.Outcome.FAILED, null, outcome.resultCode());
    }

    @Override
    public Optional<CallbackResult> parseCallback(String rawBody) {
        try {
            FakeCallbackBody body = jsonMapper.readValue(rawBody, FakeCallbackBody.class);
            if (body.reference() == null || body.resultCode() == null) {
                return Optional.empty();
            }
            return Optional.of(new CallbackResult(body.reference(), body.resultCode(), body.receiptNumber(),
                    body.amountMinor(), body.resultDescription()));
        } catch (JacksonException notJson) {
            return Optional.empty();
        }
    }

    /** "FAKE-0-..." -> SUCCEEDS (the number is the outcome's position in FakeOutcome). */
    static FakeOutcome outcomeFromReference(String providerReference) {
        String[] parts = providerReference.split("-");
        int position = Integer.parseInt(parts[1]);
        return FakeOutcome.values()[position];
    }

    @PreDestroy
    void stop() {
        scheduler.shutdownNow();
    }

    /** The JSON the fake "provider" sends us. */
    record FakeCallbackBody(String reference, String resultCode, String receiptNumber, Long amountMinor,
            String resultDescription) {
    }

    /** The job the scheduler runs after the delay: deliver one callback. */
    private class SendCallback implements Runnable {

        private final String rawBody;

        SendCallback(String rawBody) {
            this.rawBody = rawBody;
        }

        @Override
        public void run() {
            try {
                callbacks.receive(PaymentProviderName.FAKE, rawBody);
            } catch (RuntimeException e) {
                // A real provider would just see an error answer; the reconciliation job (later issue)
                // finds payments whose callback never got through.
                log.error("Fake callback could not be applied", e);
            }
        }
    }

}
