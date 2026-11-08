package com.hezron.smecommerce.ordering.internal.web;

import com.hezron.smecommerce.ordering.internal.application.OrderPageResult;
import com.hezron.smecommerce.ordering.internal.application.OrderSummaryResult;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JSON of {@code GET /api/admin/orders}: one page of orders plus what the screen needs for "page 1 of 3".
 *
 * @param page       counting from 0
 * @param totalItems orders matching in all pages together
 */
record OrderPageResponse(List<OrderRow> items, int page, int size, long totalItems) {

    /** One row of the order list. */
    record OrderRow(UUID id, String orderNumber, String customerName, String status, long totalMinor,
            String currency, Instant createdAt) {
    }

    static OrderPageResponse fromResult(OrderPageResult result) {
        List<OrderRow> rows = new ArrayList<>();
        for (OrderSummaryResult order : result.items()) {
            rows.add(new OrderRow(order.id(), order.orderNumber(), order.customerName(), order.status().name(),
                    order.totalMinor(), order.currency(), order.createdAt()));
        }
        return new OrderPageResponse(rows, result.page(), result.size(), result.totalItems());
    }

}
