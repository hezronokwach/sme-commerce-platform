package com.hezron.smecommerce.inventory.internal.application;

import com.hezron.smecommerce.inventory.internal.domain.MovementReason;
import java.util.UUID;

/**
 * Input for changing what is in the shop by hand.
 *
 * @param change  +40 when a delivery arrives, -3 after counting the shelf; never 0
 * @param reason  RESTOCK, ADJUSTMENT or RETURN (SALE is only for paid orders)
 * @param note    optional, kept in the history, e.g. "Delivery from Limuru"
 * @param actorId the staff user doing it, from the token
 */
public record AdjustStockCommand(int change, MovementReason reason, String note, UUID actorId) {
}
