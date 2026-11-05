package com.hezron.smecommerce.catalog.internal.application;

import com.hezron.smecommerce.catalog.internal.domain.ProductStatus;
import java.util.UUID;

/**
 * Input for adding a product.
 *
 * @param categoryId  null for "no category"
 * @param slug        null to build it from the name
 * @param priceMinor  price in cents: KES 50.00 is 5000
 * @param status      DRAFT or ACTIVE; null means ACTIVE
 */
public record CreateProductCommand(UUID categoryId, String name, String slug, String description,
        long priceMinor, String imageUrl, ProductStatus status) {
}
