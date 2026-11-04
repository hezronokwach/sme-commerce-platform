package com.hezron.smecommerce.catalog.internal.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Every query names the shop (ADR 0005): don't use the built-in findById / findAll, they cover every shop.
 * The list and slug queries are added with the product endpoints.
 */
public interface ProductRepository extends JpaRepository<ProductEntity, UUID> {

    /** {@code WHERE id = ? AND tenant_id = ?}: empty if the product belongs to another shop. */
    Optional<ProductEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    /** Is any product (archived ones too) still in this category? Checked before deleting it. */
    boolean existsByTenantIdAndCategoryId(UUID tenantId, UUID categoryId);

}
