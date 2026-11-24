package com.hezron.smecommerce.payments.internal.mpesa;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * M-Pesa through Safaricom's Daraja API (ADR 0019). One platform Daraja app for now, read from the
 * environment. Empty values mean M-Pesa is "not set up", and every attempt is refused politely.
 *
 * @param consumerKey     the Daraja app's key (a secret)
 * @param consumerSecret  the Daraja app's secret
 * @param shortcode       the paybill or till number; 174379 in the sandbox
 * @param passkey         Lipa na M-Pesa Online passkey (a secret)
 * @param transactionType CustomerPayBillOnline (paybill) or CustomerBuyGoodsOnline (till)
 * @param baseUrl         https://sandbox.safaricom.co.ke; tests point it at a pretend Daraja
 * @param callbackBaseUrl this backend's public address, which Safaricom calls back
 * @param callbackSecret  a hard-to-guess last part of the callback address
 * @param timeout         how long to wait for Daraja (the payment is then UNKNOWN)
 */
@ConfigurationProperties("app.payments.mpesa")
public record MpesaProperties(String consumerKey, String consumerSecret, String shortcode, String passkey,
        String transactionType, String baseUrl, String callbackBaseUrl, String callbackSecret, Duration timeout) {

    public MpesaProperties {
        if (transactionType == null || transactionType.isBlank()) {
            transactionType = "CustomerPayBillOnline";
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://sandbox.safaricom.co.ke";
        }
        if (timeout == null) {
            timeout = Duration.ofSeconds(10);
        }
    }

    /** True once every value M-Pesa needs is there. */
    public boolean isConfigured() {
        return hasText(consumerKey) && hasText(consumerSecret) && hasText(shortcode) && hasText(passkey)
                && hasText(callbackBaseUrl) && hasText(callbackSecret);
    }

    /** Where Safaricom sends the result of a prompt. */
    public String callbackUrl() {
        String base = callbackBaseUrl;
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/api/webhooks/mpesa/" + callbackSecret;
    }

    /** Compared in constant time, so the answer's timing gives nothing away about the secret. */
    public boolean isCallbackSecret(String candidate) {
        if (!hasText(callbackSecret) || candidate == null) {
            return false;
        }
        return MessageDigest.isEqual(callbackSecret.getBytes(StandardCharsets.UTF_8),
                candidate.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /** Never print the secrets, even by accident (a record's default toString would). */
    @Override
    public String toString() {
        return "MpesaProperties[baseUrl=" + baseUrl + ", shortcode=" + shortcode + ", configured=" + isConfigured() + "]";
    }

}
