package com.hezron.smecommerce.ordering.internal.web;

import com.hezron.smecommerce.ordering.internal.application.CustomerOrderResult;
import com.hezron.smecommerce.ordering.internal.application.CustomerOrderService;
import com.hezron.smecommerce.ordering.internal.application.PlaceOrderResult;
import com.hezron.smecommerce.ordering.internal.application.PlaceOrderService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Checkout and the order page on the storefront (workflows W6 and W7). Public, no login: customers order
 * as guests. The shop comes from the web name in the URL; the security chain leaves /api/store/** open.
 * After ordering, the private order-link token proves an order is the customer's own. It never goes in
 * a URL (ADR 0010): a header for reading, the body for "try again".
 */
@RestController
@RequestMapping("/api/store/{slug}/orders")
class StorefrontOrderController {

    /** Header that carries the private order link when the customer reads their order. */
    static final String ORDER_TOKEN_HEADER = "X-Order-Token";

    private final PlaceOrderService placeOrderService;
    private final CustomerOrderService customerOrderService;

    StorefrontOrderController(PlaceOrderService placeOrderService, CustomerOrderService customerOrderService) {
        this.placeOrderService = placeOrderService;
        this.customerOrderService = customerOrderService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    PlaceOrderResponse place(@PathVariable String slug, @Valid @RequestBody PlaceOrderRequest request) {
        PlaceOrderResult result = placeOrderService.placeOrder(slug, request.toCommand());
        return PlaceOrderResponse.fromResult(result);
    }

    /**
     * {@code GET /api/store/mamamboga/orders/MM-000123} with header {@code X-Order-Token}. A wrong or
     * missing token gives the same 404 as an unknown order. "no-store": no browser or proxy keeps a copy
     * of someone's order.
     */
    @GetMapping("/{orderNumber}")
    ResponseEntity<CustomerOrderResponse> get(@PathVariable String slug, @PathVariable String orderNumber,
            @RequestHeader(name = ORDER_TOKEN_HEADER, required = false) String orderToken) {
        CustomerOrderResult result = customerOrderService.getOrder(slug, orderNumber, orderToken);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(CustomerOrderResponse.fromResult(result));
    }

    /**
     * "Try again" after a failed payment. 202 Accepted: the attempt is saved; the PIN prompt follows, and
     * how it ends arrives on the order's live stream.
     */
    @PostMapping("/{orderNumber}/payment-attempts")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void tryPaymentAgain(@PathVariable String slug, @PathVariable String orderNumber,
            @Valid @RequestBody TryPaymentAgainRequest request) {
        customerOrderService.tryPaymentAgain(slug, orderNumber, request.orderToken());
    }

}
