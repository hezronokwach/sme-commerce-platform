package com.hezron.smecommerce.payments.internal.persistence;

import com.hezron.smecommerce.payments.PaymentProviderName;
import com.hezron.smecommerce.payments.internal.domain.PaymentStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

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

}
