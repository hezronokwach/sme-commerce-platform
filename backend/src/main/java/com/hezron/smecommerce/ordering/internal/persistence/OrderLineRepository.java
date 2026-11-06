package com.hezron.smecommerce.ordering.internal.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Every query names the shop (ADR 0005). */
public interface OrderLineRepository extends JpaRepository<OrderLineEntity, UUID> {

    /** {@code WHERE order_id = ? AND tenant_id = ?}: one order's lines. */
    List<OrderLineEntity> findAllByOrderIdAndTenantId(UUID orderId, UUID tenantId);

}
