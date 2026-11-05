package com.hezron.smecommerce.inventory;

import com.hezron.smecommerce.shared.TenantId;
import java.util.UUID;

/**
 * A product is running out: what's available dropped to the shop's warning level or below.
 * Notification will listen to tell the owner. Nobody listens yet.
 *
 * @param available what can still be sold (on hand minus held for unpaid orders)
 * @param threshold the warning level, 5 unless the owner changed it
 */
public record StockLowEvent(TenantId tenantId, UUID productId, int available, int threshold) {
}
