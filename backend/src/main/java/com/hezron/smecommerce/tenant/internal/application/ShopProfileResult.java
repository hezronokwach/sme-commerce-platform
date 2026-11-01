package com.hezron.smecommerce.tenant.internal.application;

import com.hezron.smecommerce.tenant.internal.persistence.TenantBrandingEntity;
import com.hezron.smecommerce.tenant.internal.persistence.TenantEntity;

/**
 * What a customer may see about a shop. Built from two rows (the shop and its branding) and only
 * their public parts: no internal id, plan, status or contact details.
 */
public record ShopProfileResult(String name, String slug, String currency, String primaryColor,
        String accentColor, String logoUrl, String tagline) {

    static ShopProfileResult fromEntities(TenantEntity tenant, TenantBrandingEntity branding) {
        return new ShopProfileResult(tenant.getName(), tenant.getSlug(), tenant.getCurrency(),
                branding.getPrimaryColor(), branding.getAccentColor(), branding.getLogoUrl(),
                branding.getTagline());
    }

}
