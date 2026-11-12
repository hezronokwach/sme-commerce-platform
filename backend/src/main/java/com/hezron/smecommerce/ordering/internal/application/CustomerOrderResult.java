package com.hezron.smecommerce.ordering.internal.application;

import com.hezron.smecommerce.ordering.internal.domain.OrderStatus;
import com.hezron.smecommerce.ordering.internal.domain.PaymentMethod;
import java.time.Instant;
import java.util.List;

/**
 * What the customer's order page shows (Guide 4.5 getOrderForCustomer). Less than the admin sees: no
 * history, no customer id, and the phone only as a hint ("2547******78") of where the PIN prompt went.
 *
 * @param paymentStatus        the newest payment attempt's status, or null if none was made yet
 * @param paymentFailureReason words for the customer, only when that attempt FAILED
 */
public record CustomerOrderResult(String orderNumber, OrderStatus status, PaymentMethod paymentMethod,
        long totalMinor, String currency, Instant expiresAt, Instant createdAt, String phoneHint,
        List<LineResult> lines, String paymentStatus, String paymentFailureReason) {

    /** One product in the order, as it was bought. */
    public record LineResult(String productName, long unitPriceMinor, int quantity, long lineTotalMinor) {
    }

}
