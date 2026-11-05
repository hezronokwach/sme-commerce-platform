package com.hezron.smecommerce.inventory.internal.web;

import com.hezron.smecommerce.inventory.internal.application.StockResult;
import com.hezron.smecommerce.inventory.internal.application.StockService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A shop's stock in the admin (Guide 3.3, workflow W3 step 6). Owners and staff may both restock (the
 * security chain already requires one of those roles for /api/admin/**).
 */
@RestController
@RequestMapping("/api/admin/stock")
class StockController {

    private final StockService stockService;

    StockController(StockService stockService) {
        this.stockService = stockService;
    }

    @GetMapping("/{productId}")
    StockResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID productId) {
        UUID tenantId = CallerOfRequest.tenantIdFrom(jwt);
        StockResult result = stockService.get(tenantId, productId);
        return StockResponse.fromResult(result);
    }

    /** Adds or removes stock; the change is also written to the stock history with who did it. */
    @PostMapping("/{productId}/adjustments")
    StockResponse adjust(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID productId,
            @Valid @RequestBody AdjustStockRequest request) {
        UUID tenantId = CallerOfRequest.tenantIdFrom(jwt);
        UUID actorId = CallerOfRequest.userIdFrom(jwt);
        StockResult result = stockService.adjust(tenantId, productId, request.toCommand(actorId));
        return StockResponse.fromResult(result);
    }

}
