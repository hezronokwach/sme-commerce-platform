package com.hezron.smecommerce.ordering.internal.application;

import com.hezron.smecommerce.shared.BaseException;
import org.springframework.http.HttpStatus;

/**
 * A customer asked for an order with a wrong or missing order link, or for an order this shop doesn't
 * have. All give the same 404, so a guessed order number never reveals that the order exists.
 */
public class CustomerOrderNotFoundException extends BaseException {

    public CustomerOrderNotFoundException() {
        super(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Order not found",
                "We couldn't find this order. Open it from the device you ordered on.");
    }

}
