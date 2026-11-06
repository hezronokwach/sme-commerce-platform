package com.hezron.smecommerce.ordering.internal.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Only ever add rows. Every query names the shop (ADR 0005). */
public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistoryEntity, UUID> {

    /** One order's status changes, oldest first. */
    List<OrderStatusHistoryEntity> findAllByOrderIdAndTenantIdOrderByCreatedAtAsc(UUID orderId, UUID tenantId);

}
