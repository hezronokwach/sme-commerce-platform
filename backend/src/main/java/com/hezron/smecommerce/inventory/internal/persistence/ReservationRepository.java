package com.hezron.smecommerce.inventory.internal.persistence;

import com.hezron.smecommerce.inventory.internal.domain.ReservationStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Every query names the shop (ADR 0005). */
public interface ReservationRepository extends JpaRepository<ReservationEntity, UUID> {

    /** {@code WHERE order_id = ? AND tenant_id = ? AND status = ?}: e.g. an order's holds that are still HELD. */
    List<ReservationEntity> findAllByOrderIdAndTenantIdAndStatus(UUID orderId, UUID tenantId,
            ReservationStatus status);

}
