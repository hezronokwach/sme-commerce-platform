package com.hezron.smecommerce.tenant.internal.application;

import com.hezron.smecommerce.shared.BaseException;
import org.springframework.http.HttpStatus;

/** Turning on a method the platform can't take payments with yet (M-Pesa, until it is connected). */
public class PaymentMethodUnavailableException extends BaseException {

    public PaymentMethodUnavailableException(String displayName) {
        super(HttpStatus.CONFLICT, "PAYMENT_METHOD_UNAVAILABLE", "Not available yet",
                displayName + " payments are not available yet.");
    }

}
