package com.hezron.smecommerce.ordering.internal.application;

import com.hezron.smecommerce.inventory.InventoryApi;
import com.hezron.smecommerce.ordering.internal.domain.ActorType;
import com.hezron.smecommerce.ordering.internal.domain.OrderStatus;
import com.hezron.smecommerce.ordering.internal.persistence.OrderEntity;
import com.hezron.smecommerce.ordering.internal.persistence.OrderRepository;
import com.hezron.smecommerce.payments.PaymentFailedEvent;
import com.hezron.smecommerce.payments.PaymentSucceededEvent;
import com.hezron.smecommerce.shared.TenantId;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Ordering's side of a finished payment (Guide 4.4). Payments announces the outcome; ordering decides
 * what it means for the order and the stock.
 * <p>
 * Events can arrive twice (Modulith retries a listener that failed), so each method looks at the
 * order's status first and stops if the work was already done. That makes running it twice harmless.
 * <p>
 * {@code @ApplicationModuleListener}: after payments' transaction commits, on another thread, in a
 * transaction of our own (see ProductCreatedListener in inventory for the longer explanation).
 */
@Component
class PaymentResultListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentResultListener.class);

    private final OrderRepository orders;
    private final OrderStatusRecorder statusRecorder;
    private final InventoryApi inventoryApi;

    PaymentResultListener(OrderRepository orders, OrderStatusRecorder statusRecorder, InventoryApi inventoryApi) {
        this.orders = orders;
        this.statusRecorder = statusRecorder;
        this.inventoryApi = inventoryApi;
    }

    @ApplicationModuleListener
    void on(PaymentSucceededEvent event) {
        Optional<OrderEntity> found = orders.findByIdAndTenantId(event.orderId(), event.tenantId().value());
        if (found.isEmpty()) {
            log.error("Payment {} succeeded for order {}, which doesn't exist", event.paymentId(), event.orderId());
            return;
        }
        OrderEntity order = found.get();

        if (order.getStatus() == OrderStatus.PAID || order.getStatus() == OrderStatus.FULFILLED) {
            return; // already handled: this event was delivered twice
        }
        if (order.getStatus() == OrderStatus.CANCELLED) {
            // The money arrived for an order that was cancelled. Real money must not be ignored, but
            // the order can't become PAID any more; staff refund it (a later feature).
            log.error("Payment {} succeeded for cancelled order {}: needs a refund", event.paymentId(), order.getId());
            return;
        }

        boolean wasExpired = order.getStatus() == OrderStatus.EXPIRED;
        // markPaid's history row says "check the stock by hand" when the order had expired.
        statusRecorder.record(order, order.markPaid(event.receiptNumber()));

        // After expiry the hold was already released (someone else may have bought the stock since),
        // so there is nothing to confirm. Otherwise the held stock now leaves the shelf for good.
        if (!wasExpired) {
            inventoryApi.confirm(event.tenantId(), order.getId(), order.getOrderNumber());
        }
    }

    @ApplicationModuleListener
    void on(PaymentFailedEvent event) {
        Optional<OrderEntity> found = orders.findByIdAndTenantId(event.orderId(), event.tenantId().value());
        if (found.isEmpty()) {
            log.error("Payment {} failed for order {}, which doesn't exist", event.paymentId(), event.orderId());
            return;
        }
        OrderEntity order = found.get();

        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            return; // already paid, cancelled or expired: nothing to do
        }
        if (event.retryable()) {
            // Cancelled prompt, wrong PIN, ...: the order keeps waiting, and its stock stays held, so
            // the customer can try again before the time limit.
            return;
        }

        statusRecorder.record(order, order.cancel("Payment failed: " + event.reason(), ActorType.SYSTEM, null));
        // The held stock goes back on sale.
        inventoryApi.release(TenantId.of(order.getTenantId()), order.getId());
    }

}
