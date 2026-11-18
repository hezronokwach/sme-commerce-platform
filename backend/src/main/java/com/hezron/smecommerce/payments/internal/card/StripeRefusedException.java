package com.hezron.smecommerce.payments.internal.card;

/** Stripe answered 4xx: it understood and said no (a bad amount, an unsupported currency, ...). */
public class StripeRefusedException extends RuntimeException {

    public StripeRefusedException(String stripeMessage) {
        super(stripeMessage);
    }

}
