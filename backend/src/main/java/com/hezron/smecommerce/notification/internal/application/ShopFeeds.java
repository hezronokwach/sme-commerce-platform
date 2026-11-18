package com.hezron.smecommerce.notification.internal.application;

import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * The open shop feeds (the admin's live orders list), by shop. Every staff tab of a shop gets every
 * order message of that shop, and only of that shop. The bookkeeping and the keep-alive are in
 * LiveStreams.
 */
@Component
public class ShopFeeds extends LiveStreams {

    /** Starts tracking a feed for this shop. */
    public void add(UUID tenantId, SseEmitter stream) {
        addStream(tenantId, stream);
    }

    /** Sends an "order" message to every feed of this shop. A feed never ends by itself. */
    public void send(UUID tenantId, ShopFeedMessage message) {
        sendToAll(tenantId, "order", message, false);
    }

}
