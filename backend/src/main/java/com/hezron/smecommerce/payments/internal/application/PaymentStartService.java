package com.hezron.smecommerce.payments.internal.application;

import com.hezron.smecommerce.payments.PaymentFailedEvent;
import com.hezron.smecommerce.payments.PaymentProviderName;
import com.hezron.smecommerce.payments.internal.domain.PaymentStatus;
import com.hezron.smecommerce.payments.internal.persistence.PaymentEntity;
import com.hezron.smecommerce.payments.internal.persistence.PaymentRepository;
import com.hezron.smecommerce.payments.internal.provider.PaymentRequest;
import com.hezron.smecommerce.payments.internal.provider.StartResult;
import com.hezron.smecommerce.shared.TenantId;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The two short database steps around the provider call (Guide 5.2, second half). They are separate
 * methods, each with its own transaction, so that NO transaction is open while the provider is asked:
 *
 * <pre>
 *   loadStartJob      (transaction 1: read the payment)
 *   provider.start    (no transaction: may take seconds)
 *   saveStartResult   (transaction 2: write what the provider said)
 * </pre>
 *
 * Holding a database connection during a slow HTTP call would let a few slow M-Pesa answers use up
 * every connection in the pool and freeze the whole shop.
 */
@Service
public class PaymentStartService {

    /** When the reconciliation job (later issue) should ask the provider, if no callback came. */
    private static final Duration FIRST_CHECK_AFTER_ACCEPTED = Duration.ofSeconds(90);
    private static final Duration FIRST_CHECK_AFTER_UNKNOWN = Duration.ofSeconds(60);

    private final PaymentRepository payments;
    private final ApplicationEventPublisher events;

    PaymentStartService(PaymentRepository payments, ApplicationEventPublisher events) {
        this.payments = payments;
        this.events = events;
    }

    /** What the provider must be told. Empty if the payment was already started (a repeated event). */
    @Transactional(readOnly = true)
    public Optional<StartJob> loadStartJob(UUID tenantId, UUID paymentId, String orderNumber) {
        Optional<PaymentEntity> found = payments.findByIdAndTenantId(paymentId, tenantId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        PaymentEntity payment = found.get();
        if (payment.getStatus() != PaymentStatus.INITIATED) {
            return Optional.empty();
        }
        PaymentRequest request = new PaymentRequest(payment.getId(), payment.getAmountMinor(),
                payment.getCurrency(), payment.getPayerPhone(), orderNumber);
        return Optional.of(new StartJob(payment.getProvider(), request));
    }

    @Transactional
    public void saveStartResult(UUID tenantId, UUID paymentId, StartResult result) {
        Optional<PaymentEntity> found = payments.findByIdAndTenantId(paymentId, tenantId);
        if (found.isEmpty()) {
            return;
        }
        PaymentEntity payment = found.get();
        if (payment.getStatus() != PaymentStatus.INITIATED) {
            return;
        }

        if (result.outcome() == StartResult.Outcome.ACCEPTED) {
            payment.markPending(result.providerReference(), Instant.now().plus(FIRST_CHECK_AFTER_ACCEPTED));
        } else if (result.outcome() == StartResult.Outcome.REJECTED) {
            payment.fail(result.reason(), null);
            // Retryable: a refused start (bad number, provider not set up) is worth another try.
            events.publishEvent(new PaymentFailedEvent(TenantId.of(tenantId), payment.getOrderId(),
                    payment.getId(), result.reason(), true));
        } else {
            payment.markUnknown(Instant.now().plus(FIRST_CHECK_AFTER_UNKNOWN));
        }
    }

    /** Which provider to ask, and what to tell it. */
    public record StartJob(PaymentProviderName provider, PaymentRequest request) {
    }

}
