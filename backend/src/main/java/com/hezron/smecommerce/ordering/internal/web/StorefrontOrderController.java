package com.hezron.smecommerce.ordering.internal.web;

import com.hezron.smecommerce.ordering.internal.application.PlaceOrderResult;
import com.hezron.smecommerce.ordering.internal.application.PlaceOrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Checkout on the storefront (workflow W6). Public, no login: customers order as guests. The shop comes
 * from the web name in the URL; the security chain leaves /api/store/** open.
 */
@RestController
@RequestMapping("/api/store/{slug}/orders")
class StorefrontOrderController {

    private final PlaceOrderService placeOrderService;

    StorefrontOrderController(PlaceOrderService placeOrderService) {
        this.placeOrderService = placeOrderService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    PlaceOrderResponse place(@PathVariable String slug, @Valid @RequestBody PlaceOrderRequest request) {
        PlaceOrderResult result = placeOrderService.placeOrder(slug, request.toCommand());
        return PlaceOrderResponse.fromResult(result);
    }

}
