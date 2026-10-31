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

}
