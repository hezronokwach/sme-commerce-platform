package com.hezron.smecommerce.payments.internal.provider;

/**
 * How a FAKE payment ends, chosen by the last digit of the customer's phone number (Guide 5.9). This
 * lets you try every path from the storefront without real money:
 *
 * <pre>
 *   ...0  and 5-9   SUCCEEDS
 *   ...1            CANCELLED     (customer pressed cancel on the PIN prompt; can try again)
 *   ...2            WRONG_PIN     (can try again)
 *   ...3            NEVER_ANSWERS (no callback ever comes; the payment stays PENDING)
 *   ...4            DECLINED      (a failure that can't be retried: the order is cancelled)
 * </pre>
 *
 * The result codes are M-Pesa's, so the same code later handles both providers.
 */
public enum FakeOutcome {

    SUCCEEDS("0", "The service request is processed successfully."),
    CANCELLED("1032", "Request cancelled by user"),
    WRONG_PIN("2001", "The initiator information is invalid."),
    NEVER_ANSWERS(null, null),
    DECLINED("9999", "Declined by the fake provider");

    private final String resultCode;
    private final String description;

    FakeOutcome(String resultCode, String description) {
        this.resultCode = resultCode;
        this.description = description;
    }

    public String resultCode() {
        return resultCode;
    }

    public String description() {
        return description;
    }

    /** The outcome for a phone number in 2547XXXXXXXX form. */
    public static FakeOutcome forPhone(String phone) {
        char lastDigit = phone.charAt(phone.length() - 1);
        if (lastDigit == '1') {
            return CANCELLED;
        }
        if (lastDigit == '2') {
            return WRONG_PIN;
        }
        if (lastDigit == '3') {
            return NEVER_ANSWERS;
        }
        if (lastDigit == '4') {
            return DECLINED;
        }
        return SUCCEEDS;
    }

}
