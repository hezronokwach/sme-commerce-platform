/**
 * Audit module: Who changed what, and when, for sensitive actions.
 * <p>
 * Types in this package are the module's public API. Everything in sub-packages
 * (for example {@code internal}) is private to the module.
 */
@ApplicationModule(
        displayName = "Audit",
        allowedDependencies = { "shared" }
)
package com.hezron.smecommerce.audit;

import org.springframework.modulith.ApplicationModule;
