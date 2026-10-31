package com.hezron.smecommerce.identity.internal.application;

import com.hezron.smecommerce.shared.BaseException;
import org.springframework.http.HttpStatus;

/** Too many wrong passwords; the account is locked for a while. Returns 429. */
public class TooManyAttemptsException extends BaseException {

    public TooManyAttemptsException() {
        super(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS", "Too many attempts",
                "Too many failed logins. Wait 15 minutes and try again.");
    }

}
