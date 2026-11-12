package com.hezron.smecommerce.notification.internal.application;

import com.hezron.smecommerce.ordering.OrderStatusChangedEvent;
import com.hezron.smecommerce.ordering.OrderStatusView;
import com.hezron.smecommerce.ordering.OrderingApi;
import com.hezron.smecommerce.payments.PaymentFailedEvent;
import com.hezron.smecommerce.payments.PaymentStartedEvent;
import com.hezron.smecommerce.payments.PaymentStatusView;
import com.hezron.smecommerce.payments.PaymentsApi;
import com.hezron.smecommerce.shared.TenantId;
import java.util.Optional;
import java.util.UUID;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Pushes order and payment news to whoever is watching the order live. Notification only listens: it
 * never changes an order or a payment.
 * <p>
 * Safe to receive an event twice (ADR 0009): the page just shows the same status again.
 * <p>
 * Events from different modules can arrive in any order (each listener runs on its own thread). So a
 * message about a payment that is no longer the newest news is not sent: an older "waiting" must never
 * replace a newer "failed" or "paid" on the customer's screen.
 */
@Component
class OrderStatusListener {

    private final OrderStreams streams;
    private final OrderingApi orderingApi;
    private final PaymentsApi paymentsApi;

    OrderStatusListener(OrderStreams streams, OrderingApi orderingApi, PaymentsApi paymentsApi) {
        this.streams = streams;
        this.orderingApi = orderingApi;
        this.paymentsApi = paymentsApi;
    }

    /** Any move of the order: paid, cancelled, expired, handed over. */
    @ApplicationModuleListener
    void on(OrderStatusChangedEvent event) {
        if (streams.countFor(event.orderId()) == 0) {
            return; // nobody is watching this order
        }
        String paymentStatus = latestPaymentStatus(event.tenantId(), event.orderId());
        String message = StatusMessages.forStatus(event.toStatus(), null, null);
        streams.send(event.orderId(), new StatusMessage(event.orderNumber(), event.toStatus(), paymentStatus,
                message, StatusMessages.isLast(event.toStatus())));
    }

    /**
     * A new attempt reached the provider: the PIN prompt is on the phone (PENDING), or we can't tell yet
     * (UNKNOWN). After a failed attempt and "Try again", this is what turns the page back to "waiting".
     */
    @ApplicationModuleListener
    void on(PaymentStartedEvent event) {
        if (streams.countFor(event.orderId()) == 0) {
            return;
        }
        Optional<OrderStatusView> order = orderingApi.findOrder(event.tenantId(), event.orderId());
        if (order.isEmpty() || !"PENDING_PAYMENT".equals(order.get().status())) {
            return; // already paid, cancelled or expired: that news was (or will be) sent instead
        }
        // Only if this attempt is still running. If it already succeeded or failed, that event is newer.
        String latest = latestPaymentStatus(event.tenantId(), event.orderId());
        if (!event.status().equals(latest)) {
            return;
        }
        String message = StatusMessages.forStatus(order.get().status(), event.status(), null);
        streams.send(event.orderId(), new StatusMessage(order.get().orderNumber(), order.get().status(),
                event.status(), message, false));
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
        // The customer already pressed "Try again" and a newer attempt is running: don't show the old failure.
        if (!"FAILED".equals(latestPaymentStatus(event.tenantId(), event.orderId()))) {
            return;
        }
        streams.send(event.orderId(), new StatusMessage(order.get().orderNumber(), order.get().status(), "FAILED",
                StatusMessages.paymentFailed(event.reason()), false));
    }

    /** The newest attempt's status, or null if the order has none yet. */
    private String latestPaymentStatus(TenantId tenantId, UUID orderId) {
        Optional<PaymentStatusView> payment = paymentsApi.findLatestPayment(tenantId, orderId);
        if (payment.isEmpty()) {
            return null;
        }
        return payment.get().status();
    }

}
