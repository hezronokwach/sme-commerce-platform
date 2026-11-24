package com.hezron.smecommerce.tenant;

import com.hezron.smecommerce.shared.TenantId;

/** An open shop, as other modules may see it. */
public record ActiveShop(TenantId tenantId, String name) {
}
