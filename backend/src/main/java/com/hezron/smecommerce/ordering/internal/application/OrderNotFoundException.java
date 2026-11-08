package com.hezron.smecommerce.ordering.internal.application;

import com.hezron.smecommerce.shared.BaseException;
import org.springframework.http.HttpStatus;

/** No order with this id in the caller's shop. Another shop's order gives the same 404. */
public class OrderNotFoundException extends BaseException {

    public OrderNotFoundException() {
        super(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Order not found",
                "There is no order with this id in your shop.");
    }

}
