package com.hezron.smecommerce.inventory;

import com.hezron.smecommerce.shared.TenantId;
import java.util.UUID;

/** Stock is now held for this unpaid order. Nobody listens yet. */
public record StockReservedEvent(TenantId tenantId, UUID orderId) {
}
