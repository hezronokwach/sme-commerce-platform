package com.hezron.smecommerce.payments.internal.application;

import com.hezron.smecommerce.payments.internal.domain.PaymentStatus;
import com.hezron.smecommerce.payments.internal.persistence.PaymentEntity;
import com.hezron.smecommerce.payments.internal.persistence.PaymentRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The database steps of reconciliation, each in its own short transaction with the payment locked.
 * Kept apart from the job so that no transaction is open while a provider is asked.
 */
@Service
class ReconciliationSteps {

    private final PaymentRepository payments;
    private final ReconciliationProperties settings;

    ReconciliationSteps(PaymentRepository payments, ReconciliationProperties settings) {
        this.payments = payments;
        this.settings = settings;
    }

    /**
     * The provider says "not finished yet". Ask again later, a little later each time. Long after the
     * start the payment becomes TIMED_OUT (still "checking" for the customer) and is asked about hourly;
     * after a day, not at all.
     */
    @Transactional
    public void stillWaiting(UUID paymentId, Instant now) {
        Optional<PaymentEntity> found = payments.lockById(paymentId);
        if (found.isEmpty()) {
            return;
        }
        PaymentEntity payment = found.get();
        if (payment.getStatus().isFinal()) {
            return; // a callback arrived meanwhile
        }
        Duration age = Duration.between(payment.getCreatedAt(), now);

        if (age.compareTo(settings.stopAfter()) >= 0) {
            payment.scheduleNextCheck(null);
            return;
        }
        if (payment.getStatus() == PaymentStatus.TIMED_OUT) {
            payment.scheduleNextCheck(now.plus(settings.timedOutCheckEvery()));
            return;
        }
        if (age.compareTo(settings.timedOutAfter()) >= 0) {
            payment.markTimedOut(now.plus(settings.timedOutCheckEvery()));
            return;
        }
        payment.scheduleNextCheck(now.plus(settings.waitAfter(payment.getCheckAttempts())));
    }

    /** Couldn't ask this time (provider down, unknown provider, not old enough to give up): try again later. */
    @Transactional
    public void askAgainLater(UUID paymentId, Instant now) {
        Optional<PaymentEntity> found = payments.lockById(paymentId);
        if (found.isEmpty() || found.get().getStatus().isFinal()) {
            return;
        }
        PaymentEntity payment = found.get();
        payment.scheduleNextCheck(now.plus(settings.waitAfter(payment.getCheckAttempts())));
    }

}
