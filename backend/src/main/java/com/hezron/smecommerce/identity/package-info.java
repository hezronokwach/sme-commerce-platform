/**
 * Identity module: Staff users, customers, authentication and tokens.
 * <p>
 * Types in this package are the module's public API. Everything in sub-packages
 * (for example {@code internal}) is private to the module.
 */
@ApplicationModule(
        displayName = "Identity",
        allowedDependencies = { "tenant", "shared", "audit" }
)
package com.hezron.smecommerce.identity;

import org.springframework.modulith.ApplicationModule;
