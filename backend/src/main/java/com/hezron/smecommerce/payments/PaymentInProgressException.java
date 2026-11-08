package com.hezron.smecommerce.payments;

import com.hezron.smecommerce.shared.BaseException;
import org.springframework.http.HttpStatus;

/** The order already has a payment that is still running, so a second one isn't started. Returns 409. */
public class PaymentInProgressException extends BaseException {

    public PaymentInProgressException() {
        super(HttpStatus.CONFLICT, "PAYMENT_IN_PROGRESS", "Payment already running",
                "A payment for this order is already in progress. Please finish it on your phone.");
    }

}
