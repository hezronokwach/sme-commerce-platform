package com.hezron.smecommerce.payments.internal.domain;

/**
 * What a provider's failure code means for the customer (Guide 5.6). The codes are M-Pesa's; the fake
 * provider uses the same ones.
 */
public final class PaymentResultCodes {

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
        return false;
    }

}
