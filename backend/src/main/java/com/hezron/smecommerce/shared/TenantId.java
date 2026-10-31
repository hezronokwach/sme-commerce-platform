package com.hezron.smecommerce.shared;

import java.util.Objects;
import java.util.UUID;

/**
 * The id of a shop. Every tenant-owned row and every event carries one.
 * <p>
 * A dedicated type instead of a bare {@link UUID} means a method like
 * {@code findProduct(TenantId, UUID productId)} can't be called with the two ids swapped.
 */
public record TenantId(UUID value) {

    public TenantId {
        Objects.requireNonNull(value, "TenantId value must not be null");
    }

    public static TenantId of(UUID value) {
        return new TenantId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }

}
