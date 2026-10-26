/**
 * Inventory module: Stock levels, reservations and stock history.
 * Listens to catalog events.
 * <p>
 * Types in this package are the module's public API. Everything in sub-packages
 * (for example {@code internal}) is private to the module.
 */
@ApplicationModule(
        displayName = "Inventory",
        allowedDependencies = { "catalog", "shared", "audit" }
)
package com.hezron.smecommerce.inventory;

import org.springframework.modulith.ApplicationModule;
