package com.hezron.smecommerce.payments.internal.persistence;

import com.hezron.smecommerce.payments.PaymentProviderName;
import com.hezron.smecommerce.payments.internal.domain.PaymentStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<PaymentEntity, UUID> {

    Optional<PaymentEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    /** Is there already a live attempt (INITIATED, PENDING, UNKNOWN) for this order? */
    boolean existsByTenantIdAndOrderIdAndStatusIn(UUID tenantId, UUID orderId, Collection<PaymentStatus> statuses);

    /** Every attempt for one order, oldest first. */
    List<PaymentEntity> findAllByTenantIdAndOrderIdOrderByCreatedAtAsc(UUID tenantId, UUID orderId);

    /**
     * The payment a callback is about, LOCKED until the transaction ends (SELECT ... FOR UPDATE).
     * <p>
     * A second copy of the same callback, arriving at the same moment on another thread, waits here
     * until the first one has committed; it then sees the payment already final and does nothing.
     * <p>
     * The one query without a tenant: a callback comes from the provider, not from a logged-in shop,
     * so we only learn the shop from the payment row this returns. The reference is unique per
     * provider (uq_payments_provider_reference), so it can only ever match one shop's payment.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PaymentEntity> findForUpdateByProviderAndProviderReference(PaymentProviderName provider,
            String providerReference);

    /**
     * One payment, LOCKED until the transaction ends. For reconciliation: an answer found by asking the
     * provider is applied under the same lock as a callback, so the two can never both apply.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PaymentEntity p WHERE p.id = :id")
    Optional<PaymentEntity> lockById(@Param("id") UUID id);

    /**
     * Payments whose next check is due, oldest due first, for the reconciliation job. Like the callback
     * lookup this spans every shop: it's the system's own job, not a shop's request. Each row carries
     * its tenant_id, and everything done with it afterwards uses that.
     */
    @Query("""
            SELECT p FROM PaymentEntity p
            WHERE p.status IN :statuses AND p.nextCheckAt IS NOT NULL AND p.nextCheckAt <= :now
            ORDER BY p.nextCheckAt
            """)
    List<PaymentEntity> findDueForCheck(@Param("statuses") Collection<PaymentStatus> statuses,
            @Param("now") Instant now, Pageable page);

}
