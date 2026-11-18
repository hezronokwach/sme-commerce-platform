package com.hezron.smecommerce.payments;

import static org.assertj.core.api.Assertions.assertThat;

import com.hezron.smecommerce.payments.internal.card.StripeSignature;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** The signature check on its own: no Spring, no database. */
class StripeSignatureTests {

    private static final String SECRET = "whsec_test_only";
    private static final byte[] BODY = "{\"type\":\"payment_intent.succeeded\"}".getBytes(StandardCharsets.UTF_8);
    private static final Instant NOW = Instant.ofEpochSecond(1_800_000_000L);

    private static String header(long timestamp, String secret, byte[] body) {
        return "t=" + timestamp + ",v1=" + StripeSignature.sign(String.valueOf(timestamp), body, secret);
    }

    @Test
    void acceptsABodySignedWithOurSecretJustNow() {
        assertThat(StripeSignature.isValid(BODY, header(NOW.getEpochSecond(), SECRET, BODY), SECRET, NOW)).isTrue();
    }

    @Test
    void acceptsStripesExtraSignatureFields() {
        // Stripe may add a v0 (old scheme) next to v1; only v1 is checked.
        String withExtras = header(NOW.getEpochSecond(), SECRET, BODY) + ",v0=deadbeef";
        assertThat(StripeSignature.isValid(BODY, withExtras, SECRET, NOW)).isTrue();
    }

    @Test
    void refusesAnotherSecretAChangedBodyAndAnOldTimestamp() {
        assertThat(StripeSignature.isValid(BODY, header(NOW.getEpochSecond(), "whsec_other", BODY), SECRET, NOW))
                .isFalse();
        byte[] changed = "{\"type\":\"payment_intent.succeeded\" }".getBytes(StandardCharsets.UTF_8);
        assertThat(StripeSignature.isValid(changed, header(NOW.getEpochSecond(), SECRET, BODY), SECRET, NOW))
                .isFalse();
        long sixMinutesAgo = NOW.getEpochSecond() - 360;
        assertThat(StripeSignature.isValid(BODY, header(sixMinutesAgo, SECRET, BODY), SECRET, NOW)).isFalse();
    }

    @Test
    void refusesMissingOrBrokenHeadersAndAMissingSecret() {
        assertThat(StripeSignature.isValid(BODY, null, SECRET, NOW)).isFalse();
        assertThat(StripeSignature.isValid(BODY, "v1=abc", SECRET, NOW)).isFalse();
        assertThat(StripeSignature.isValid(BODY, "t=notanumber,v1=abc", SECRET, NOW)).isFalse();
        // No webhook secret configured: nothing can be trusted.
        assertThat(StripeSignature.isValid(BODY, header(NOW.getEpochSecond(), SECRET, BODY), "", NOW)).isFalse();
    }

}
