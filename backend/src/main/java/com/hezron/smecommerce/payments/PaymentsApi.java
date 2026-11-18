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
     * @throws PaymentInProgressException       if the order already has a live attempt
     * @throws PaymentMethodNotOfferedException if the shop doesn't offer this way of paying
     */
    UUID requestPayment(RequestPaymentCommand command);

    /** The order's newest payment attempt, or empty if none was made yet (or it's another shop's order). */
    Optional<PaymentStatusView> findLatestPayment(TenantId tenantId, UUID orderId);

    /**
     * What the customer's browser must do to finish the order's newest payment, or empty if nothing
     * (M-Pesa, a finished payment, a card payment not yet created at Stripe). May ask the provider over
     * HTTP, so it is only for the customer's order page, never for frequent lookups.
     */
    Optional<PaymentNextAction> findNextAction(TenantId tenantId, UUID orderId);

}
