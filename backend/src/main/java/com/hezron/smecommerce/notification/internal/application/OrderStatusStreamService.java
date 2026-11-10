package com.hezron.smecommerce.notification.internal.application;

import com.hezron.smecommerce.notification.internal.application.StreamTicketService.WatchedOrder;
import com.hezron.smecommerce.ordering.OrderStatusView;
import com.hezron.smecommerce.ordering.OrderingApi;
import com.hezron.smecommerce.payments.PaymentStatusView;
import com.hezron.smecommerce.payments.PaymentsApi;
import java.time.Duration;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Opens a live status stream for one order (Guide 6.3). */
@Service
public class OrderStatusStreamService {

    /** The browser reconnects by itself after this; it then needs a new ticket. */
    private static final Duration STREAM_LIFETIME = Duration.ofMinutes(10);

    private final StreamTicketService tickets;
    private final OrderingApi orderingApi;
    private final PaymentsApi paymentsApi;
    private final OrderStreams streams;

    OrderStatusStreamService(StreamTicketService tickets, OrderingApi orderingApi, PaymentsApi paymentsApi,
            OrderStreams streams) {
        this.tickets = tickets;
        this.orderingApi = orderingApi;
        this.paymentsApi = paymentsApi;
        this.streams = streams;
    }

    /**
     * Uses the ticket up, opens the stream and sends the order's CURRENT status straight away, so a
     * page that connects late (or reconnects) still shows the truth without waiting for the next event.
     *
     * @throws InvalidStreamTicketException if the ticket is unknown, used or expired
     */
    public SseEmitter open(String ticket) {
        WatchedOrder watched = tickets.redeem(ticket);

        Optional<OrderStatusView> order = orderingApi.findOrder(watched.tenantId(), watched.orderId());
        if (order.isEmpty()) {
            throw new InvalidStreamTicketException();
        }

        SseEmitter stream = new SseEmitter(STREAM_LIFETIME.toMillis());
        // Registered first, then the current status is sent: an event arriving in between is then
        // sent too, instead of falling into a gap. At worst the page gets the same status twice.
        streams.add(watched.orderId(), stream);
        streams.send(watched.orderId(), currentStatus(order.get()));
        return stream;
    }

    private StatusMessage currentStatus(OrderStatusView order) {
        String paymentStatus = null;
        String failureReason = null;
        Optional<PaymentStatusView> payment = paymentsApi.findLatestPayment(order.tenantId(), order.orderId());
        if (payment.isPresent()) {
            paymentStatus = payment.get().status();
            failureReason = payment.get().failureReason();
        }
        String message = StatusMessages.forStatus(order.status(), paymentStatus, failureReason);
        return new StatusMessage(order.orderNumber(), order.status(), message, StatusMessages.isLast(order.status()));
    }

}
