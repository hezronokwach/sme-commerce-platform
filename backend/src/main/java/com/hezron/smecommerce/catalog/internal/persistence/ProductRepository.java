package com.hezron.smecommerce.catalog.internal.persistence;

import com.hezron.smecommerce.catalog.internal.domain.ProductStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Every query names the shop (ADR 0005): don't use the built-in findById / findAll, they cover every shop.
 */
public interface ProductRepository extends JpaRepository<ProductEntity, UUID> {

    /** {@code WHERE tenant_id = ? ORDER BY created_at DESC}: newest first. */
    List<ProductEntity> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    /** {@code WHERE tenant_id = ? AND status = ? ORDER BY created_at DESC}. */
    List<ProductEntity> findAllByTenantIdAndStatusOrderByCreatedAtDesc(UUID tenantId, ProductStatus status);

    /** {@code WHERE tenant_id = ? AND status = ? AND category_id = ? ORDER BY created_at DESC}. */
    List<ProductEntity> findAllByTenantIdAndStatusAndCategoryIdOrderByCreatedAtDesc(UUID tenantId,
            ProductStatus status, UUID categoryId);

    /** {@code WHERE tenant_id = ? AND slug = ? AND status = ?}: one product page on the storefront. */
    Optional<ProductEntity> findByTenantIdAndSlugAndStatus(UUID tenantId, String slug, ProductStatus status);

    /** {@code WHERE id = ? AND tenant_id = ?}: empty if the product belongs to another shop. */
    Optional<ProductEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    /** {@code WHERE tenant_id = ? AND id IN (...)}: several products of one shop in one query. */
    List<ProductEntity> findAllByTenantIdAndIdIn(UUID tenantId, List<UUID> ids);

    /** Is this web name already used by a product in this shop? */
    boolean existsByTenantIdAndSlug(UUID tenantId, String slug);

    /** Is any product (archived ones too) still in this category? Checked before deleting it. */
    boolean existsByTenantIdAndCategoryId(UUID tenantId, UUID categoryId);

}
