package com.hezron.smecommerce.identity.internal.application;

import com.hezron.smecommerce.shared.BaseException;
import org.springframework.http.HttpStatus;

/**
 * Login failed. The same answer for an unknown email, a wrong password and a disabled account, so the
 * response never tells an attacker which emails have accounts. Returns 401.
 */
public class InvalidCredentialsException extends BaseException {

    public InvalidCredentialsException() {
        super(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Login failed",
                "The email or password is incorrect.");
    }

}
