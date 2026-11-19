package com.hezron.smecommerce.payments.internal.application;

import com.hezron.smecommerce.payments.internal.domain.PaymentStatus;
import com.hezron.smecommerce.payments.internal.persistence.PaymentEntity;
import com.hezron.smecommerce.payments.internal.persistence.PaymentRepository;
import com.hezron.smecommerce.payments.internal.provider.PaymentProvider;
import com.hezron.smecommerce.payments.internal.provider.PaymentProviders;
import com.hezron.smecommerce.payments.internal.provider.StatusResult;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Reconciliation (Guide 5.7, workflow W7): the safety net for answers that never came. Callbacks get
 * lost and start requests time out; without this a payment would sit at "checking" forever.
 * <p>
 * Every run (30 s by default) it takes the payments whose next check is due and, one by one:
 * <ol>
 *   <li>a start with no answer and no reference can't be asked about: after a while it is given up as
 *       FAILED, retryable ("please try again");</li>
 *   <li>otherwise it asks the provider. A final answer goes through PaymentCallbackService, exactly
 *       like a callback (same lock, so the two can never both apply). "Not yet" means: ask again
 *       later, a bit later each time;</li>
 *   <li>still nothing long after the start: TIMED_OUT. Customers see "checking", never "failed",
 *       and it is still asked about, hourly, for a day.</li>
 * </ol>
 * No database transaction is open while a provider is asked: each database step is its own short one.
 * fixedDelay (not fixedRate) means a run never starts while the previous one is still going.
 */
@Component
@EnableConfigurationProperties(ReconciliationProperties.class)
class PaymentReconciliationJob {

    private static final Logger log = LoggerFactory.getLogger(PaymentReconciliationJob.class);

    private static final List<PaymentStatus> STILL_OPEN = List.of(PaymentStatus.PENDING, PaymentStatus.UNKNOWN,
            PaymentStatus.TIMED_OUT);

    private final PaymentRepository payments;
    private final PaymentProviders providers;
    private final PaymentCallbackService callbacks;
    private final ReconciliationSteps steps;
    private final ReconciliationProperties settings;

    PaymentReconciliationJob(PaymentRepository payments, PaymentProviders providers, PaymentCallbackService callbacks,
            ReconciliationSteps steps, ReconciliationProperties settings) {
        this.payments = payments;
        this.providers = providers;
        this.callbacks = callbacks;
        this.steps = steps;
        this.settings = settings;
    }

    @Scheduled(fixedDelayString = "${app.payments.reconciliation.every:30s}")
    void reconcile() {
        Instant now = Instant.now();
        List<PaymentEntity> due = payments.findDueForCheck(STILL_OPEN, now, PageRequest.of(0, settings.batchSize()));
        for (PaymentEntity payment : due) {
            try {
                check(payment, now);
            } catch (RuntimeException e) {
                // One bad payment must not stop the others; it is asked about again next time.
                log.warn("Reconciliation of payment {} failed; will retry", payment.getId(), e);
                steps.askAgainLater(payment.getId(), now);
            }
        }
    }

    private void check(PaymentEntity payment, Instant now) {
        Duration age = Duration.between(payment.getCreatedAt(), now);

        if (payment.getProviderReference() == null) {
            // The start timed out before the provider gave us its id: there is nothing to ask about.
            if (age.compareTo(settings.unknownGiveUpAfter()) >= 0) {
                log.info("Payment {} got no answer and has no reference; giving up as failed", payment.getId());
                callbacks.giveUpUnanswered(payment.getId());
            } else {
                steps.askAgainLater(payment.getId(), now);
            }
            return;
        }

        Optional<PaymentProvider> provider = providers.find(payment.getProvider());
        if (provider.isEmpty()) {
            steps.askAgainLater(payment.getId(), now);
            return;
        }

        // No transaction here: this may be a slow HTTP call.
        StatusResult status = provider.get().queryStatus(payment.getProviderReference());

        if (status.outcome() == StatusResult.Outcome.PENDING) {
            steps.stillWaiting(payment.getId(), now);
            return;
        }
        log.info("Payment {} resolved by a status query: {}", payment.getId(), status.outcome());
        callbacks.applyStatus(payment.getId(), status);
    }

}
