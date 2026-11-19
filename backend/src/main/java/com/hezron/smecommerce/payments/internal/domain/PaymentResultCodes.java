package com.hezron.smecommerce.payments.internal.domain;

/**
 * What a provider's failure code means for the customer (Guide 5.6). The codes are M-Pesa's; the fake
 * provider uses the same ones.
 */
public final class PaymentResultCodes {

    /**
     * Our code for any failed card payment (declined, wrong CVC, expired, not enough money). Stripe's own
     * message is the reason. Always retryable: the customer can try again, or use another card.
     */
    // Short: result_code is varchar(10).
    public static final String CARD_FAILED = "CARD_FAIL";

    /**
     * Our code when a start got no answer at all and the provider never gave us a reference, so we can't
     * even ask about it. After a while it's given up as failed, and the customer can simply try again.
     */
    public static final String NO_REPLY = "NO_REPLY";

    private PaymentResultCodes() {
    }

    /** Words for the customer. Falls back to the provider's own description for codes we don't know. */
    public static String friendlyReason(String resultCode, String providerDescription) {
        if ("1032".equals(resultCode)) {
            return "You cancelled the payment on your phone.";
        }
        if ("2001".equals(resultCode)) {
            return "The M-Pesa PIN was wrong.";
        }
        if ("1".equals(resultCode)) {
            return "Your M-Pesa balance was too low.";
        }
        if ("1037".equals(resultCode)) {
            return "We couldn't reach your phone.";
        }
        if (providerDescription != null && !providerDescription.isBlank()) {
            return providerDescription;
        }
        if (CARD_FAILED.equals(resultCode)) {
            return "Your card was declined.";
        }
        if (NO_REPLY.equals(resultCode)) {
            return "We couldn't confirm the payment. Please try again.";
        }
        return "The payment was declined.";
    }

    /**
     * True for failures where trying again makes sense: the customer cancelled, typed the wrong PIN,
     * had too little money, or didn't see the prompt in time. Anything else is a hard "no".
     */
    public static boolean isRetryable(String resultCode) {
        if ("1032".equals(resultCode)) {
            return true;
        }
        if ("2001".equals(resultCode)) {
            return true;
        }
        if ("1".equals(resultCode)) {
            return true;
        }
        if ("1037".equals(resultCode)) {
            return true;
        }
        if (CARD_FAILED.equals(resultCode)) {
            return true;
        }
        if (NO_REPLY.equals(resultCode)) {
            return true;
        }
        return false;
    }

}
