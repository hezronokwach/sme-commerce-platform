/**
 * Payments module: Payments, provider adapters and the callback log.
 * Never depends on ordering: it only announces results as events.
 * <p>
 * Types in this package are the module's public API. Everything in sub-packages
 * (for example {@code internal}) is private to the module.
 */
@ApplicationModule(
        displayName = "Payments",
        allowedDependencies = { "tenant", "shared", "audit" }
)
package com.hezron.smecommerce.payments;

import org.springframework.modulith.ApplicationModule;
