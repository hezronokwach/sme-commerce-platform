package com.hezron.smecommerce.ordering.internal.application;

import com.hezron.smecommerce.ordering.internal.application.CustomerOrderResult.LineResult;
import com.hezron.smecommerce.ordering.internal.domain.OrderStatus;
import com.hezron.smecommerce.ordering.internal.persistence.OrderEntity;
import com.hezron.smecommerce.ordering.internal.persistence.OrderLineEntity;
import com.hezron.smecommerce.ordering.internal.persistence.OrderLineRepository;
import com.hezron.smecommerce.ordering.internal.persistence.OrderRepository;
import com.hezron.smecommerce.ordering.internal.persistence.OrderStatusHistoryRepository;
import com.hezron.smecommerce.payments.PaymentProviderName;
import com.hezron.smecommerce.payments.PaymentStatusView;
import com.hezron.smecommerce.payments.PaymentsApi;
import com.hezron.smecommerce.payments.RequestPaymentCommand;
import com.hezron.smecommerce.shared.PhoneNumber;
import com.hezron.smecommerce.shared.TenantId;
import com.hezron.smecommerce.tenant.TenantApi;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The customer's own order, on the storefront, with no login (Guide 4.5, workflow W7). The proof that
 * the order is theirs is the private order-link token they got when ordering; only its hash is stored.
 */
@Service
public class CustomerOrderService {

    private final TenantApi tenantApi;
    private final PaymentsApi paymentsApi;
    private final OrderRepository orders;
    private final OrderLineRepository orderLines;
    private final OrderStatusHistoryRepository history;

    CustomerOrderService(TenantApi tenantApi, PaymentsApi paymentsApi, OrderRepository orders,
            OrderLineRepository orderLines, OrderStatusHistoryRepository history) {
        this.tenantApi = tenantApi;
        this.paymentsApi = paymentsApi;
        this.orders = orders;
        this.orderLines = orderLines;
        this.history = history;
    }

    /**
     * @throws com.hezron.smecommerce.tenant.ShopNotFoundException if no open shop has this web name
     * @throws CustomerOrderNotFoundException                       if the order or its token don't match
     */
    @Transactional(readOnly = true)
    public CustomerOrderResult getOrder(String shopSlug, String orderNumber, String orderToken) {
        OrderEntity order = findOwnedOrder(shopSlug, orderNumber, orderToken);

        List<LineResult> lines = new ArrayList<>();
        for (OrderLineEntity line : orderLines.findAllByOrderIdAndTenantId(order.getId(), order.getTenantId())) {
            lines.add(new LineResult(line.getProductName(), line.getUnitPriceMinor(), line.getQuantity(),
                    line.getLineTotalMinor()));
        }

        String paymentStatus = null;
        String paymentFailureReason = null;
        Optional<PaymentStatusView> payment = paymentsApi.findLatestPayment(TenantId.of(order.getTenantId()),
                order.getId());
        if (payment.isPresent()) {
            paymentStatus = payment.get().status();
            paymentFailureReason = payment.get().failureReason();
        }

        // toString() hides the middle digits: enough for the customer to recognise their own number.
        String phoneHint = PhoneNumber.parse(order.getCustomerPhone()).toString();

        return new CustomerOrderResult(order.getOrderNumber(), order.getStatus(), order.getPaymentMethod(),
                order.getTotalMinor(), order.getCurrency(), order.getExpiresAt(), order.getCreatedAt(), phoneHint,
                lines, paymentStatus, paymentFailureReason);
    }

    /**
     * "Try again" after a failed attempt (cancelled prompt, wrong PIN): a new payment attempt for the
     * same amount, to the same phone. Like the first one, payments only saves it here; the provider is
     * asked after this transaction commits.
     *
     * @throws CustomerOrderNotFoundException                       if the order or its token don't match
     * @throws OrderNotPayableException                             if paid, cancelled, expired or past its deadline
     * @throws com.hezron.smecommerce.payments.PaymentInProgressException if an attempt is still running
     */
    @Transactional
    public void tryPaymentAgain(String shopSlug, String orderNumber, String orderToken) {
        OrderEntity order = findOwnedOrder(shopSlug, orderNumber, orderToken);

        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new OrderNotPayableException(whyNotPayable(order.getStatus()));
        }
        // The expiry job (a later issue) moves late orders to EXPIRED. Until it has run, the deadline
        // itself still counts: the stock hold ends then, so a payment after it may buy nothing.
        if (order.getExpiresAt() != null && Instant.now().isAfter(order.getExpiresAt())) {
            throw new OrderNotPayableException("The time to pay ran out. Please order again.");
        }

        // The two PaymentMethod/PaymentProviderName enums share their names (as in PlaceOrderService).
        PaymentProviderName provider = PaymentProviderName.valueOf(order.getPaymentMethod().name());
        paymentsApi.requestPayment(new RequestPaymentCommand(TenantId.of(order.getTenantId()), order.getId(),
                order.getOrderNumber(), order.getTotalMinor(), order.getCurrency(), provider,
                order.getCustomerPhone()));

        // Staff can see each attempt in the order's history. The status itself doesn't change, so this
        // row is saved directly, without announcing a status change.
        history.save(order.recordNewPaymentAttempt());
    }

    /** {@code WHERE tenant_id = ? AND order_number = ?}, then the token must match the stored hash. */
    private OrderEntity findOwnedOrder(String shopSlug, String orderNumber, String orderToken) {
        TenantId tenantId = tenantApi.findActiveTenantIdBySlug(shopSlug);

        Optional<OrderEntity> found = orders.findByTenantIdAndOrderNumber(tenantId.value(), orderNumber);
        if (found.isEmpty()) {
            throw new CustomerOrderNotFoundException();
        }
        OrderEntity order = found.get();
        if (orderToken == null || !OrderAccessTokens.matches(orderToken, order.getAccessTokenHash())) {
            throw new CustomerOrderNotFoundException();
        }
        return order;
    }

    private static String whyNotPayable(OrderStatus status) {
        if (status == OrderStatus.PAID || status == OrderStatus.FULFILLED) {
            return "This order is already paid.";
        }
        if (status == OrderStatus.EXPIRED) {
            return "The time to pay ran out. Please order again.";
        }
        if (status == OrderStatus.CANCELLED) {
            return "This order was cancelled. Please order again.";
        }
        return "This order isn't waiting for payment.";
    }

}
