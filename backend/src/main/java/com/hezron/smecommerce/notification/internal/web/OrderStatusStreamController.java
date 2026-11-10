package com.hezron.smecommerce.notification.internal.web;

import com.hezron.smecommerce.notification.internal.application.OrderStatusStreamService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * The live order-status stream (Server-Sent Events). In the browser:
 * <pre>
 *   const stream = new EventSource(streamUrl);   // streamUrl from the ticket request
 *   stream.addEventListener("status", function (e) { show(JSON.parse(e.data)); });
 * </pre>
 * The response stays open: the server writes one "status" message per change, and closes it once the
 * order is final. No login here (EventSource can't send one); the one-time ticket is the proof.
 */
@RestController
class OrderStatusStreamController {

    private final OrderStatusStreamService streamService;

    OrderStatusStreamController(OrderStatusStreamService streamService) {
        this.streamService = streamService;
    }

    /** {@code GET /api/store/order-stream?ticket=...} */
    @GetMapping(path = "/api/store/order-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter stream(@RequestParam String ticket) {
        return streamService.open(ticket);
    }

}
