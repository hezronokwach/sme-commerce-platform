package com.hezron.smecommerce.catalog;

import com.hezron.smecommerce.shared.TenantId;
import java.util.UUID;

/**
 * A shop edited or archived a product. The assistant will listen to keep its knowledge up to date.
 * <p>
 * {@code status} is "DRAFT", "ACTIVE" or "ARCHIVED" as text: the status enum is internal to catalog,
 * and other modules must not depend on internal types.
 */
public record ProductChangedEvent(TenantId tenantId, UUID productId, String name, String description,
        String status) {
}
