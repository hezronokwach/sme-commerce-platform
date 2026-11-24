package com.hezron.smecommerce.tenant.internal.application;

import com.hezron.smecommerce.tenant.internal.persistence.TenantBrandingEntity;
import com.hezron.smecommerce.tenant.internal.persistence.TenantEntity;

/** The shop's own details as its admin sees and edits them. The slug is shown but can't be changed. */
public record ShopSettingsResult(String name, String slug, String tagline, String logoUrl, String primaryColor,
        String accentColor) {

    static ShopSettingsResult fromEntities(TenantEntity tenant, TenantBrandingEntity branding) {
        return new ShopSettingsResult(tenant.getName(), tenant.getSlug(), branding.getTagline(),
                branding.getLogoUrl(), branding.getPrimaryColor(), branding.getAccentColor());
    }

}
