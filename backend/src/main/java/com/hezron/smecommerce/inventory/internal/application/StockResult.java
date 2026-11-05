package com.hezron.smecommerce.inventory.internal.application;

import com.hezron.smecommerce.inventory.internal.persistence.StockItemEntity;
import java.util.UUID;

/** What the stock use cases return for one product. */
public record StockResult(UUID productId, int onHand, int reserved, int available, int lowStockThreshold) {

    static StockResult fromEntity(StockItemEntity entity) {
        return new StockResult(entity.getProductId(), entity.getOnHand(), entity.getReserved(),
                entity.getAvailable(), entity.getLowStockThreshold());
    }

}
