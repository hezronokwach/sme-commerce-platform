package com.hezron.smecommerce.identity.internal.application;

import com.hezron.smecommerce.identity.internal.domain.StaffRole;
import com.hezron.smecommerce.identity.internal.domain.StaffStatus;
import com.hezron.smecommerce.identity.internal.persistence.StaffUserEntity;
import java.time.Instant;
import java.util.UUID;

/** What the staff use cases return for one person: the row without the password hash or login counters. */
public record StaffResult(UUID id, String fullName, String email, StaffRole role, StaffStatus status,
        Instant lastLoginAt) {

    static StaffResult fromEntity(StaffUserEntity entity) {
        return new StaffResult(entity.getId(), entity.getFullName(), entity.getEmail(), entity.getRole(),
                entity.getStatus(), entity.getLastLoginAt());
    }

}
