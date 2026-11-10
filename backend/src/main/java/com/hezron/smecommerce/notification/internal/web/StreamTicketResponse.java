package com.hezron.smecommerce.notification.internal.web;

import com.hezron.smecommerce.notification.internal.application.StreamTicketService.IssuedTicket;

/**
 * JSON answer to a ticket request.
 * <pre>{"ticket": "...", "streamUrl": "/api/store/order-stream?ticket=...", "expiresInSeconds": 60}</pre>
 * The page opens {@code new EventSource(streamUrl)} straight away; the ticket works once.
 */
record StreamTicketResponse(String ticket, String streamUrl, long expiresInSeconds) {

    static StreamTicketResponse fromResult(IssuedTicket issued) {
        // base64url tickets only use letters, digits, - and _, so they need no escaping in a URL.
        return new StreamTicketResponse(issued.ticket(), "/api/store/order-stream?ticket=" + issued.ticket(),
                issued.expiresInSeconds());
    }

}
