package com.hezron.smecommerce.tenant;

import com.hezron.smecommerce.shared.TenantId;

/**
 * Published when a shop is created. Ordering will listen to it to set up the shop's order numbers.
 * Events are stored in the database once the event registry is set up, so they never carry secrets.
 */
public record TenantCreatedEvent(TenantId tenantId, String slug, String shopName) {
}
