package com.hezron.smecommerce.ordering;

import com.hezron.smecommerce.shared.TenantId;
import java.util.UUID;

/**
 * A customer placed an order; it is waiting for payment and its stock is held. Notification will
 * listen to add it to the admin's live order list. Payments does NOT listen to it (CLAUDE.md): ordering
 * asks payments directly instead, in the same transaction (payments issue).
 *
 * @param totalMinor cents: KES 100.00 is 10000
 */
public record OrderPlacedEvent(TenantId tenantId, UUID orderId, String orderNumber, long totalMinor,
        String currency) {
}
