/**
 * Ordering module: Orders, order lines, order state and order numbers.
 * Conducts checkout: calls catalog, inventory, identity, tenant and payments;
 * payment results come back as events.
 * <p>
 * Types in this package are the module's public API. Everything in sub-packages
 * (for example {@code internal}) is private to the module.
 */
@ApplicationModule(
        displayName = "Ordering",
        allowedDependencies = { "catalog", "inventory", "identity", "tenant", "payments", "shared", "audit" }
)
package com.hezron.smecommerce.ordering;

import org.springframework.modulith.ApplicationModule;
