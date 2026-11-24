package com.hezron.smecommerce.catalog;

import com.hezron.smecommerce.shared.TenantId;

/** The open shop behind a storefront's web name. */
public record StorefrontShop(TenantId tenantId, String name) {
}
