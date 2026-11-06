package com.hezron.smecommerce.ordering.internal.application;

import com.hezron.smecommerce.shared.BaseException;
import org.springframework.http.HttpStatus;

/** The order makes no sense (for example the same product on two lines). Returns 400. */
public class InvalidOrderException extends BaseException {

    public InvalidOrderException(String reason) {
        super(HttpStatus.BAD_REQUEST, "ORDER_INVALID", "Invalid order", reason);
    }

}
