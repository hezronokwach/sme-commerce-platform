/**
 * Shared kernel: small value types every module may use, such as {@code Money},
 * {@code TenantId}, {@code PhoneNumber}, the domain event base type and the tenant context.
 * <p>
 * Rules: no business logic, no Spring beans that belong to a feature, and no dependency on
 * any other module. If something here starts making decisions, it belongs in a module.
 * <p>
 * The module is {@link Type#OPEN open} so its types can be grouped into sub-packages
 * (for example {@code shared.money}) and still be visible to every module.
 */
@ApplicationModule(
        displayName = "Shared Kernel",
        type = Type.OPEN,
        allowedDependencies = {}
)
package com.hezron.smecommerce.shared;

import org.springframework.modulith.ApplicationModule;
import org.springframework.modulith.ApplicationModule.Type;
