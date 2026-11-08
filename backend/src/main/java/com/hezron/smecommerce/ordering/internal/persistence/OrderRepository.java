package com.hezron.smecommerce.ordering.internal.persistence;

import com.hezron.smecommerce.ordering.internal.domain.OrderStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Every query names the shop (ADR 0005): don't use the built-in findById / findAll, they cover every shop.
 * <p>
 * The list queries take a Pageable (which page, how many per page, sorted how) and return a Page: that
 * page's orders plus the total count, which Spring Data gets with a second COUNT query.
 */
public interface OrderRepository extends JpaRepository<OrderEntity, UUID> {

    /** {@code WHERE id = ? AND tenant_id = ?}: empty if the order belongs to another shop. */
    Optional<OrderEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    /** One page of the shop's orders. */
    Page<OrderEntity> findAllByTenantId(UUID tenantId, Pageable pageable);

    /** One page of the shop's orders in one status. */
    Page<OrderEntity> findAllByTenantIdAndStatus(UUID tenantId, OrderStatus status, Pageable pageable);

}
