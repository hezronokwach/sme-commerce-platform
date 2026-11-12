package com.hezron.smecommerce.ordering.internal.web;

import com.hezron.smecommerce.ordering.internal.application.CustomerOrderResult;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * The customer's order page:
 * <pre>{"orderNumber": "MM-000123", "status": "PENDING_PAYMENT", "totalMinor": 10000, "currency": "KES",
 *  "expiresAt": "...", "phoneHint": "2547******78", "paymentStatus": "FAILED",
 *  "paymentFailureReason": "You cancelled the payment on your phone.", "lines": [...]}</pre>
 */
record CustomerOrderResponse(String orderNumber, String status, String paymentMethod, long totalMinor,
        String currency, Instant expiresAt, Instant createdAt, String phoneHint, List<LineResponse> lines,
        String paymentStatus, String paymentFailureReason) {

    record LineResponse(String productName, long unitPriceMinor, int quantity, long lineTotalMinor) {
    }

    static CustomerOrderResponse fromResult(CustomerOrderResult result) {
        List<LineResponse> lines = new ArrayList<>();
        for (CustomerOrderResult.LineResult line : result.lines()) {
            lines.add(new LineResponse(line.productName(), line.unitPriceMinor(), line.quantity(),
                    line.lineTotalMinor()));
        }
        return new CustomerOrderResponse(result.orderNumber(), result.status().name(),
                result.paymentMethod().name(), result.totalMinor(), result.currency(), result.expiresAt(),
                result.createdAt(), result.phoneHint(), lines, result.paymentStatus(),
                result.paymentFailureReason());
    }

}
