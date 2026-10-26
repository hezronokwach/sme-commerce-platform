/**
 * Catalog module: Products, categories and prices.
 * <p>
 * Types in this package are the module's public API. Everything in sub-packages
 * (for example {@code internal}) is private to the module.
 */
@ApplicationModule(
        displayName = "Catalog",
        allowedDependencies = { "shared", "audit" }
)
package com.hezron.smecommerce.catalog;

import org.springframework.modulith.ApplicationModule;
