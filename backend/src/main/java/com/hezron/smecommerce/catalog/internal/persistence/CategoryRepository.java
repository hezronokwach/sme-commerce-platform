package com.hezron.smecommerce.catalog.internal.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Every query names the shop (ADR 0005): don't use the built-in findById / findAll, they cover every shop.
 */
public interface CategoryRepository extends JpaRepository<CategoryEntity, UUID> {

    /** {@code WHERE tenant_id = ? ORDER BY sort_order, name}: the storefront menu order. */
    List<CategoryEntity> findAllByTenantIdOrderBySortOrderAscNameAsc(UUID tenantId);

    /** {@code WHERE id = ? AND tenant_id = ?}: empty if the category belongs to another shop. */
    Optional<CategoryEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    /** Is the web name already used in this shop? */
    boolean existsByTenantIdAndSlug(UUID tenantId, String slug);

    /** Same, but ignoring one category: renaming a category to its own current slug is fine. */
    boolean existsByTenantIdAndSlugAndIdNot(UUID tenantId, String slug, UUID id);

}
