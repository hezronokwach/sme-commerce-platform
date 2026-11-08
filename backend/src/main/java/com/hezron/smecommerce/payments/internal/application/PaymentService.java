package com.hezron.smecommerce.payments.internal.application;

import com.hezron.smecommerce.payments.PaymentInProgressException;
import com.hezron.smecommerce.payments.PaymentsApi;
import com.hezron.smecommerce.payments.RequestPaymentCommand;
import com.hezron.smecommerce.payments.internal.domain.PaymentStatus;
import com.hezron.smecommerce.payments.internal.persistence.PaymentEntity;
import com.hezron.smecommerce.payments.internal.persistence.PaymentRepository;
import java.util.List;
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
    private final ApplicationEventPublisher events;

    PaymentService(PaymentRepository payments, ApplicationEventPublisher events) {
        this.payments = payments;
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

}
