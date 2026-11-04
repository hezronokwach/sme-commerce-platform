package com.hezron.smecommerce.catalog.internal.domain;

/** Where a product is in its life. Only ACTIVE products show on the storefront. */
public enum ProductStatus {

    /** Being prepared; only the shop's staff see it. */
    DRAFT,

    /** On sale. */
    ACTIVE,

    /** Off sale for good. Kept, never deleted, because past orders point at it. */
    ARCHIVED

}
