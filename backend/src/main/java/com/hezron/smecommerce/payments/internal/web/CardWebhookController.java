package com.hezron.smecommerce.payments.internal.web;

import com.hezron.smecommerce.payments.PaymentProviderName;
import com.hezron.smecommerce.payments.internal.application.PaymentCallbackService;
import com.hezron.smecommerce.payments.internal.card.CardProperties;
import com.hezron.smecommerce.payments.internal.card.StripeSignature;
import com.hezron.smecommerce.payments.internal.domain.CallbackOutcome;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Stripe's webhook for card payments (Guide 5.8). Public, like every webhook (WebhookSecurityConfig):
 * Stripe can't log in. What makes it safe is the signature, checked on the raw bytes BEFORE anything
 * in the body is believed. A body that isn't signed by our webhook secret gets 400 and changes nothing.
 * <p>
 * After that, the result goes through PaymentCallbackService like every other provider's: logged,
 * the payment row locked, duplicates ignored, the amount compared.
 */
@RestController
class CardWebhookController {

    private static final Logger log = LoggerFactory.getLogger(CardWebhookController.class);

    private final PaymentCallbackService callbacks;
    private final CardProperties properties;

    CardWebhookController(PaymentCallbackService callbacks, CardProperties properties) {
        this.callbacks = callbacks;
        this.properties = properties;
    }

    /**
     * {@code POST /api/webhooks/card}. The body is taken as bytes on purpose: the signature covers the
     * exact bytes Stripe sent, and turning them into objects first could change them.
     */
    @PostMapping("/api/webhooks/card")
    ResponseEntity<Map<String, Object>> receive(@RequestBody byte[] rawBody,
            @RequestHeader(name = "Stripe-Signature", required = false) String signature) {
        if (!StripeSignature.isValid(rawBody, signature, properties.webhookSecret(), Instant.now())) {
            // Not from Stripe (or replayed, or changed on the way). Nothing about it is logged as data.
            log.warn("Card webhook refused: missing or bad signature");
            return ResponseEntity.badRequest().body(Map.of("received", false));
        }

        CallbackOutcome outcome = callbacks.receive(PaymentProviderName.CARD, new String(rawBody, StandardCharsets.UTF_8));
        // 200 for everything we could read, even events we don't use: otherwise Stripe keeps resending them.
        return ResponseEntity.ok(Map.of("received", true, "outcome", outcome.name()));
    }

}
