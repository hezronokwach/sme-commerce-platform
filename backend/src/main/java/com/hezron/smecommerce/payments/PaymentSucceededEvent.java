package com.hezron.smecommerce.payments;

import com.hezron.smecommerce.shared.TenantId;
import java.util.UUID;

/**
 * The money for an order arrived. Ordering listens and marks the order PAID; the order's status change
 * then reaches the customer's live page through ordering's OrderStatusChangedEvent.
 *
 * @param amountMinor cents actually collected (already checked against what was asked)
 */
public record PaymentSucceededEvent(TenantId tenantId, UUID orderId, UUID paymentId, String receiptNumber,
        long amountMinor) {
}
