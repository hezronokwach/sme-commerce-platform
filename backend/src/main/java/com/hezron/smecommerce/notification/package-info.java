/**
 * Notification module: Live order and payment status streams.
 * Listens to ordering and payments events; has no public API.
 * <p>
 * Types in this package are the module's public API. Everything in sub-packages
 * (for example {@code internal}) is private to the module.
 */
@ApplicationModule(
        displayName = "Notification",
        allowedDependencies = { "ordering", "payments", "shared", "audit" }
)
package com.hezron.smecommerce.notification;

import org.springframework.modulith.ApplicationModule;
