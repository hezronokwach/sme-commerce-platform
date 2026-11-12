package com.hezron.smecommerce.notification.internal.application;

import com.hezron.smecommerce.shared.TenantId;
import java.time.Duration;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Opens the admin's live order feed for one shop, with a one-time SHOP ticket (ADR 0010, ADR 0012). */
@Service
public class ShopFeedService {

    /** Then the browser reconnects with a new ticket, as for the order streams. */
    private static final Duration FEED_LIFETIME = Duration.ofMinutes(10);

    private final StreamTicketService tickets;
    private final ShopFeeds feeds;

    ShopFeedService(StreamTicketService tickets, ShopFeeds feeds) {
        this.tickets = tickets;
        this.feeds = feeds;
    }

    /**
     * Uses the ticket up and opens the feed. The first message says "connected", so the page knows the
     * feed works even when no order comes for a while.
     *
     * @throws InvalidStreamTicketException if the ticket is unknown, used, expired or an order ticket
     */
    public SseEmitter open(String ticket) {
        TenantId tenantId = tickets.redeemShopFeed(ticket);

        SseEmitter stream = new SseEmitter(FEED_LIFETIME.toMillis());
        feeds.add(tenantId.value(), stream);
        feeds.send(tenantId.value(), ShopFeedMessage.connected());
        return stream;
    }

}
