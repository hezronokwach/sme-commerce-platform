package com.hezron.smecommerce.identity.internal.application;

import com.hezron.smecommerce.shared.TenantId;
import java.util.UUID;

/** What sign-up created. Tokens are added when login and refresh are built. */
public record SignUpResult(UUID userId, TenantId tenantId) {
}
