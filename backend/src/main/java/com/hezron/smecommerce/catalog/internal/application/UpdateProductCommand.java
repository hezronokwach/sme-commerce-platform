package com.hezron.smecommerce.catalog.internal.application;

import com.hezron.smecommerce.catalog.internal.domain.ProductStatus;
import java.util.UUID;

/**
 * Input for editing a product. All fields are replaced (like a form being saved).
 *
 * @param expectedVersion the {@code version} the caller loaded. If the product changed since, the
 *                        update is refused, so nobody overwrites a change they never saw.
 * @param categoryId      null for "no category"
 * @param status          DRAFT or ACTIVE (archive with delete instead)
 */
public record UpdateProductCommand(int expectedVersion, UUID categoryId, String name, String description,
        long priceMinor, String imageUrl, ProductStatus status) {
}
