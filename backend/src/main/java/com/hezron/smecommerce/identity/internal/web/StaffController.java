package com.hezron.smecommerce.identity.internal.web;

import com.hezron.smecommerce.identity.internal.application.StaffService;
import com.hezron.smecommerce.identity.internal.security.TokenClaims;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The owner's view of their shop's staff.
 * <p>
 * The shop always comes from the token ({@code tid}), never from the URL or the body: a caller can
 * change a URL, but can't change a signed token. So asking for another shop's staff isn't possible.
 */
@RestController
@RequestMapping("/api/admin/staff")
// Checked before each method runs: STAFF users get 403. hasRole('OWNER') matches authority ROLE_OWNER.
@PreAuthorize("hasRole('OWNER')")
class StaffController {

    private final StaffService staffService;

    StaffController(StaffService staffService) {
        this.staffService = staffService;
    }

    @GetMapping
    List<StaffResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return staffService.list(tenantIdOf(jwt)).stream().map(StaffResponse::fromResult).toList();
    }

    @GetMapping("/{staffId}")
    StaffResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID staffId) {
        return StaffResponse.fromResult(staffService.get(tenantIdOf(jwt), staffId));
    }

    /** The caller's shop, from the token's {@code tid} claim. TokenService always sets it. */
    private static UUID tenantIdOf(Jwt jwt) {
        return UUID.fromString(Objects.requireNonNull(jwt.getClaimAsString(TokenClaims.TENANT_ID), "tid"));
    }

}
