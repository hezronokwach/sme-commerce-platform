package com.hezron.smecommerce.payments;

import com.hezron.smecommerce.shared.BaseException;
import org.springframework.http.HttpStatus;

/**
 * The shop doesn't offer this way of paying (its owner turned it off, or never turned it on). Thrown
 * inside ordering's transaction, so the whole order is rolled back: nothing saved, no stock held.
 * Returns 409.
 */
public class PaymentMethodNotOfferedException extends BaseException {

    public PaymentMethodNotOfferedException() {
        super(HttpStatus.CONFLICT, "PAYMENT_METHOD_NOT_OFFERED", "Payment method not offered",
                "This shop doesn't take this way of paying. Please choose another.");
    }

}
