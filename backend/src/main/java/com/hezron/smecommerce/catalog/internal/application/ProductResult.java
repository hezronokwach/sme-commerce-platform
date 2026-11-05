package com.hezron.smecommerce.catalog.internal.application;

import com.hezron.smecommerce.catalog.internal.domain.ProductStatus;
import com.hezron.smecommerce.catalog.internal.persistence.ProductEntity;
import java.time.Instant;
import java.util.UUID;

/** What the product use cases return for one product. */
public record ProductResult(UUID id, UUID categoryId, String name, String slug, String description,
        long priceMinor, String currency, String imageUrl, ProductStatus status, int version,
        Instant createdAt, Instant updatedAt) {

    static ProductResult fromEntity(ProductEntity entity) {
        return new ProductResult(
                entity.getId(),
                entity.getCategoryId(),
                entity.getName(),
                entity.getSlug(),
                entity.getDescription(),
                entity.getPriceMinor(),
                entity.getCurrency(),
                entity.getImageUrl(),
                entity.getStatus(),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

}
