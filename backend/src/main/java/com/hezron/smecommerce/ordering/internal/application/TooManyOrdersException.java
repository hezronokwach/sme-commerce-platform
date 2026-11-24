package com.hezron.smecommerce.ordering.internal.application;

import com.hezron.smecommerce.shared.BaseException;
import org.springframework.http.HttpStatus;

/** Too many orders for one phone this minute: each can send a payment prompt to it. Returns 429. */
public class TooManyOrdersException extends BaseException {

    public TooManyOrdersException() {
        super(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ORDERS", "Too many orders",
                "Too many orders for this phone. Please wait a minute and try again.");
    }

}
