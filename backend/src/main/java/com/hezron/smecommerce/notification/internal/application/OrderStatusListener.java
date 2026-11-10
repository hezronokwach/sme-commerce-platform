package com.hezron.smecommerce.notification.internal.application;

import com.hezron.smecommerce.ordering.OrderStatusChangedEvent;
import com.hezron.smecommerce.ordering.OrderStatusView;
import com.hezron.smecommerce.ordering.OrderingApi;
import com.hezron.smecommerce.payments.PaymentFailedEvent;
import java.util.Optional;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Pushes order and payment news to whoever is watching the order live. Notification only listens: it
 * never changes an order or a payment.
 * <p>
 * Safe to receive an event twice (ADR 0009): the page just shows the same status again.
 */
@Component
class OrderStatusListener {

    private final OrderStreams streams;
    private final OrderingApi orderingApi;

    OrderStatusListener(OrderStreams streams, OrderingApi orderingApi) {
        this.streams = streams;
        this.orderingApi = orderingApi;
    }

    /** Any move of the order: paid, cancelled, expired, handed over. */
    @ApplicationModuleListener
    void on(OrderStatusChangedEvent event) {
        if (streams.countFor(event.orderId()) == 0) {
            return; // nobody is watching this order
        }
        String message = StatusMessages.forStatus(event.toStatus(), null, null);
        streams.send(event.orderId(), new StatusMessage(event.orderNumber(), event.toStatus(), message,
                StatusMessages.isLast(event.toStatus())));
    }

    /**
     * A failed attempt the customer can retry (cancelled prompt, wrong PIN). The order doesn't change,
     * so there is no OrderStatusChangedEvent for it, but the customer must still hear about it.
     * A failure that can't be retried cancels the order, and that cancellation is announced above.
     */
    @ApplicationModuleListener
    void on(PaymentFailedEvent event) {
        if (!event.retryable()) {
            return;
        }
        if (streams.countFor(event.orderId()) == 0) {
            return;
        }
        Optional<OrderStatusView> order = orderingApi.findOrder(event.tenantId(), event.orderId());
        if (order.isEmpty()) {
            return;
        }
        streams.send(event.orderId(), new StatusMessage(order.get().orderNumber(), order.get().status(),
                StatusMessages.paymentFailed(event.reason()), false));
    }

}
