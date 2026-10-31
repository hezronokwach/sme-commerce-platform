package com.hezron.smecommerce.identity.internal.application;

import com.hezron.smecommerce.shared.TenantId;
import java.util.UUID;

/** What sign-up created. The owner is logged in straight away, so tokens come with it. */
public record SignUpResult(UUID userId, TenantId tenantId, AuthTokens tokens) {
}
