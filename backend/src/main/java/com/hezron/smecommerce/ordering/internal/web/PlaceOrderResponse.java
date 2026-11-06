package com.hezron.smecommerce.ordering.internal.web;

import com.hezron.smecommerce.ordering.internal.application.PlaceOrderResult;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.NonNull;

/**
 * JSON returned after placing an order. {@code accessToken} is the only copy the customer will ever get
 * of their private order link's key; the storefront keeps it to show the order page.
 */
record PlaceOrderResponse(UUID orderId, String orderNumber, String accessToken, String status, long totalMinor,
        String currency, Instant expiresAt) {

    static PlaceOrderResponse fromResult(PlaceOrderResult result) {
        return new PlaceOrderResponse(result.orderId(), result.orderNumber(), result.accessToken(),
                result.status().name(), result.totalMinor(), result.currency(), result.expiresAt());
    }

    @Override
    public @NonNull String toString() {
        return "PlaceOrderResponse[orderNumber=" + orderNumber + ", accessToken=***, status=" + status + "]";
    }

}
