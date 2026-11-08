package com.hezron.smecommerce.ordering.internal.web;

import com.hezron.smecommerce.shared.AccessTokenClaims;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * The caller's shop, read from the access token. Always from the token, never from the URL or the
 * body: a caller can change those, but not a signed token.
 */
final class CallerTenant {

    private CallerTenant() {
    }

    static UUID idFrom(Jwt jwt) {
        String tenantId = jwt.getClaimAsString(AccessTokenClaims.TENANT_ID);
        if (tenantId == null) {
            // Can't happen for our tokens: identity always sets tid, and the security chain checked the token.
            throw new IllegalStateException("Access token has no tid claim");
        }
        return UUID.fromString(tenantId);
    }

}
