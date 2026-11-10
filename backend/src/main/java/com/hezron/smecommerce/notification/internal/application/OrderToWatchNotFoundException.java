package com.hezron.smecommerce.notification.internal.application;

import com.hezron.smecommerce.shared.BaseException;
import org.springframework.http.HttpStatus;

/**
 * No ticket: the order doesn't exist, or the caller may not see it (wrong order link, another shop's
 * order). Both give the same 404, so nobody learns that someone else's order exists.
 */
public class OrderToWatchNotFoundException extends BaseException {

    public OrderToWatchNotFoundException() {
        super(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Order not found", "No such order.");
    }

}
