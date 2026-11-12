package com.hezron.smecommerce.ordering.internal.persistence;

import com.hezron.smecommerce.ordering.internal.domain.ActorType;
import com.hezron.smecommerce.ordering.internal.domain.IllegalOrderTransitionException;
import com.hezron.smecommerce.ordering.internal.domain.OrderStatus;
import com.hezron.smecommerce.ordering.internal.domain.PaymentMethod;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Persistable;

/**
 * Row in {@code ordering.orders}: one customer's order at one shop.
 * <p>
 * The lines and the status history are separate rows (OrderLineEntity, OrderStatusHistoryEntity) that
 * point at this order by id. They are saved and loaded explicitly, not through JPA relations, so every
 * query is visible in the code.
 */
@Entity
@Table(schema = "ordering", name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA
public class OrderEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    /** Readable, such as MM-000123. */
    @Column(name = "order_number", nullable = false, updatable = false)
    private String orderNumber;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    /** Copied at order time. */
    @Column(name = "customer_name", nullable = false, updatable = false)
    private String customerName;

    /** Copied at order time, in 2547XXXXXXXX form. Never write it to the logs in full. */
    @Column(name = "customer_phone", nullable = false, updatable = false)
    private String customerPhone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, updatable = false)
    private PaymentMethod paymentMethod;

    @Column(name = "subtotal_minor", nullable = false, updatable = false)
    private long subtotalMinor;

    @Column(name = "total_minor", nullable = false, updatable = false)
    private long totalMinor;

    @Column(nullable = false, updatable = false)
    private String currency;

    /** SHA-256 of the private order-link token. The token itself is never stored. */
    @Column(name = "access_token_hash", nullable = false, updatable = false)
    private String accessTokenHash;

    /** Payment deadline. Null while CREATED. */
    @Column(name = "expires_at")
    private Instant expiresAt;

    /** Optimistic locking. Never set in Java: Hibernate owns this number. */
    @SuppressWarnings("unused")
    @Version
    private int version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Transient
    @Getter(AccessLevel.NONE)
    private boolean isNew = true;

    /**
     * A new order in status CREATED. The totals are worked out by the caller from the lines (which use
     * the server's prices). Moving it on to PENDING_PAYMENT is a status change of its own.
     */
    public static OrderEntity newOrder(UUID tenantId, String orderNumber, UUID customerId, String customerName,
            String customerPhone, PaymentMethod paymentMethod, long totalMinor, String currency,
            String accessTokenHash) {
        Instant now = Instant.now();
        OrderEntity order = new OrderEntity();
        order.id = UUID.randomUUID();
        order.tenantId = tenantId;
        order.orderNumber = orderNumber;
        order.customerId = customerId;
        order.customerName = customerName;
        order.customerPhone = customerPhone;
        order.status = OrderStatus.CREATED;
        order.paymentMethod = paymentMethod;
        // No delivery fees yet, so the total is the subtotal.
        order.subtotalMinor = totalMinor;
        order.totalMinor = totalMinor;
        order.currency = currency;
        order.accessTokenHash = accessTokenHash;
        order.createdAt = now;
        order.updatedAt = now;
        return order;
    }

    // ---------------------------------------------------------------------------------------------
    // The state machine (Guide 4.3, architecture section 7). These five methods are the ONLY way an
    // order's status changes. Each checks where the order is now, refuses a move that isn't allowed,
    // and returns the history row describing the move; the caller saves that row with the order.
    //
    //   placeAwaitingPayment   CREATED                    -> PENDING_PAYMENT
    //   markPaid               PENDING_PAYMENT or EXPIRED -> PAID
    //   cancel                 CREATED or PENDING_PAYMENT -> CANCELLED
    //   expire                 PENDING_PAYMENT            -> EXPIRED
    //   fulfil                 PAID                       -> FULFILLED
    //
    // FULFILLED and CANCELLED are final: nothing moves out of them.
    // ---------------------------------------------------------------------------------------------

    /** Stock is held; the customer now has until {@code expiresAt} to pay. */
    public OrderStatusHistoryEntity placeAwaitingPayment(Instant expiresAt) {
        if (status != OrderStatus.CREATED) {
            throw new IllegalOrderTransitionException(status, OrderStatus.PENDING_PAYMENT);
        }
        this.expiresAt = expiresAt;
        return moveTo(OrderStatus.PENDING_PAYMENT, "Waiting for payment", ActorType.CUSTOMER, null);
    }

    /**
     * The money arrived. Also allowed from EXPIRED: a customer who paid after the deadline still paid,
     * so the order is marked PAID (and staff check the stock) instead of ignoring real money.
     */
    public OrderStatusHistoryEntity markPaid(String receiptNumber) {
        if (status != OrderStatus.PENDING_PAYMENT && status != OrderStatus.EXPIRED) {
            throw new IllegalOrderTransitionException(status, OrderStatus.PAID);
        }
        String reason = "Payment succeeded, receipt " + receiptNumber;
        if (status == OrderStatus.EXPIRED) {
            // The stock hold ended with the expiry, so the goods may have been sold to someone else.
            reason = "Paid after the time limit, receipt " + receiptNumber + ": check the stock by hand";
        }
        return moveTo(OrderStatus.PAID, reason, ActorType.SYSTEM, null);
    }

    /** Only before payment. A paid order is refunded, not cancelled (a later feature). */
    public OrderStatusHistoryEntity cancel(String reason, ActorType actorType, UUID actorId) {
        if (status != OrderStatus.CREATED && status != OrderStatus.PENDING_PAYMENT) {
            throw new IllegalOrderTransitionException(status, OrderStatus.CANCELLED);
        }
        return moveTo(OrderStatus.CANCELLED, reason, actorType, actorId);
    }

    /** No payment before the deadline. */
    public OrderStatusHistoryEntity expire() {
        if (status != OrderStatus.PENDING_PAYMENT) {
            throw new IllegalOrderTransitionException(status, OrderStatus.EXPIRED);
        }
        return moveTo(OrderStatus.EXPIRED, "Not paid within the time limit", ActorType.SYSTEM, null);
    }

    /** Staff handed the goods over. */
    public OrderStatusHistoryEntity fulfil(UUID staffId) {
        if (status != OrderStatus.PAID) {
            throw new IllegalOrderTransitionException(status, OrderStatus.FULFILLED);
        }
        return moveTo(OrderStatus.FULFILLED, "Handed over to the customer", ActorType.STAFF, staffId);
    }

    /**
     * Not a move: the order stays PENDING_PAYMENT while the customer tries paying again. The row only
     * lets staff see each attempt in the order's history.
     */
    public OrderStatusHistoryEntity recordNewPaymentAttempt() {
        if (status != OrderStatus.PENDING_PAYMENT) {
            throw new IllegalOrderTransitionException(status, OrderStatus.PENDING_PAYMENT);
        }
        return OrderStatusHistoryEntity.newEntry(tenantId, id, status, status, "Customer tried paying again",
                ActorType.CUSTOMER, null);
    }

    /** Changes the status and returns the history row that records the change. */
    private OrderStatusHistoryEntity moveTo(OrderStatus to, String reason, ActorType actorType, UUID actorId) {
        OrderStatus from = this.status;
        this.status = to;
        return OrderStatusHistoryEntity.newEntry(tenantId, id, from, to, reason, actorType, actorId);
    }

    /** Written out only to say it's never null: every order gets its id in newOrder. */
    @Override
    public @NonNull UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

}
