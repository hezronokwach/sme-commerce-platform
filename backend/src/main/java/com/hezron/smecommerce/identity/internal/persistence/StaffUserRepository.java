package com.hezron.smecommerce.identity.internal.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffUserRepository extends JpaRepository<StaffUserEntity, UUID> {

    /** Callers must pass the email already lowercased. */
    boolean existsByEmail(String email);

}
