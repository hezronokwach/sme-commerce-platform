package com.hezron.smecommerce.ordering.internal.application;

import com.hezron.smecommerce.ordering.OrderStatusChangedEvent;
import com.hezron.smecommerce.ordering.internal.persistence.OrderEntity;
import com.hezron.smecommerce.ordering.internal.persistence.OrderStatusHistoryEntity;
import com.hezron.smecommerce.ordering.internal.persistence.OrderStatusHistoryRepository;
import com.hezron.smecommerce.shared.TenantId;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Saves one status change of an order AND announces it, always both. Use this instead of saving a
 * history row directly, so no status change can be forgotten by the live status screens.
 * <p>
 * Runs inside the caller's transaction: the history row, the order's new status and the event's
 * registry row are saved together, or not at all.
 */
@Component
class OrderStatusRecorder {

    private final OrderStatusHistoryRepository history;
    private final ApplicationEventPublisher events;

    OrderStatusRecorder(OrderStatusHistoryRepository history, ApplicationEventPublisher events) {
        this.history = history;
        this.events = events;
    }

    /** {@code change} is the row an OrderEntity state method returned (or the order's first row). */
    void record(OrderEntity order, OrderStatusHistoryEntity change) {
        history.save(change);

        String from = null;
        if (change.getFromStatus() != null) {
            from = change.getFromStatus().name();
        }
        events.publishEvent(new OrderStatusChangedEvent(TenantId.of(order.getTenantId()), order.getId(),
                order.getOrderNumber(), from, change.getToStatus().name(), change.getReason()));
    }

}
