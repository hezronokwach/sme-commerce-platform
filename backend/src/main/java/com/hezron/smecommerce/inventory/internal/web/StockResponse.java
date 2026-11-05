package com.hezron.smecommerce.inventory.internal.web;

import com.hezron.smecommerce.inventory.internal.application.StockResult;
import java.util.UUID;

/** One product's stock as the admin shows it. {@code available} = onHand minus reserved. */
record StockResponse(UUID productId, int onHand, int reserved, int available, int lowStockThreshold) {

    static StockResponse fromResult(StockResult result) {
        return new StockResponse(result.productId(), result.onHand(), result.reserved(), result.available(),
                result.lowStockThreshold());
    }

}
