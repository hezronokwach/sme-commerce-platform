package com.hezron.smecommerce.tenant.internal.web;

import com.hezron.smecommerce.tenant.internal.application.ShopSettingsResult;

/** JSON answer of {@code GET/PUT /api/admin/shop}. The slug is the shop's web name: its page is /s/{slug}. */
record ShopSettingsResponse(String name, String slug, String tagline, String logoUrl, String primaryColor,
        String accentColor) {

    static ShopSettingsResponse fromResult(ShopSettingsResult result) {
        return new ShopSettingsResponse(result.name(), result.slug(), result.tagline(), result.logoUrl(),
                result.primaryColor(), result.accentColor());
    }

}
