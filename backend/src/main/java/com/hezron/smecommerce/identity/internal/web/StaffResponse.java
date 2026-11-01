package com.hezron.smecommerce.identity.internal.web;

import com.hezron.smecommerce.identity.internal.application.StaffResult;
import java.time.Instant;
import java.util.UUID;

/** One staff member as the admin shows it. No tenant id: the caller already knows their own shop. */
record StaffResponse(UUID id, String fullName, String email, String role, String status, Instant lastLoginAt) {

    static StaffResponse fromResult(StaffResult result) {
        return new StaffResponse(result.id(), result.fullName(), result.email(), result.role().name(),
                result.status().name(), result.lastLoginAt());
    }

}
