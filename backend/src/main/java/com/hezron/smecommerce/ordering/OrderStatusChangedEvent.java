package com.hezron.smecommerce.ordering;

import com.hezron.smecommerce.shared.TenantId;
import java.util.UUID;

/**
 * An order moved from one status to another (any move: placed, waiting for payment, paid, cancelled,
 * expired, handed over). Published together with the history row that records the move, in the same
 * transaction. Notification listens to push the change to whoever is watching the order live.
 * <p>
 * The statuses are plain text ("PENDING_PAYMENT", "PAID", ...) because ordering's OrderStatus enum is
 * internal to ordering.
 *
 * @param fromStatus null for the very first entry (the order was just created)
 * @param reason     words from the history row, e.g. "Payment succeeded, receipt FK1234"
 */
public record OrderStatusChangedEvent(TenantId tenantId, UUID orderId, String orderNumber, String fromStatus,
        String toStatus, String reason) {
}
