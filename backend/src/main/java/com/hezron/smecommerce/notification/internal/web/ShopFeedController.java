package com.hezron.smecommerce.notification.internal.web;

import com.hezron.smecommerce.notification.internal.application.ShopFeedService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * The admin's live order feed. The browser's EventSource can't send the Authorization header, so this
 * one admin endpoint is let through without a login (see SecurityConfig): the one-time SHOP ticket,
 * which only a logged-in owner or staff member can get, proves who it is instead.
 */
@RestController
class ShopFeedController {

    private final ShopFeedService feedService;

    ShopFeedController(ShopFeedService feedService) {
        this.feedService = feedService;
    }

    /** {@code GET /api/admin/order-feed?ticket=...} */
    @GetMapping(path = "/api/admin/order-feed", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter feed(@RequestParam String ticket) {
        return feedService.open(ticket);
    }

}
