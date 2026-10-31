package com.hezron.smecommerce.identity.internal.web;

import com.hezron.smecommerce.identity.internal.application.SignUpResult;
import java.util.UUID;

/** JSON returned by sign-up. Tokens are added when login is built. */
record SignUpResponse(UUID userId, UUID tenantId) {

    static SignUpResponse from(SignUpResult result) {
        return new SignUpResponse(result.userId(), result.tenantId().value());
    }

}
