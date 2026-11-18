package com.hezron.smecommerce.tenant.internal.persistence;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Every query names the shop (ADR 0005). */
public interface PaymentMethodRepository extends JpaRepository<PaymentMethodEntity, UUID> {

    /** {@code WHERE tenant_id = ? ORDER BY provider}. */
    List<PaymentMethodEntity> findAllByTenantIdOrderByProviderAsc(UUID tenantId);

    /**
     * The shop's rows, locked until the transaction ends ({@code SELECT ... FOR UPDATE}). Used when
     * switching a method: two owners' tabs turning off the last two methods at the same moment then
     * run one after the other, so the second one sees that only one is left and is refused.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM PaymentMethodEntity m WHERE m.tenantId = :tenantId")
    List<PaymentMethodEntity> lockAllForShop(@Param("tenantId") UUID tenantId);

    /** {@code WHERE tenant_id = ? AND provider = ? AND enabled}. */
    boolean existsByTenantIdAndProviderAndEnabledTrue(UUID tenantId, String provider);

}
