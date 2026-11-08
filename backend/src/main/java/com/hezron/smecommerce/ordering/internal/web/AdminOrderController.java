package com.hezron.smecommerce.ordering.internal.web;

import com.hezron.smecommerce.ordering.internal.application.OrderDetailResult;
import com.hezron.smecommerce.ordering.internal.application.OrderPageResult;
import com.hezron.smecommerce.ordering.internal.application.OrderQueryService;
import com.hezron.smecommerce.ordering.internal.domain.OrderStatus;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * A shop's orders in the admin (Guide 4.5). Owners and staff may both see them (the security chain
 * already requires one of those roles for /api/admin/**).
 */
@RestController
@RequestMapping("/api/admin/orders")
class AdminOrderController {

    private final OrderQueryService orderQueryService;

    AdminOrderController(OrderQueryService orderQueryService) {
        this.orderQueryService = orderQueryService;
    }

    /** {@code GET /api/admin/orders?status=PENDING_PAYMENT&page=0&size=20}; every parameter is optional. */
    @GetMapping
    OrderPageResponse list(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID tenantId = CallerTenant.idFrom(jwt);
        OrderPageResult result = orderQueryService.list(tenantId, status, page, size);
        return OrderPageResponse.fromResult(result);
    }

    @GetMapping("/{orderId}")
    OrderDetailResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderId) {
        UUID tenantId = CallerTenant.idFrom(jwt);
        OrderDetailResult result = orderQueryService.get(tenantId, orderId);
        return OrderDetailResponse.fromResult(result);
    }

}
