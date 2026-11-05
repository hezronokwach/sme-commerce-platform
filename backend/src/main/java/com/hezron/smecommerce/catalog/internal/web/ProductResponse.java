package com.hezron.smecommerce.catalog.internal.web;

import com.hezron.smecommerce.catalog.internal.application.ProductResult;
import java.time.Instant;
import java.util.UUID;

/**
 * One product as the admin shows it. {@code version} must be sent back unchanged when editing,
 * so the server can tell whether someone else saved in between.
 */
record ProductResponse(UUID id, UUID categoryId, String name, String slug, String description, long priceMinor,
        String currency, String imageUrl, String status, int version, Instant createdAt, Instant updatedAt) {

    static ProductResponse fromResult(ProductResult result) {
        return new ProductResponse(
                result.id(),
                result.categoryId(),
                result.name(),
                result.slug(),
                result.description(),
                result.priceMinor(),
                result.currency(),
                result.imageUrl(),
                result.status().name(),
                result.version(),
                result.createdAt(),
                result.updatedAt());
    }

}
