package com.hezron.smecommerce.tenant.internal.application;

import com.hezron.smecommerce.shared.BaseException;
import org.springframework.http.HttpStatus;

/** Turning off the only method left: customers would have no way to pay. */
public class LastPaymentMethodException extends BaseException {

    public LastPaymentMethodException() {
        super(HttpStatus.CONFLICT, "LAST_PAYMENT_METHOD", "Keep one way to pay",
                "Turn another payment method on first. Customers need at least one way to pay.");
    }

}
