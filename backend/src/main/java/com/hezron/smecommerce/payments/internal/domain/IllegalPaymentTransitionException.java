package com.hezron.smecommerce.payments.internal.domain;

import com.hezron.smecommerce.shared.BaseException;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/** A payment was asked to move to a status it may not reach from where it is. A bug if it ever happens. */
@Getter
public class IllegalPaymentTransitionException extends BaseException {

    private final PaymentStatus from;
    private final PaymentStatus to;

    public IllegalPaymentTransitionException(PaymentStatus from, PaymentStatus to) {
        super(HttpStatus.CONFLICT, "ILLEGAL_PAYMENT_TRANSITION", "Payment can't change like that",
                "A payment that is " + from + " can't become " + to + ".");
        this.from = from;
        this.to = to;
    }

}
