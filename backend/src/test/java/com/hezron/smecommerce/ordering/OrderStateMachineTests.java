package com.hezron.smecommerce.ordering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hezron.smecommerce.ordering.internal.domain.ActorType;
import com.hezron.smecommerce.ordering.internal.domain.IllegalOrderTransitionException;
import com.hezron.smecommerce.ordering.internal.domain.OrderStatus;
import com.hezron.smecommerce.ordering.internal.domain.PaymentMethod;
import com.hezron.smecommerce.ordering.internal.persistence.OrderEntity;
import com.hezron.smecommerce.ordering.internal.persistence.OrderStatusHistoryEntity;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Every legal and every illegal status move (architecture section 7: "Test every legal and illegal
 * transition"). Plain unit tests: no Spring, no database.
 * <p>
 * Each test tries one move from all six statuses. The move must work from the allowed ones and throw,
 * leaving the order unchanged, from all the others.
 */
class OrderStateMachineTests {

    private static final UUID STAFF_ID = UUID.randomUUID();

    @Test
    void placeAwaitingPaymentOnlyFromCreated() {
        for (OrderStatus from : OrderStatus.values()) {
            OrderEntity order = orderIn(from);
            if (from == OrderStatus.CREATED) {
                order.placeAwaitingPayment(inFifteenMinutes());
                assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
            } else {
                assertRefused(order, from, OrderStatus.PENDING_PAYMENT, "placeAwaitingPayment");
            }
        }
    }

    @Test
    void markPaidFromPendingPaymentOrExpired() {
        for (OrderStatus from : OrderStatus.values()) {
            OrderEntity order = orderIn(from);
            if (from == OrderStatus.PENDING_PAYMENT || from == OrderStatus.EXPIRED) {
                order.markPaid("QJK3ABC123");
                assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
            } else {
                assertRefused(order, from, OrderStatus.PAID, "markPaid");
            }
        }
    }

    @Test
    void cancelOnlyBeforePayment() {
        for (OrderStatus from : OrderStatus.values()) {
            OrderEntity order = orderIn(from);
            if (from == OrderStatus.CREATED || from == OrderStatus.PENDING_PAYMENT) {
                order.cancel("Customer changed their mind", ActorType.CUSTOMER, null);
                assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            } else {
                assertRefused(order, from, OrderStatus.CANCELLED, "cancel");
            }
        }
    }

    @Test
    void expireOnlyFromPendingPayment() {
        for (OrderStatus from : OrderStatus.values()) {
            OrderEntity order = orderIn(from);
            if (from == OrderStatus.PENDING_PAYMENT) {
                order.expire();
                assertThat(order.getStatus()).isEqualTo(OrderStatus.EXPIRED);
            } else {
                assertRefused(order, from, OrderStatus.EXPIRED, "expire");
            }
        }
    }

    @Test
    void fulfilOnlyFromPaid() {
        for (OrderStatus from : OrderStatus.values()) {
            OrderEntity order = orderIn(from);
            if (from == OrderStatus.PAID) {
                order.fulfil(STAFF_ID);
                assertThat(order.getStatus()).isEqualTo(OrderStatus.FULFILLED);
            } else {
                assertRefused(order, from, OrderStatus.FULFILLED, "fulfil");
            }
        }
    }

    @Test
    void everyMoveDescribesItselfInAHistoryRow() {
        OrderEntity order = orderIn(OrderStatus.PAID);

        OrderStatusHistoryEntity entry = order.fulfil(STAFF_ID);

        assertThat(entry.getOrderId()).isEqualTo(order.getId());
        assertThat(entry.getTenantId()).isEqualTo(order.getTenantId());
        assertThat(entry.getFromStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(entry.getToStatus()).isEqualTo(OrderStatus.FULFILLED);
        assertThat(entry.getActorType()).isEqualTo(ActorType.STAFF);
        assertThat(entry.getActorId()).isEqualTo(STAFF_ID);
    }

    @Test
    void aLatePaymentKeepsTheReceiptInTheHistory() {
        OrderEntity order = orderIn(OrderStatus.EXPIRED);

        OrderStatusHistoryEntity entry = order.markPaid("QJK3LATE01");

        assertThat(entry.getFromStatus()).isEqualTo(OrderStatus.EXPIRED);
        assertThat(entry.getReason()).contains("QJK3LATE01");
        assertThat(entry.getActorType()).isEqualTo(ActorType.SYSTEM);
    }

    @Test
    void placingSetsThePaymentDeadline() {
        OrderEntity order = orderIn(OrderStatus.CREATED);
        Instant deadline = inFifteenMinutes();

        order.placeAwaitingPayment(deadline);

        assertThat(order.getExpiresAt()).isEqualTo(deadline);
    }

    /** The move must throw IllegalOrderTransition naming both statuses, and leave the order as it was. */
    private static void assertRefused(OrderEntity order, OrderStatus from, OrderStatus to, String move) {
        assertThatThrownBy(() -> tryMove(order, move))
                .as("%s from %s", move, from)
                .isInstanceOf(IllegalOrderTransitionException.class)
                .hasFieldOrPropertyWithValue("from", from)
                .hasFieldOrPropertyWithValue("to", to);
        assertThat(order.getStatus()).as("status after refused %s from %s", move, from).isEqualTo(from);
    }

    private static void tryMove(OrderEntity order, String move) {
        if (move.equals("placeAwaitingPayment")) {
            order.placeAwaitingPayment(inFifteenMinutes());
        } else if (move.equals("markPaid")) {
            order.markPaid("QJK3ABC123");
        } else if (move.equals("cancel")) {
            order.cancel("Test", ActorType.STAFF, STAFF_ID);
        } else if (move.equals("expire")) {
            order.expire();
        } else if (move.equals("fulfil")) {
            order.fulfil(STAFF_ID);
        }
    }

    /** A new order taken to {@code status} through legal moves only. */
    private static OrderEntity orderIn(OrderStatus status) {
        OrderEntity order = OrderEntity.newOrder(UUID.randomUUID(), "MM-000001", UUID.randomUUID(), "Amina",
                "254712345678", PaymentMethod.MPESA, 10000, "KES", "0".repeat(64));
        if (status == OrderStatus.CREATED) {
            return order;
        }
        if (status == OrderStatus.CANCELLED) {
            order.cancel("Test", ActorType.STAFF, STAFF_ID);
            return order;
        }
        order.placeAwaitingPayment(inFifteenMinutes());
        if (status == OrderStatus.PENDING_PAYMENT) {
            return order;
        }
        if (status == OrderStatus.EXPIRED) {
            order.expire();
            return order;
        }
        order.markPaid("QJK3ABC123");
        if (status == OrderStatus.PAID) {
            return order;
        }
        order.fulfil(STAFF_ID);
        return order;
    }

    private static Instant inFifteenMinutes() {
        return Instant.now().plusSeconds(15 * 60);
    }

}
