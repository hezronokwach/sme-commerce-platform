package com.hezron.smecommerce;

import com.hezron.smecommerce.shared.BaseException;
import org.springframework.http.HttpStatus;

/** The caller sent too many requests of one kind this minute. Returns 429. */
class TooManyRequestsException extends BaseException {

    TooManyRequestsException() {
        super(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS", "Too many requests",
                "Please wait a minute and try again.");
    }

}
