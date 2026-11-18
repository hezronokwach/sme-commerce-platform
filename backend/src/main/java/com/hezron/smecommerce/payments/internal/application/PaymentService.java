package com.hezron.smecommerce.payments.internal.application;

import com.hezron.smecommerce.payments.PaymentInProgressException;
import com.hezron.smecommerce.payments.PaymentMethodNotOfferedException;
import com.hezron.smecommerce.payments.PaymentNextAction;
import com.hezron.smecommerce.payments.PaymentProviderName;
import com.hezron.smecommerce.payments.PaymentStatusView;
import com.hezron.smecommerce.payments.PaymentsApi;
import com.hezron.smecommerce.payments.RequestPaymentCommand;
import com.hezron.smecommerce.payments.internal.card.StripeClient;
import com.hezron.smecommerce.payments.internal.domain.PaymentStatus;
import com.hezron.smecommerce.payments.internal.persistence.PaymentEntity;
import com.hezron.smecommerce.payments.internal.persistence.PaymentRepository;
import com.hezron.smecommerce.shared.TenantId;
import com.hezron.smecommerce.tenant.TenantApi;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Payments' answer to ordering (Guide 5.2, first half): save the attempt, ask the provider later. */
@Service
class PaymentService implements PaymentsApi {

    /** Attempts that are still running: an order may have only one of these at a time. */
    private static final List<PaymentStatus> LIVE = List.of(PaymentStatus.INITIATED, PaymentStatus.PENDING,
            PaymentStatus.UNKNOWN);

    private final PaymentRepository payments;
    private final TenantApi tenantApi;
    private final StripeClient stripe;
    private final ApplicationEventPublisher events;

    PaymentService(PaymentRepository payments, TenantApi tenantApi, StripeClient stripe,
            ApplicationEventPublisher events) {
        this.payments = payments;
        this.tenantApi = tenantApi;
        this.stripe = stripe;
        this.events = events;
    }

    /**
     * {@code @Transactional} joins the caller's transaction (ordering's), so the payment row and the
     * order are saved, or thrown away, together.
     */
    @Override
    @Transactional
    public UUID requestPayment(RequestPaymentCommand command) {
        UUID tenantId = command.tenantId().value();

        // Only a way of paying this shop offers (its owner's choice). Refusing here, inside ordering's
        // transaction, rolls the whole order back, so ordering needs no rule of its own for it.
        if (!tenantApi.isPaymentMethodEnabled(command.tenantId(), command.provider().name())) {
            throw new PaymentMethodNotOfferedException();
        }

        boolean alreadyRunning = payments.existsByTenantIdAndOrderIdAndStatusIn(tenantId, command.orderId(), LIVE);
        if (alreadyRunning) {
            throw new PaymentInProgressException();
        }

        PaymentEntity payment = PaymentEntity.newPayment(tenantId, command.orderId(), command.provider(),
                command.amountMinor(), command.currency(), command.payerPhone());
        try {
            payments.saveAndFlush(payment);
        } catch (DataIntegrityViolationException twoAtOnce) {
            // Two requests passed the check above at the same moment; uq_payments_one_active let only
            // one of them in.
            throw new PaymentInProgressException();
        }

        // Not the provider call itself: that happens after commit, in PaymentRequestedListener.
        events.publishEvent(new PaymentRequestedEvent(tenantId, payment.getId(), command.orderNumber()));
        return payment.getId();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PaymentStatusView> findLatestPayment(TenantId tenantId, UUID orderId) {
        List<PaymentEntity> attempts = payments.findAllByTenantIdAndOrderIdOrderByCreatedAtAsc(tenantId.value(), orderId);
        if (attempts.isEmpty()) {
            return Optional.empty();
        }
        PaymentEntity newest = attempts.get(attempts.size() - 1);
        return Optional.of(new PaymentStatusView(newest.getStatus().name(), newest.getFailureReason()));
    }

    /**
     * No @Transactional: the row is read by the repository's own short transaction, and the call to
     * Stripe happens after it, so no database transaction waits on the network.
     */
    @Override
    public Optional<PaymentNextAction> findNextAction(TenantId tenantId, UUID orderId) {
        List<PaymentEntity> attempts = payments.findAllByTenantIdAndOrderIdOrderByCreatedAtAsc(tenantId.value(), orderId);
        if (attempts.isEmpty()) {
            return Optional.empty();
        }
        PaymentEntity newest = attempts.get(attempts.size() - 1);
        boolean waitingCard = newest.getProvider() == PaymentProviderName.CARD
                && newest.getStatus() == PaymentStatus.PENDING
                && newest.getProviderReference() != null;
        if (!waitingCard || !stripe.isConfigured()) {
            return Optional.empty();
        }

        StripeClient.Intent intent = stripe.readIntent(newest.getProviderReference());
        if ("succeeded".equals(intent.status()) || "canceled".equals(intent.status())) {
            return Optional.empty(); // nothing left for the browser to do; the webhook brings the result
        }
        return Optional.of(new PaymentNextAction("CARD", intent.clientSecret(), stripe.publishableKey()));
    }

}
