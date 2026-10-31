package com.hezron.smecommerce.identity.internal.application;

import com.hezron.smecommerce.shared.BaseException;
import org.springframework.http.HttpStatus;

/**
 * The refresh token is missing, unknown, expired, revoked or reused. One answer for all of them:
 * the caller only needs to know they must log in again. Returns 401.
 */
public class InvalidRefreshTokenException extends BaseException {

    public InvalidRefreshTokenException() {
        super(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "Session expired",
                "Your session has ended. Log in again.");
    }

}
