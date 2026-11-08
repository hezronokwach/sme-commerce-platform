package com.hezron.smecommerce.ordering.internal.application;

import com.hezron.smecommerce.ordering.internal.domain.OrderStatus;
import com.hezron.smecommerce.ordering.internal.persistence.OrderEntity;
import java.time.Instant;
import java.util.UUID;

/** One row of the admin's order list: just enough to recognise the order. */
public record OrderSummaryResult(UUID id, String orderNumber, String customerName, OrderStatus status,
        long totalMinor, String currency, Instant createdAt) {

    static OrderSummaryResult fromEntity(OrderEntity entity) {
        return new OrderSummaryResult(entity.getId(), entity.getOrderNumber(), entity.getCustomerName(),
                entity.getStatus(), entity.getTotalMinor(), entity.getCurrency(), entity.getCreatedAt());
    }

}
