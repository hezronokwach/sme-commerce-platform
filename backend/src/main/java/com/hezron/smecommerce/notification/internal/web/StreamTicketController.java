package com.hezron.smecommerce.notification.internal.web;

import com.hezron.smecommerce.notification.internal.application.StreamTicketService;
import com.hezron.smecommerce.notification.internal.application.StreamTicketService.IssuedTicket;
import com.hezron.smecommerce.shared.AccessTokenClaims;
import com.hezron.smecommerce.shared.TenantId;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Hands out one-time tickets for the live order-status stream (StreamController).
 * <ul>
 *   <li>Customers: no login, so they prove the order is theirs with the private order link.
 *       /api/store/** is open in the security chain.</li>
 *   <li>Staff: /api/admin/** needs a staff login; the shop comes from the token, never the URL.</li>
 * </ul>
 */
@RestController
class StreamTicketController {

    private final StreamTicketService tickets;

    StreamTicketController(StreamTicketService tickets) {
        this.tickets = tickets;
    }

    /** {@code POST /api/store/{slug}/orders/{orderNumber}/stream-ticket} with {"orderToken": "..."} */
    @PostMapping("/api/store/{slug}/orders/{orderNumber}/stream-ticket")
    StreamTicketResponse forCustomer(@PathVariable String slug, @PathVariable String orderNumber,
            @Valid @RequestBody CustomerStreamTicketRequest request) {
        IssuedTicket issued = tickets.issueForCustomer(slug, orderNumber, request.orderToken());
        return StreamTicketResponse.fromResult(issued);
    }

    /** {@code POST /api/admin/orders/{orderId}/stream-ticket}: owners and staff of the order's shop. */
    @PostMapping("/api/admin/orders/{orderId}/stream-ticket")
    StreamTicketResponse forStaff(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderId) {
        TenantId tenantId = TenantId.of(UUID.fromString(jwt.getClaimAsString(AccessTokenClaims.TENANT_ID)));
        IssuedTicket issued = tickets.issueForStaff(tenantId, orderId);
        return StreamTicketResponse.fromResult(issued);
    }

}
