package com.hezron.smecommerce.payments;

import com.hezron.smecommerce.shared.TenantId;
import java.util.Optional;
import java.util.UUID;

/**
 * What other modules may ask of payments. Only ordering uses it (CLAUDE.md: ordering -> payments).
 * <p>
 * Payments never calls or listens to ordering. It answers this call, and later announces how the
 * payment ended with PaymentSucceededEvent or PaymentFailedEvent, which ordering listens to.
 */
public interface PaymentsApi {

    /**
     * Records a new payment attempt for an order and returns its id.
     * <p>
     * Runs INSIDE the caller's transaction (Guide 5.2): if placing the order fails afterwards, the
     * payment row disappears with it. The provider (the slow, risky part) is only asked after that
     * transaction has committed.
     *
     * @throws PaymentInProgressException if the order already has a live attempt
     */
    UUID requestPayment(RequestPaymentCommand command);

    /** The order's newest payment attempt, or empty if none was made yet (or it's another shop's order). */
    Optional<PaymentStatusView> findLatestPayment(TenantId tenantId, UUID orderId);

}
