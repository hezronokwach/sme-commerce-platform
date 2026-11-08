package com.hezron.smecommerce.payments.internal.domain;

/** Where one payment attempt is (architecture section 7, payment state machine). */
public enum PaymentStatus {

    /** Saved together with the order; the provider hasn't been asked yet. */
    INITIATED,

    /** The provider accepted the request; we are waiting for the customer (PIN prompt) and the callback. */
    PENDING,

    /** The money arrived. Final. */
    SUCCEEDED,

    /** The provider said no (cancelled, wrong PIN, ...). Final for this attempt; the order may try again. */
    FAILED,

    /** No answer within the time limit (reconciliation issue). */
    TIMED_OUT,

    /**
     * We don't know: the request to the provider failed half-way, so it may or may not have started.
     * A real state: never tell the customer "failed" when the truth is "unknown".
     */
    UNKNOWN;

    /** SUCCEEDED and FAILED never change again; a second callback for them is a duplicate. */
    public boolean isFinal() {
        return this == SUCCEEDED || this == FAILED;
    }
}
