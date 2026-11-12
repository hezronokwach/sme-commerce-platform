package com.hezron.smecommerce.notification.internal.application;

import java.util.UUID;

/**
 * One "order" message on the admin's live feed, sent as JSON:
 * <pre>{"type": "order-placed", "orderId": "...", "orderNumber": "MM-000123", "status": "PENDING_PAYMENT"}</pre>
 * Small on purpose: the admin page reloads its list when one arrives, so the list (its filter, its
 * pages, the customer's name) is always built by the server the usual way.
 *
 * @param type "connected" (first message, nothing else set), "order-placed" or "order-status"
 */
public record ShopFeedMessage(String type, UUID orderId, String orderNumber, String status) {

    static ShopFeedMessage connected() {
        return new ShopFeedMessage("connected", null, null, null);
    }

    static ShopFeedMessage orderPlaced(UUID orderId, String orderNumber) {
        return new ShopFeedMessage("order-placed", orderId, orderNumber, "PENDING_PAYMENT");
    }

    static ShopFeedMessage orderStatus(UUID orderId, String orderNumber, String status) {
        return new ShopFeedMessage("order-status", orderId, orderNumber, status);
    }

}
