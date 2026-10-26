/**
 * Tenant module: Shops, branding and per-shop payment settings.
 * <p>
 * Types in this package are the module's public API. Everything in sub-packages
 * (for example {@code internal}) is private to the module.
 */
@ApplicationModule(
        displayName = "Tenant",
        allowedDependencies = { "shared", "audit" }
)
package com.hezron.smecommerce.tenant;

import org.springframework.modulith.ApplicationModule;
