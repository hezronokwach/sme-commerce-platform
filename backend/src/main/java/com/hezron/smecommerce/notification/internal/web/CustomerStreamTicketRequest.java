package com.hezron.smecommerce.notification.internal.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * JSON body of a customer's ticket request: the private order-link token they got when ordering.
 * <p>
 * In the body, not the URL: web servers and proxies write URLs to their logs, and this token is the
 * customer's long-lived key to their order. (The ticket can go in a URL because it dies in 60 s.)
 */
record CustomerStreamTicketRequest(@NotBlank @Size(max = 100) String orderToken) {
}
