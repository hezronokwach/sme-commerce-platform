package com.hezron.smecommerce.tenant.internal.web;

import com.hezron.smecommerce.tenant.internal.application.ShopProfileResult;

/** JSON of {@code GET /api/store/{slug}}. {@code logoUrl} and {@code tagline} are null until shop settings exist. */
record ShopProfileResponse(String name, String slug, String currency, String primaryColor, String accentColor,
        String logoUrl, String tagline) {

    static ShopProfileResponse fromResult(ShopProfileResult result) {
        return new ShopProfileResponse(result.name(), result.slug(), result.currency(), result.primaryColor(),
                result.accentColor(), result.logoUrl(), result.tagline());
    }

}
