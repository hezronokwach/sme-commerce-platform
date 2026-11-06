package com.hezron.smecommerce.shared;

import org.springframework.http.HttpStatus;

/**
 * Not a Kenyan mobile number. Returns 400. The number itself isn't repeated in the message: errors can
 * end up in logs, and phone numbers are personal data.
 */
public class InvalidPhoneNumberException extends BaseException {

    public InvalidPhoneNumberException() {
        super(HttpStatus.BAD_REQUEST, "PHONE_INVALID", "Invalid phone number",
                "Enter a Kenyan mobile number, for example 0712 345 678.");
    }

}
