package com.hezron.smecommerce.notification.internal.application;

import com.hezron.smecommerce.notification.internal.persistence.StreamTicketEntity;
import com.hezron.smecommerce.notification.internal.persistence.StreamTicketRepository;
import com.hezron.smecommerce.ordering.OrderStatusView;
import com.hezron.smecommerce.ordering.OrderingApi;
import com.hezron.smecommerce.shared.TenantId;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * One-time passes for watching an order live (Guide 6.3). Two ways in:
 * <ul>
 *   <li>a customer, with the private order link from when they ordered;</li>
 *   <li>staff, with their login (the shop comes from the token).</li>
 * </ul>
 * Both get the same kind of ticket; the stream itself doesn't care who asked for it.
 */
@Service
public class StreamTicketService {

    /** Long enough to open the stream right after asking; short enough that a leaked ticket is useless. */
    static final Duration TICKET_LIFETIME = Duration.ofSeconds(60);

    /** Old tickets are kept a day (handy when looking into a problem), then deleted. */
    private static final Duration KEEP_TICKETS_FOR = Duration.ofDays(1);

    private final OrderingApi orderingApi;
    private final StreamTicketRepository tickets;

    StreamTicketService(OrderingApi orderingApi, StreamTicketRepository tickets) {
        this.orderingApi = orderingApi;
        this.tickets = tickets;
    }

    /** @throws OrderToWatchNotFoundException if the shop, number or order link is wrong */
    @Transactional
    public IssuedTicket issueForCustomer(String shopSlug, String orderNumber, String orderToken) {
        Optional<OrderStatusView> order = orderingApi.findOrderForCustomer(shopSlug, orderNumber, orderToken);
        if (order.isEmpty()) {
            throw new OrderToWatchNotFoundException();
        }
        return issue(order.get());
    }

    /** @throws OrderToWatchNotFoundException if the order isn't one of this shop's */
    @Transactional
    public IssuedTicket issueForStaff(TenantId tenantId, UUID orderId) {
        Optional<OrderStatusView> order = orderingApi.findOrder(tenantId, orderId);
        if (order.isEmpty()) {
            throw new OrderToWatchNotFoundException();
        }
        return issue(order.get());
    }

    /**
     * Uses the ticket up and says which order it is for.
     *
     * @throws InvalidStreamTicketException if it is unknown, already used or expired
     */
    @Transactional
    public WatchedOrder redeem(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            throw new InvalidStreamTicketException();
        }
        String hash = StreamTicketCodes.hash(ticket);

        int changed = tickets.useUp(hash, Instant.now());
        if (changed != 1) {
            throw new InvalidStreamTicketException();
        }
        StreamTicketEntity row = tickets.findById(hash).get();
        return new WatchedOrder(TenantId.of(row.getTenantId()), row.getOrderId());
    }

    /** Every day at 04:00: forget tickets that expired more than a day ago. */
    @Scheduled(cron = "0 0 4 * * *")
    @Transactional
    public void deleteOldTickets() {
        tickets.deleteExpiredBefore(Instant.now().minus(KEEP_TICKETS_FOR));
    }

    private IssuedTicket issue(OrderStatusView order) {
        String ticket = StreamTicketCodes.newTicket();
        Instant expiresAt = Instant.now().plus(TICKET_LIFETIME);
        tickets.save(StreamTicketEntity.newTicket(StreamTicketCodes.hash(ticket), order.tenantId().value(),
                order.orderId(), expiresAt));
        return new IssuedTicket(ticket, TICKET_LIFETIME.toSeconds());
    }

    /** The ticket itself is returned once, here, and never stored. */
    public record IssuedTicket(String ticket, long expiresInSeconds) {
    }

    /** Which order a redeemed ticket lets you watch. */
    public record WatchedOrder(TenantId tenantId, UUID orderId) {
    }

}
