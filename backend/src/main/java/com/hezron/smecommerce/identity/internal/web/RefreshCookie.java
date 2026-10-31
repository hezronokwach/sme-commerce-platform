package com.hezron.smecommerce.identity.internal.web;

import com.hezron.smecommerce.identity.internal.application.AuthTokens;
import java.time.Duration;
import org.springframework.http.ResponseCookie;

/**
 * The cookie that carries the refresh token. Each flag closes one way of stealing or misusing it:
 * <ul>
 *   <li>{@code HttpOnly}: page scripts can't read it, so an XSS bug can't copy it.</li>
 *   <li>{@code Secure}: only sent over HTTPS (browsers treat localhost as secure too, so local dev works).</li>
 *   <li>{@code SameSite=Strict}: never sent when another site triggers the request.</li>
 *   <li>{@code Path=/api/auth}: only sent to refresh and logout, not with every API call.</li>
 * </ul>
 */
final class RefreshCookie {

    static final String NAME = "refresh_token";
    private static final String PATH = "/api/auth";

    private RefreshCookie() {
    }

    /** Set-Cookie header value holding the new refresh token. */
    static String with(AuthTokens tokens) {
        return build(tokens.refreshToken(), tokens.refreshTokenTtl());
    }

    /** Set-Cookie header value that makes the browser delete the cookie (Max-Age=0). */
    static String cleared() {
        return build("", Duration.ZERO);
    }

    private static String build(String value, Duration maxAge) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(PATH)
                .maxAge(maxAge)
                .build()
                .toString();
    }

}
