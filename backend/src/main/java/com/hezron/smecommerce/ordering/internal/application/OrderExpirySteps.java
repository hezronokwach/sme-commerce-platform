package com.hezron.smecommerce.ordering.internal.application;

import com.hezron.smecommerce.inventory.InventoryApi;
import com.hezron.smecommerce.ordering.internal.domain.OrderStatus;
import com.hezron.smecommerce.ordering.internal.persistence.OrderEntity;
import com.hezron.smecommerce.ordering.internal.persistence.OrderRepository;
import com.hezron.smecommerce.payments.PaymentStatusView;
import com.hezron.smecommerce.payments.PaymentsApi;
import com.hezron.smecommerce.shared.TenantId;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Expiring one order, in its own transaction with the order row locked. A payment result takes the same
 * lock, so the two can't both act on an old status.
 */
@Service
class OrderExpirySteps {

    private final OrderRepository orders;
    private final OrderStatusRecorder statusRecorder;
    private final InventoryApi inventoryApi;
    private final PaymentsApi paymentsApi;

    OrderExpirySteps(OrderRepository orders, OrderStatusRecorder statusRecorder, InventoryApi inventoryApi,
            PaymentsApi paymentsApi) {
        this.orders = orders;
        this.statusRecorder = statusRecorder;
        this.inventoryApi = inventoryApi;
        this.paymentsApi = paymentsApi;
    }

    /**
     * Expires the order and releases its stock, unless it was paid meanwhile or its newest payment is
     * still under way.
     *
     * @return true if it was expired
     */
    @Transactional
    public boolean expireIfUnpaid(UUID tenantId, UUID orderId, Instant now) {
        Optional<OrderEntity> found = orders.lockByIdAndTenantId(orderId, tenantId);
        if (found.isEmpty()) {
            return false;
        }
        OrderEntity order = found.get();
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            return false; // paid or cancelled while we waited for the lock
        }
        if (order.getExpiresAt() == null || order.getExpiresAt().isAfter(now)) {
            return false;
        }

        // The customer may have paid at the last moment (Guide 3.5). While the newest attempt is still
        // under way, wait: reconciliation settles it within minutes (paid, failed or TIMED_OUT).
        Optional<PaymentStatusView> payment = paymentsApi.findLatestPayment(TenantId.of(tenantId), orderId);
        if (payment.isPresent() && isStillUnderWay(payment.get().status())) {
            return false;
        }

        statusRecorder.record(order, order.expire());
        // The held stock goes back on sale. If the money still arrives later, the order becomes PAID
        // anyway, with "check the stock by hand" in its history (OrderEntity.markPaid).
        inventoryApi.release(TenantId.of(tenantId), orderId);
        return true;
    }

    /** INITIATED, PENDING or UNKNOWN: the money may still come. FAILED and TIMED_OUT don't hold the order. */
    private static boolean isStillUnderWay(String paymentStatus) {
        if ("INITIATED".equals(paymentStatus)) {
            return true;
        }
        if ("PENDING".equals(paymentStatus)) {
            return true;
        }
        if ("UNKNOWN".equals(paymentStatus)) {
            return true;
        }
        return false;
    }

}
