package com.hezron.smecommerce.inventory.internal.web;

import com.hezron.smecommerce.shared.AccessTokenClaims;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Who is calling, read from the access token. Always from the token, never from the URL or the body:
 * a caller can change those, but not a signed token.
 */
final class CallerOfRequest {

    private CallerOfRequest() {
    }

    /** The caller's shop (the "tid" claim). */
    static UUID tenantIdFrom(Jwt jwt) {
        String tenantId = jwt.getClaimAsString(AccessTokenClaims.TENANT_ID);
        if (tenantId == null) {
            // Can't happen for our tokens: identity always sets tid, and the security chain checked the token.
            throw new IllegalStateException("Access token has no tid claim");
        }
        return UUID.fromString(tenantId);
    }

    /** The caller's staff user id (the standard "sub" claim). */
    static UUID userIdFrom(Jwt jwt) {
        String subject = jwt.getSubject();
        if (subject == null) {
            throw new IllegalStateException("Access token has no sub claim");
        }
        return UUID.fromString(subject);
    }

}
