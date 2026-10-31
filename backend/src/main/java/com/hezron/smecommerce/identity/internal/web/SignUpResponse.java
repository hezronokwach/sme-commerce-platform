package com.hezron.smecommerce.identity.internal.web;

import com.hezron.smecommerce.identity.internal.application.SignUpResult;
import java.util.UUID;

/** JSON returned by sign-up: the new ids plus an access token (the refresh token goes in the cookie). */
record SignUpResponse(UUID userId, UUID tenantId, String accessToken, long expiresIn) {

    static SignUpResponse from(SignUpResult result) {
        return new SignUpResponse(result.userId(), result.tenantId().value(),
                result.tokens().accessToken(), result.tokens().accessTokenTtl().toSeconds());
    }

}
