package com.hezron.smecommerce.tenant.internal.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRepository extends JpaRepository<TenantEntity, UUID> {

    boolean existsBySlug(String slug);

    /** {@code SELECT ... WHERE slug = ?}. The unique index on slug makes it a single-row lookup. */
    Optional<TenantEntity> findBySlug(String slug);

}
