package com.hezron.smecommerce.identity.internal.application;

import com.hezron.smecommerce.shared.BaseException;
import org.springframework.http.HttpStatus;

/**
 * A staff account with this email already exists. Emails are unique across the whole platform so
 * login needs only email and password. Returns 409.
 */
public class EmailTakenException extends BaseException {

    public EmailTakenException() {
        // The email isn't repeated in the message: errors can end up in logs, and emails are personal data.
        super(HttpStatus.CONFLICT, "EMAIL_TAKEN", "Email already registered",
                "An account with this email already exists. Log in instead.");
    }

}
