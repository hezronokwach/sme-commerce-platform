package com.hezron.smecommerce.ordering.internal.domain;

/** How the customer chose to pay. */
public enum PaymentMethod {

    MPESA,

    CARD,

    /** The fake provider used in development and tests (issue "Payments with a fake provider first"). */
    FAKE

}
