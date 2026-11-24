package com.hezron.smecommerce.tenant.internal.application;

/**
 * What the owner may change about the shop. Empty tagline or logo means "none".
 *
 * @param primaryColor buttons and chosen filters, as #RRGGBB
 * @param accentColor  prices and badges, as #RRGGBB
 */
public record UpdateShopSettingsCommand(String name, String tagline, String logoUrl, String primaryColor,
        String accentColor) {
}
