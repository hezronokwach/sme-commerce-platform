package com.hezron.smecommerce.tenant.internal.application;

import com.hezron.smecommerce.shared.BaseException;
import org.springframework.http.HttpStatus;

/** A provider code the platform doesn't know, e.g. a typo in the address. */
public class UnknownPaymentMethodException extends BaseException {

    public UnknownPaymentMethodException(String provider) {
        super(HttpStatus.BAD_REQUEST, "PAYMENT_METHOD_UNKNOWN", "Unknown payment method",
                "There is no payment method called '" + provider + "'.");
    }

}
