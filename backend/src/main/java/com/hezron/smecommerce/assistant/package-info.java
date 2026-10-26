/**
 * Assistant module: Tenant documents, embeddings and question answering.
 * Listens to catalog events.
 * <p>
 * Types in this package are the module's public API. Everything in sub-packages
 * (for example {@code internal}) is private to the module.
 */
@ApplicationModule(
        displayName = "Assistant",
        allowedDependencies = { "catalog", "shared", "audit" }
)
package com.hezron.smecommerce.assistant;

import org.springframework.modulith.ApplicationModule;
