package com.hezron.smecommerce.catalog.internal.web;

import com.hezron.smecommerce.catalog.internal.application.ProductResult;
import java.util.UUID;

/**
 * One product as a customer sees it. Smaller than the admin's {@code ProductResponse}: no status
 * (always ACTIVE here), no version, no timestamps. Customers don't need them, so they aren't sent.
 * <p>
 * The price is for showing only. At checkout, ordering asks catalog for the price again.
 */
record StorefrontProductResponse(UUID id, UUID categoryId, String name, String slug, String description,
        long priceMinor, String currency, String imageUrl) {

    static StorefrontProductResponse fromResult(ProductResult result) {
        return new StorefrontProductResponse(
                result.id(),
                result.categoryId(),
                result.name(),
                result.slug(),
                result.description(),
                result.priceMinor(),
                result.currency(),
                result.imageUrl());
    }

}
