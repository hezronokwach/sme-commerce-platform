package com.hezron.smecommerce.tenant.internal.web;

import com.hezron.smecommerce.tenant.internal.application.UpdateShopSettingsCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * JSON body of {@code PUT /api/admin/shop}: the whole settings form. Empty tagline or logo removes it.
 * The logo must be an https address (in practice a Cloudinary upload), so the storefront never loads
 * anything over plain http.
 */
record UpdateShopSettingsRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 160) String tagline,
        @Size(max = 2000) @Pattern(regexp = "^(https://.+)?$", message = "must be an https address") String logoUrl,
        @NotNull @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "must be a colour like #1F1B17") String primaryColor,
        @NotNull @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "must be a colour like #B4532A") String accentColor) {

    UpdateShopSettingsCommand toCommand() {
        return new UpdateShopSettingsCommand(name, tagline, logoUrl, primaryColor, accentColor);
    }

}
