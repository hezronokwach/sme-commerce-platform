package com.hezron.smecommerce.tenant.internal.web;

import com.hezron.smecommerce.shared.AccessTokenClaims;
import com.hezron.smecommerce.tenant.internal.application.ShopSettingsService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The shop's own details. Owners and staff may read them (staff need the shop's link too); only the
 * owner may change them. The shop is always the caller's own, from the access token.
 */
@RestController
@RequestMapping("/api/admin/shop")
class ShopSettingsController {

    private final ShopSettingsService shopSettings;

    ShopSettingsController(ShopSettingsService shopSettings) {
        this.shopSettings = shopSettings;
    }

    @GetMapping
    ShopSettingsResponse get(@AuthenticationPrincipal Jwt jwt) {
        return ShopSettingsResponse.fromResult(shopSettings.get(callerTenant(jwt)));
    }

    @PutMapping
    @PreAuthorize("hasRole('OWNER')")
    ShopSettingsResponse update(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateShopSettingsRequest request) {
        return ShopSettingsResponse.fromResult(shopSettings.update(callerTenant(jwt), request.toCommand()));
    }

    private static UUID callerTenant(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString(AccessTokenClaims.TENANT_ID));
    }

}
