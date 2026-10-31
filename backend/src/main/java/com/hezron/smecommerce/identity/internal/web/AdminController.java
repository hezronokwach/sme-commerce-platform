package com.hezron.smecommerce.identity.internal.web;

import com.hezron.smecommerce.identity.internal.security.TokenClaims;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Who is logged in. The first endpoint behind the JWT: the security chain has already checked the
 * token before this runs, so the method only reads claims and never touches the database.
 */
@RestController
@RequestMapping("/api/admin")
class AdminController {

    /** {@code @AuthenticationPrincipal} hands us the decoded, already verified token. */
    @GetMapping("/me")
    MeResponse me(@AuthenticationPrincipal Jwt jwt) {
        // Only TokenService signs tokens, and it always sets these claims; a token without them can't be ours.
        return new MeResponse(
                UUID.fromString(Objects.requireNonNull(jwt.getSubject(), "sub")),
                UUID.fromString(Objects.requireNonNull(jwt.getClaimAsString(TokenClaims.TENANT_ID), "tid")),
                jwt.getClaimAsString(TokenClaims.ROLE));
    }

    record MeResponse(UUID userId, UUID tenantId, String role) {
    }

}
