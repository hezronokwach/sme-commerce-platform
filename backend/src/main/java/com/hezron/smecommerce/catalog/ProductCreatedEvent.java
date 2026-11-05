package com.hezron.smecommerce.catalog;

import com.hezron.smecommerce.shared.TenantId;
import java.util.UUID;

/**
 * A shop added a product (workflow W3). Inventory will listen to create its stock record (0 in stock),
 * and the assistant to learn the description. Nobody listens yet.
 * <p>
 * Public because other modules listen to it. It carries plain values only, never the entity.
 */
public record ProductCreatedEvent(TenantId tenantId, UUID productId, String name, String description) {
}
