package com.hezron.smecommerce.identity.internal.application;

import java.time.Duration;
import org.jspecify.annotations.NonNull;

/**
 * A fresh token pair. The access token goes in the response body; the raw refresh token goes in an
 * httpOnly cookie and is never stored server-side (only its hash is).
 */
public record TokensResult(String accessToken, Duration accessTokenTtl, String refreshToken, Duration refreshTokenTtl) {

    /** Tokens are as good as passwords: keep them out of logs. */
    @Override
    public @NonNull String toString() {
        return "TokensResult[accessToken=***, accessTokenTtl=" + accessTokenTtl
                + ", refreshToken=***, refreshTokenTtl=" + refreshTokenTtl + "]";
    }

}
