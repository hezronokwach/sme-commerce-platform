package com.hezron.smecommerce.inventory.internal.web;

import com.hezron.smecommerce.inventory.internal.application.AdjustStockCommand;
import com.hezron.smecommerce.inventory.internal.domain.MovementReason;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * JSON body of {@code POST /api/admin/stock/{productId}/adjustments}:
 *
 * <pre>{"change": 40, "reason": "RESTOCK", "note": "Delivery from Limuru"}</pre>
 *
 * A negative change removes stock. A change of 0 is refused (it would add an empty history row).
 */
record AdjustStockRequest(
        @NotNull @Min(-100_000) @Max(100_000) Integer change,
        @NotNull MovementReason reason,
        @Size(max = 60) String note) {

    AdjustStockCommand toCommand(UUID actorId) {
        return new AdjustStockCommand(change, reason, note, actorId);
    }

}
