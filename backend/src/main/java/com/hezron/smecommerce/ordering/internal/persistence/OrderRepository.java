package com.hezron.smecommerce.ordering.internal.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Every query names the shop (ADR 0005): don't use the built-in findById / findAll, they cover every shop.
 */
public interface OrderRepository extends JpaRepository<OrderEntity, UUID> {

    /** {@code WHERE id = ? AND tenant_id = ?}: empty if the order belongs to another shop. */
    Optional<OrderEntity> findByIdAndTenantId(UUID id, UUID tenantId);

}
