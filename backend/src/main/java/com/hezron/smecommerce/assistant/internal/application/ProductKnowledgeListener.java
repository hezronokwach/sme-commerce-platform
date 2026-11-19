package com.hezron.smecommerce.assistant.internal.application;

import com.hezron.smecommerce.catalog.ProductChangedEvent;
import com.hezron.smecommerce.catalog.ProductCreatedEvent;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * The assistant's side of catalog's product events. Catalog doesn't know the assistant exists; it only
 * announces its products, and the assistant listens (assistant -> catalog is allowed in package-info).
 * <p>
 * {@code @ApplicationModuleListener}: after catalog's transaction commits, on another thread, in a
 * transaction of our own. That transaction only saves the text; the slow embedding happens after it, in
 * DocumentIndexer. Running twice is harmless: the same text is recognised by its hash and skipped.
 */
@Component
class ProductKnowledgeListener {

    private final ProductKnowledge knowledge;

    ProductKnowledgeListener(ProductKnowledge knowledge) {
        this.knowledge = knowledge;
    }

    @ApplicationModuleListener
    void on(ProductCreatedEvent event) {
        knowledge.remember(event.tenantId().value(), event.productId(), event.name(), event.description(),
                event.price(), event.status());
    }

    @ApplicationModuleListener
    void on(ProductChangedEvent event) {
        knowledge.remember(event.tenantId().value(), event.productId(), event.name(), event.description(),
                event.price(), event.status());
    }

}
