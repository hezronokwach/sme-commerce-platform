package com.hezron.smecommerce.ordering.internal.web;

import com.hezron.smecommerce.ordering.internal.application.OrderDetailResult;
import com.hezron.smecommerce.ordering.internal.application.OrderDetailResult.LineResult;
import com.hezron.smecommerce.ordering.internal.application.OrderDetailResult.StatusChangeResult;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** JSON of {@code GET /api/admin/orders/{id}}: the whole order, its lines and its status history. */
record OrderDetailResponse(UUID id, String orderNumber, String customerName, String customerPhone, String status,
        String paymentMethod, long totalMinor, String currency, Instant expiresAt, Instant createdAt,
        List<LineRow> lines, List<StatusChangeRow> history) {

    /** One product in the order. */
    record LineRow(UUID productId, String productName, long unitPriceMinor, int quantity, long lineTotalMinor) {
    }

    /** One status change. {@code fromStatus} is null for the first one. */
    record StatusChangeRow(String fromStatus, String toStatus, String reason, String actorType, Instant at) {
    }

    static OrderDetailResponse fromResult(OrderDetailResult result) {
        List<LineRow> lines = new ArrayList<>();
        for (LineResult line : result.lines()) {
            lines.add(new LineRow(line.productId(), line.productName(), line.unitPriceMinor(), line.quantity(),
                    line.lineTotalMinor()));
        }

        List<StatusChangeRow> history = new ArrayList<>();
        for (StatusChangeResult change : result.history()) {
            String from = null;
            if (change.fromStatus() != null) {
                from = change.fromStatus().name();
            }
            history.add(new StatusChangeRow(from, change.toStatus().name(), change.reason(),
                    change.actorType().name(), change.at()));
        }

        return new OrderDetailResponse(result.id(), result.orderNumber(), result.customerName(),
                result.customerPhone(), result.status().name(), result.paymentMethod().name(), result.totalMinor(),
                result.currency(), result.expiresAt(), result.createdAt(), lines, history);
    }

}
