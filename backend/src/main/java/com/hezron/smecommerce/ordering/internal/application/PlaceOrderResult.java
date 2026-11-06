package com.hezron.smecommerce.ordering.internal.application;

import com.hezron.smecommerce.ordering.internal.domain.OrderStatus;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.NonNull;

/**
 * What placing an order gives back.
 *
 * @param accessToken the private order-link token. Given out once, here; only its hash is stored. The
 *                    storefront builds the order page link with it: /s/mama-mboga/orders/MM-000123?t=...
 * @param expiresAt   the payment deadline
 */
public record PlaceOrderResult(UUID orderId, String orderNumber, String accessToken, OrderStatus status,
        long totalMinor, String currency, Instant expiresAt) {

    /** The access token opens the order: keep it out of logs. */
    @Override
    public @NonNull String toString() {
        return "PlaceOrderResult[orderId=" + orderId + ", orderNumber=" + orderNumber + ", accessToken=***, status="
                + status + ", totalMinor=" + totalMinor + ", currency=" + currency + ", expiresAt=" + expiresAt + "]";
    }

}
