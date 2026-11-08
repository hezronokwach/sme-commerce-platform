package com.hezron.smecommerce.ordering.internal.application;

import com.hezron.smecommerce.ordering.internal.domain.ActorType;
import com.hezron.smecommerce.ordering.internal.domain.OrderStatus;
import com.hezron.smecommerce.ordering.internal.domain.PaymentMethod;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Everything the admin's order page shows. Staff see the customer's full phone: they may need to call.
 */
public record OrderDetailResult(UUID id, String orderNumber, String customerName, String customerPhone,
        OrderStatus status, PaymentMethod paymentMethod, long totalMinor, String currency, Instant expiresAt,
        Instant createdAt, List<LineResult> lines, List<StatusChangeResult> history) {

    /** One product in the order, as it was bought. */
    public record LineResult(UUID productId, String productName, long unitPriceMinor, int quantity,
            long lineTotalMinor) {
    }

    /** One status change: from (null for the first), to, why, who and when. */
    public record StatusChangeResult(OrderStatus fromStatus, OrderStatus toStatus, String reason,
            ActorType actorType, Instant at) {
    }

}
