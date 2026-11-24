package com.hezron.smecommerce.catalog;

import com.hezron.smecommerce.shared.Money;
import java.util.UUID;

/** A product the shop sells right now, as other modules may see it (no entity, no internal types). */
public record ActiveProductView(UUID productId, String name, String description, Money price) {
}
