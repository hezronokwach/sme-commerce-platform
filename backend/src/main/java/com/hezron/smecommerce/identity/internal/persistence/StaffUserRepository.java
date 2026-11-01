package com.hezron.smecommerce.identity.internal.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Callers must pass emails already lowercased.
 * <p>
 * Shop rule: anything a shop's staff can see is looked up with {@code ...TenantId}, so the query itself
 * says {@code WHERE tenant_id = ?}. Don't use the built-in {@code findById} or {@code findAll} for that:
 * they cover every shop. Only sign-up, login and refresh look users up across shops (by email or id),
 * because that is how they find out which shop the user belongs to.
 */
public interface StaffUserRepository extends JpaRepository<StaffUserEntity, UUID> {

    boolean existsByEmail(String email);

    Optional<StaffUserEntity> findByEmail(String email);

    /** {@code SELECT ... WHERE tenant_id = ? ORDER BY created_at}: one shop's staff, oldest first. */
    List<StaffUserEntity> findAllByTenantIdOrderByCreatedAtAsc(UUID tenantId);

    /** {@code SELECT ... WHERE id = ? AND tenant_id = ?}: empty if the user belongs to another shop. */
    Optional<StaffUserEntity> findByIdAndTenantId(UUID id, UUID tenantId);

}
