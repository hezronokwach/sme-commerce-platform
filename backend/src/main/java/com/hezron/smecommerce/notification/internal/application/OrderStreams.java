package com.hezron.smecommerce.notification.internal.application;

import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * The open order-status streams (the customer's order page, issue #17), by order id. The bookkeeping
 * and the keep-alive are in LiveStreams.
 */
@Component
public class OrderStreams extends LiveStreams {

    /** Starts tracking a stream watching this order. */
    public void add(UUID orderId, SseEmitter stream) {
        addStream(orderId, stream);
    }

    /** Sends a "status" message to every stream watching this order, and closes them if it's the last one. */
    public void send(UUID orderId, StatusMessage message) {
        sendToAll(orderId, "status", message, message.last());
    }

}
