package com.hezron.smecommerce.catalog;

import com.hezron.smecommerce.shared.Money;
import com.hezron.smecommerce.shared.TenantId;
import java.util.UUID;

/**
 * A shop added a product (workflow W3). Inventory listens to create its stock record (0 in stock), and
 * the assistant to learn about it.
 * <p>
 * Public because other modules listen to it. It carries plain values only, never the entity.
 * {@code status} is "DRAFT" or "ACTIVE" as text: the status enum is internal to catalog.
 */
public record ProductCreatedEvent(TenantId tenantId, UUID productId, String name, String description,
        String status, Money price) {
}
