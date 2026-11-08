package com.hezron.smecommerce.payments.internal.provider;

/**
 * The provider's answer to "how did this payment end?".
 *
 * @param receiptNumber only for SUCCEEDED
 * @param resultCode    the provider's own code, e.g. "0" or "1032"
 */
public record StatusResult(Outcome outcome, String receiptNumber, String resultCode) {

    public enum Outcome {
        SUCCEEDED,
        FAILED,
        /** Not finished yet: ask again later. */
        PENDING
    }

}
