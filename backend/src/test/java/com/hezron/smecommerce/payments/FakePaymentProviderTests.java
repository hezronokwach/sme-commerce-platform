package com.hezron.smecommerce.payments;

import static org.assertj.core.api.Assertions.assertThat;

import com.hezron.smecommerce.payments.internal.domain.PaymentResultCodes;
import com.hezron.smecommerce.payments.internal.provider.FakeOutcome;
import org.junit.jupiter.api.Test;

/** The fake provider's digit rules and what each result code means. Plain unit tests, no Spring. */
class FakePaymentProviderTests {

    @Test
    void theLastDigitOfThePhonePicksTheOutcome() {
        assertThat(FakeOutcome.forPhone("254712345670")).isEqualTo(FakeOutcome.SUCCEEDS);
        assertThat(FakeOutcome.forPhone("254712345671")).isEqualTo(FakeOutcome.CANCELLED);
        assertThat(FakeOutcome.forPhone("254712345672")).isEqualTo(FakeOutcome.WRONG_PIN);
        assertThat(FakeOutcome.forPhone("254712345673")).isEqualTo(FakeOutcome.NEVER_ANSWERS);
        assertThat(FakeOutcome.forPhone("254712345674")).isEqualTo(FakeOutcome.DECLINED);
        assertThat(FakeOutcome.forPhone("254712345678")).isEqualTo(FakeOutcome.SUCCEEDS);
    }

    @Test
    void cancelledAndWrongPinCanBeRetriedButADeclineCannot() {
        assertThat(PaymentResultCodes.isRetryable(FakeOutcome.CANCELLED.resultCode())).isTrue();
        assertThat(PaymentResultCodes.isRetryable(FakeOutcome.WRONG_PIN.resultCode())).isTrue();
        assertThat(PaymentResultCodes.isRetryable(FakeOutcome.DECLINED.resultCode())).isFalse();
    }

    @Test
    void customersReadPlainWords() {
        assertThat(PaymentResultCodes.friendlyReason("1032", "Request cancelled by user"))
                .isEqualTo("You cancelled the payment on your phone.");
        // A code we don't know: the provider's own words.
        assertThat(PaymentResultCodes.friendlyReason("9999", "Declined by the fake provider"))
                .isEqualTo("Declined by the fake provider");
    }

}
