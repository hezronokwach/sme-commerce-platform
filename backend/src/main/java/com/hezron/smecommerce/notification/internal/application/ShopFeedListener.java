package com.hezron.smecommerce.notification.internal.application;

import com.hezron.smecommerce.ordering.OrderPlacedEvent;
import com.hezron.smecommerce.ordering.OrderStatusChangedEvent;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Tells the shop's open admin feeds about its orders: a new order, and each later change of status.
 * Each message goes only to feeds of the event's own shop (the key is the event's tenant id).
 * <p>
 * Safe to receive an event twice (ADR 0009): the admin page just reloads its list once more.
 */
@Component
class ShopFeedListener {

    private final ShopFeeds feeds;

    ShopFeedListener(ShopFeeds feeds) {
        this.feeds = feeds;
    }

    /** A customer placed an order. */
    @ApplicationModuleListener
    void on(OrderPlacedEvent event) {
        if (feeds.countFor(event.tenantId().value()) == 0) {
            return; // nobody from this shop has the orders page open
        }
        feeds.send(event.tenantId().value(), ShopFeedMessage.orderPlaced(event.orderId(), event.orderNumber()));
    }

    /**
     * Paid, cancelled, expired, handed over. The steps of placing an order (nothing to CREATED, then
     * CREATED to PENDING_PAYMENT) are skipped: "order-placed" above already covers them.
     */
    @ApplicationModuleListener
    void on(OrderStatusChangedEvent event) {
        if (event.fromStatus() == null || "CREATED".equals(event.fromStatus())) {
            return;
        }
        if (feeds.countFor(event.tenantId().value()) == 0) {
            return;
        }
        feeds.send(event.tenantId().value(),
                ShopFeedMessage.orderStatus(event.orderId(), event.orderNumber(), event.toStatus()));
    }

}
