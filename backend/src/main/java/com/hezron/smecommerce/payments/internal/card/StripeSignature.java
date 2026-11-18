package com.hezron.smecommerce.payments.internal.card;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Checks that a webhook really came from Stripe, before anything in it is believed (Guide 5.8).
 * <p>
 * Stripe sends a header like {@code Stripe-Signature: t=1700000000,v1=5257a8...}. v1 is
 * HMAC-SHA256(webhook secret, t + "." + the exact body bytes), in hex. We compute the same and compare.
 * Two more rules:
 * <ul>
 *   <li>the comparison takes the same time however many characters match (MessageDigest.isEqual), so an
 *       attacker can't guess the signature one character at a time by timing us;</li>
 *   <li>t must be within 5 minutes of now, so a recorded webhook can't be replayed later.</li>
 * </ul>
 * The body must be the raw bytes as received: parsing and re-writing JSON would change spaces and break
 * the signature.
 */
public final class StripeSignature {

    static final Duration TOLERANCE = Duration.ofMinutes(5);

    private StripeSignature() {
    }

    /** True only for a correctly signed, recent body. */
    public static boolean isValid(byte[] rawBody, String signatureHeader, String webhookSecret, Instant now) {
        if (rawBody == null || signatureHeader == null || webhookSecret == null || webhookSecret.isBlank()) {
            return false;
        }

        String timestamp = null;
        String signature = null;
        for (String part : signatureHeader.split(",")) {
            String item = part.strip();
            if (item.startsWith("t=")) {
                timestamp = item.substring(2);
            } else if (item.startsWith("v1=") && signature == null) {
                signature = item.substring(3);
            }
        }
        if (timestamp == null || signature == null) {
            return false;
        }

        long seconds;
        try {
            seconds = Long.parseLong(timestamp);
        } catch (NumberFormatException notANumber) {
            return false;
        }
        Duration age = Duration.between(Instant.ofEpochSecond(seconds), now).abs();
        if (age.compareTo(TOLERANCE) > 0) {
            return false;
        }

        String expected = sign(timestamp, rawBody, webhookSecret);
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                signature.getBytes(StandardCharsets.US_ASCII));
    }

    /** HMAC-SHA256(secret, timestamp + "." + body) in lowercase hex. Also used by tests to sign a body. */
    public static String sign(String timestamp, byte[] rawBody, String webhookSecret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update((timestamp + ".").getBytes(StandardCharsets.UTF_8));
            mac.update(rawBody);
            return HexFormat.of().formatHex(mac.doFinal());
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            // HmacSHA256 is in every Java runtime; this can't happen.
            throw new IllegalStateException(e);
        }
    }

}
