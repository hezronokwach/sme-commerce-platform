package com.hezron.smecommerce.payments.internal.domain;

/** What we did with one incoming callback (stored in payments.callback_log). */
public enum CallbackOutcome {

    /** It changed the payment. */
    APPLIED,

    /** The payment was already final: a repeat, ignored. */
    DUPLICATE,

    /** No payment has this reference: a stranger, or a mistake. Ignored. */
    UNKNOWN_REFERENCE,

    /** "Success", but for a different amount than we asked for. Never marked paid; staff must look. */
    AMOUNT_MISMATCH,

    /** The body couldn't be read. */
    INVALID
}
