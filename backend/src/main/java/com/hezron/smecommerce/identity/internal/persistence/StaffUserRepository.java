package com.hezron.smecommerce.identity.internal.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Callers must pass emails already lowercased. */
public interface StaffUserRepository extends JpaRepository<StaffUserEntity, UUID> {

    boolean existsByEmail(String email);

    Optional<StaffUserEntity> findByEmail(String email);

}
