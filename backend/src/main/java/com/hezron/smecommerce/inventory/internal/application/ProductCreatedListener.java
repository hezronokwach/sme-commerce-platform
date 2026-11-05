package com.hezron.smecommerce.inventory.internal.application;

import com.hezron.smecommerce.catalog.ProductCreatedEvent;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Inventory's side of workflow W3: when catalog announces a new product, create its stock record.
 * <p>
 * Catalog doesn't know inventory exists; it only publishes ProductCreatedEvent. Inventory listens. So
 * the dependency points one way (inventory → catalog's public event), which package-info allows.
 * <p>
 * {@code @ApplicationModuleListener} is Spring Modulith's listener annotation. It means three things:
 * <ul>
 *   <li>Run only after catalog's transaction has committed, so the product really exists.</li>
 *   <li>Run in a new transaction of our own, so a problem here can never undo the product.</li>
 *   <li>Run on another thread (asynchronously), so creating a product doesn't wait for inventory.</li>
 * </ul>
 * Until it finishes, the event waits in the event_publication table (V95), so it isn't lost.
 */
@Component
class ProductCreatedListener {

    private final StockService stockService;

    ProductCreatedListener(StockService stockService) {
        this.stockService = stockService;
    }

    @ApplicationModuleListener
    void on(ProductCreatedEvent event) {
        stockService.createStockItemForProduct(event.tenantId().value(), event.productId());
    }

}
