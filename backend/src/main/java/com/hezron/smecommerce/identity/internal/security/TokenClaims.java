package com.hezron.smecommerce.identity.internal.security;

import com.hezron.smecommerce.shared.AccessTokenClaims;

/**
 * Names of our own claims in access tokens. Standard claims (sub, iss, exp, ...) are set through
 * {@code JwtClaimsSet}'s named methods instead.
 */
public final class TokenClaims {

    /** Tenant (shop) the user belongs to. Defined in the shared kernel because other modules read it. */
    public static final String TENANT_ID = AccessTokenClaims.TENANT_ID;

    /** OWNER or STAFF. Turned into ROLE_OWNER / ROLE_STAFF by JwtConfig. */
    public static final String ROLE = "role";

    /** STAFF or CUSTOMER, so a storefront token can never pass as an admin token. */
    public static final String SUBJECT_TYPE = "typ";

    private TokenClaims() {
    }

}
