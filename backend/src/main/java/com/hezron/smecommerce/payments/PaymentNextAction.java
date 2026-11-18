package com.hezron.smecommerce.payments;

/**
 * What the customer's browser must do to finish a payment, if anything. Provider-neutral on purpose:
 * ordering passes it to the order page without reading it, so a new provider never changes ordering.
 * <p>
 * Today only card has one: show Stripe's card form for this payment intent. M-Pesa needs none (the PIN
 * prompt arrives on the phone by itself).
 *
 * @param kind           CARD
 * @param clientSecret   lets this one browser confirm this one payment with Stripe. Sent to the customer
 *                       only, never stored or logged.
 * @param publishableKey Stripe's public key for the browser's card form
 */
public record PaymentNextAction(String kind, String clientSecret, String publishableKey) {

    @Override
    public String toString() {
        return "PaymentNextAction[kind=" + kind + "]";
    }

}
