package com.hezron.smecommerce.catalog.internal.web;

import com.hezron.smecommerce.catalog.internal.application.ProductResult;
import com.hezron.smecommerce.catalog.internal.application.ProductService;
import com.hezron.smecommerce.catalog.internal.domain.ProductStatus;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * A shop's products in the admin (workflow W3). Owners and staff may both manage them (the security
 * chain already requires one of those roles for /api/admin/**).
 */
@RestController
@RequestMapping("/api/admin/products")
class ProductController {

    private final ProductService productService;

    ProductController(ProductService productService) {
        this.productService = productService;
    }

    /** {@code GET /api/admin/products} or {@code GET /api/admin/products?status=ACTIVE}. */
    @GetMapping
    List<ProductResponse> list(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) ProductStatus status) {
        UUID tenantId = CallerTenant.idFrom(jwt);
        List<ProductResult> results = productService.list(tenantId, status);

        List<ProductResponse> responses = new ArrayList<>();
        for (ProductResult result : results) {
            responses.add(ProductResponse.fromResult(result));
        }
        return responses;
    }

    @GetMapping("/{productId}")
    ProductResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID productId) {
        UUID tenantId = CallerTenant.idFrom(jwt);
        ProductResult result = productService.get(tenantId, productId);
        return ProductResponse.fromResult(result);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ProductResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateProductRequest request) {
        UUID tenantId = CallerTenant.idFrom(jwt);
        ProductResult result = productService.create(tenantId, request.toCommand());
        return ProductResponse.fromResult(result);
    }

    @PutMapping("/{productId}")
    ProductResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID productId,
            @Valid @RequestBody UpdateProductRequest request) {
        UUID tenantId = CallerTenant.idFrom(jwt);
        ProductResult result = productService.update(tenantId, productId, request.toCommand());
        return ProductResponse.fromResult(result);
    }

    /** "Delete" archives: the product leaves the shop for good but stays in the database for past orders. */
    @DeleteMapping("/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void archive(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID productId) {
        UUID tenantId = CallerTenant.idFrom(jwt);
        productService.archive(tenantId, productId);
    }

}
