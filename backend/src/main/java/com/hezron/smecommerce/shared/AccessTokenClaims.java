package com.hezron.smecommerce.shared;

/**
 * Claim names in the access token that other modules read. Identity writes the token; any module with
 * admin endpoints reads the caller's shop from it. Only the name lives here, so no module needs to
 * depend on identity just to know it.
 */
public final class AccessTokenClaims {

    /** The caller's shop (tenant) id. Every admin endpoint filters its queries by it. */
    public static final String TENANT_ID = "tid";

    private AccessTokenClaims() {
    }

}
