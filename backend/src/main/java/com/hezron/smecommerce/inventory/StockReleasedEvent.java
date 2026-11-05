package com.hezron.smecommerce.inventory;

import com.hezron.smecommerce.shared.TenantId;
import java.util.UUID;

/** This order's held stock went back to available (the order was cancelled or expired). Nobody listens yet. */
public record StockReleasedEvent(TenantId tenantId, UUID orderId) {
}
