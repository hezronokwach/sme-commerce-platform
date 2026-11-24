package com.hezron.smecommerce.payments;

/**
 * Who takes the money. Public, because ordering says which one the customer chose when it asks for a
 * payment. Ordering has its own PaymentMethod enum (internal to ordering) with the same names; it
 * converts by name, so neither module uses the other's internal types.
 */
public enum PaymentProviderName {

    /** Safaricom M-Pesa STK push through Daraja (ADR 0019). */
    MPESA,

    /** Card payments through Stripe (ADR 0013). */
    CARD,

    /** Pretend provider for development and tests: no real money, outcome picked by the phone number. */
    FAKE
}
