package com.hezron.smecommerce.ordering.internal.application;

import com.hezron.smecommerce.shared.BaseException;
import org.springframework.http.HttpStatus;

/** "Try again" on an order that can't take a payment any more: already paid, cancelled, or too late. */
public class OrderNotPayableException extends BaseException {

    public OrderNotPayableException(String detail) {
        super(HttpStatus.CONFLICT, "ORDER_NOT_PAYABLE", "This order can't be paid now", detail);
    }

}
