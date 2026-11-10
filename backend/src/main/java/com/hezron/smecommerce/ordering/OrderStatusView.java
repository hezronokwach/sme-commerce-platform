package com.hezron.smecommerce.ordering;

import com.hezron.smecommerce.shared.TenantId;
import java.util.UUID;

/**
 * What other modules may know about one order: which shop, which order, and where it is now.
 *
 * @param status plain text, e.g. "PENDING_PAYMENT" (ordering's OrderStatus enum stays internal)
 */
public record OrderStatusView(TenantId tenantId, UUID orderId, String orderNumber, String status) {
}
