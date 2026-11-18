package com.hezron.smecommerce.payments.internal.card;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Card payments through Stripe (ADR 0013). One platform Stripe account for now, so one set of keys,
 * read from the environment (backend/.env locally). All empty by default: then card payments are
 * simply "not set up" and every attempt is refused politely, nothing crashes.
 * <p>
 * The keys are secrets: never logged, never returned by an API. Only the publishable key may go to a
 * browser (that is what it is for).
 *
 * @param secretKey      sk_test_... : lets our server create and read payment intents
 * @param publishableKey pk_test_... : lets the customer's browser show Stripe's card form
 * @param webhookSecret  whsec_... : proves a webhook really came from Stripe
 * @param baseUrl        https://api.stripe.com; tests point it at a pretend Stripe
 * @param timeout        how long to wait for Stripe before giving up (the payment is then UNKNOWN)
 */
@ConfigurationProperties("app.payments.card")
public record CardProperties(String secretKey, String publishableKey, String webhookSecret, String baseUrl,
        Duration timeout) {

    public CardProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://api.stripe.com";
        }
        if (timeout == null) {
            timeout = Duration.ofSeconds(10);
        }
    }

    /** True once the server can talk to Stripe. */
    public boolean isConfigured() {
        return secretKey != null && !secretKey.isBlank();
    }

    /** Never print the keys, even by accident (a record's default toString would). */
    @Override
    public String toString() {
        return "CardProperties[baseUrl=" + baseUrl + ", configured=" + isConfigured() + "]";
    }

}
