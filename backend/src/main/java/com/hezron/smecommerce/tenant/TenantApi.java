package com.hezron.smecommerce.tenant;

import com.hezron.smecommerce.shared.TenantId;

/**
 * What other modules may ask of the tenant module. Implemented in {@code tenant.internal}; callers
 * only ever see this interface, the exceptions and the events in this package.
 */
public interface TenantApi {

    /**
     * Creates an ACTIVE shop with default branding and publishes {@link TenantCreatedEvent}.
     * Joins the caller's transaction, so sign-up can save the shop and its owner together.
     *
     * @throws SlugNotAllowedException if the slug has the wrong format or is reserved
     * @throws SlugTakenException      if another shop already uses the slug
     */
    TenantId createTenant(String shopName, String slug);

    /**
     * Turns a storefront web name into the shop's id. Storefront endpoints call this first, then pass
     * the id into their own queries ({@code WHERE tenant_id = ?}).
     *
     * @throws ShopNotFoundException if no shop has this slug, or the shop is suspended
     */
    TenantId findActiveTenantIdBySlug(String slug);

    /**
     * The same lookup, for callers that also need the shop's name.
     *
     * @throws ShopNotFoundException if no shop has this slug, or the shop is suspended
     */
    ActiveShop findActiveShopBySlug(String slug);

    /**
     * Whether this shop offers this way of paying (FAKE, CARD or MPESA), as its owner set it. Payments
     * asks before starting a payment, so a method the shop turned off can never be used.
     */
    boolean isPaymentMethodEnabled(TenantId tenantId, String provider);

}
