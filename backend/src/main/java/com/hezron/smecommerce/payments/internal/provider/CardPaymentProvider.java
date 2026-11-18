package com.hezron.smecommerce.payments.internal.provider;

import com.hezron.smecommerce.payments.PaymentProviderName;
import com.hezron.smecommerce.payments.internal.card.StripeClient;
import com.hezron.smecommerce.payments.internal.card.StripeRefusedException;
import com.hezron.smecommerce.payments.internal.domain.PaymentResultCodes;
import java.util.Optional;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Card payments through Stripe test mode (ADR 0013). Behind the same PaymentProvider interface as the
 * fake one, so ordering doesn't know or care which it is.
 * <p>
 * start() creates a payment intent; the customer then confirms it with Stripe's card form in their
 * browser; Stripe tells us the result by a signed webhook (checked in CardWebhookController before it
 * reaches parseCallback).
 */
@Component
class CardPaymentProvider implements PaymentProvider {

    /** Our result codes for card payments (PaymentResultCodes turns them into words and "retryable"). */
    static final String SUCCEEDED = "0";
    static final String FAILED = PaymentResultCodes.CARD_FAILED;

    private final StripeClient stripe;
    private final JsonMapper jsonMapper;

    CardPaymentProvider(StripeClient stripe, JsonMapper jsonMapper) {
        this.stripe = stripe;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public PaymentProviderName name() {
        return PaymentProviderName.CARD;
    }

    @Override
    public StartResult start(PaymentRequest request) {
        if (!stripe.isConfigured()) {
            return StartResult.rejected("Card payments are not set up yet. Please choose another way to pay.");
        }
        try {
            String intentId = stripe.createIntent(request.paymentId(), request.amountMinor(), request.currency(),
                    request.orderNumber());
            return StartResult.accepted(intentId);
        } catch (StripeRefusedException refused) {
            return StartResult.rejected(refused.getMessage());
        }
        // Timeouts and connection errors fly on: the caller records UNKNOWN, never FAILED.
    }

    @Override
    public StatusResult queryStatus(String providerReference) {
        StripeClient.Intent intent = stripe.readIntent(providerReference);
        if ("succeeded".equals(intent.status())) {
            return new StatusResult(StatusResult.Outcome.SUCCEEDED, receiptOf(intent.latestCharge(), intent.id()),
                    SUCCEEDED);
        }
        if ("canceled".equals(intent.status())) {
            return new StatusResult(StatusResult.Outcome.FAILED, null, FAILED);
        }
        // requires_payment_method, requires_confirmation, processing...: not finished yet.
        return new StatusResult(StatusResult.Outcome.PENDING, null, null);
    }

    /**
     * Reads a Stripe event. Only the two events that end a payment count; anything else is "not a
     * callback for us" (Stripe can send many kinds of events).
     */
    @Override
    public Optional<CallbackResult> parseCallback(String rawBody) {
        JsonNode event;
        try {
            event = jsonMapper.readTree(rawBody);
        } catch (JacksonException notJson) {
            return Optional.empty();
        }
        String type = event.path("type").asString("");
        JsonNode intent = event.path("data").path("object");
        String intentId = intent.path("id").asString("");
        if (intentId.isBlank()) {
            return Optional.empty();
        }

        if ("payment_intent.succeeded".equals(type)) {
            long amount = intent.path("amount_received").asLong();
            String receipt = receiptOf(intent.path("latest_charge").asString(""), intentId);
            return Optional.of(new CallbackResult(intentId, SUCCEEDED, receipt, amount, "Card payment received"));
        }
        if ("payment_intent.payment_failed".equals(type)) {
            String message = intent.path("last_payment_error").path("message").asString("");
            return Optional.of(new CallbackResult(intentId, FAILED, null, null, message));
        }
        return Optional.empty();
    }

    /** The charge id (ch_...) is what Stripe's dashboard shows for a payment; the intent id if there's none. */
    private static String receiptOf(String latestCharge, String intentId) {
        if (latestCharge != null && !latestCharge.isBlank()) {
            return latestCharge;
        }
        return intentId;
    }

}
