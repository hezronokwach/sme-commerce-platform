package com.hezron.smecommerce.identity.internal.web;

import com.hezron.smecommerce.identity.internal.application.AuthTokens;

/**
 * JSON returned by login and refresh. The refresh token is not in here: it travels in the cookie.
 *
 * @param expiresIn seconds until the access token expires, so the client can refresh just before
 */
record TokenResponse(String accessToken, long expiresIn) {

    static TokenResponse from(AuthTokens tokens) {
        return new TokenResponse(tokens.accessToken(), tokens.accessTokenTtl().toSeconds());
    }

}
