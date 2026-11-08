package com.hezron.smecommerce.payments.internal.application;

import com.hezron.smecommerce.payments.PaymentFailedEvent;
import com.hezron.smecommerce.payments.PaymentProviderName;
import com.hezron.smecommerce.payments.PaymentSucceededEvent;
import com.hezron.smecommerce.payments.internal.domain.CallbackOutcome;
import com.hezron.smecommerce.payments.internal.domain.PaymentResultCodes;
import com.hezron.smecommerce.payments.internal.persistence.CallbackLogEntity;
import com.hezron.smecommerce.payments.internal.persistence.CallbackLogRepository;
import com.hezron.smecommerce.payments.internal.persistence.PaymentEntity;
import com.hezron.smecommerce.payments.internal.persistence.PaymentRepository;
import com.hezron.smecommerce.payments.internal.provider.CallbackResult;
import com.hezron.smecommerce.payments.internal.provider.PaymentProvider;
import com.hezron.smecommerce.payments.internal.provider.PaymentProviders;
import com.hezron.smecommerce.shared.TenantId;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The ONE place a payment learns how it ended (Guide 5.5 and 5.6). Callbacks from every provider come
 * here: today from the fake provider, later from the M-Pesa and card webhooks.
 * <p>
 * Safe to receive the same callback twice, even at the same moment:
 * <ol>
 *   <li>The payment row is locked (SELECT ... FOR UPDATE), so two copies take turns.</li>
 *   <li>The second copy then finds the payment already final and changes nothing (DUPLICATE).</li>
 * </ol>
 * Every callback is written to callback_log with what we did with it, whatever happens.
 */
@Service
public class PaymentCallbackService {

    private static final Logger log = LoggerFactory.getLogger(PaymentCallbackService.class);

    private final PaymentProviders providers;
    private final PaymentRepository payments;
    private final CallbackLogRepository callbackLog;
    private final ApplicationEventPublisher events;

    PaymentCallbackService(PaymentProviders providers, PaymentRepository payments, CallbackLogRepository callbackLog,
            ApplicationEventPublisher events) {
        this.providers = providers;
        this.payments = payments;
        this.callbackLog = callbackLog;
        this.events = events;
    }

    /** Handles one callback body from a provider and says what was done with it. */
    @Transactional
    public CallbackOutcome receive(PaymentProviderName providerName, String rawBody) {
        Optional<PaymentProvider> provider = providers.find(providerName);
        if (provider.isEmpty()) {
            return record(providerName, null, rawBody, CallbackOutcome.INVALID);
        }

        Optional<CallbackResult> parsed = provider.get().parseCallback(rawBody);
        if (parsed.isEmpty()) {
            return record(providerName, null, rawBody, CallbackOutcome.INVALID);
        }
        CallbackResult result = parsed.get();

        // Locked until this transaction ends: a second copy of this callback waits here.
        Optional<PaymentEntity> found = payments.findForUpdateByProviderAndProviderReference(providerName,
                result.providerReference());
        if (found.isEmpty()) {
            return record(providerName, result.providerReference(), rawBody, CallbackOutcome.UNKNOWN_REFERENCE);
        }
        PaymentEntity payment = found.get();

        if (payment.getStatus().isFinal()) {
            return record(providerName, result.providerReference(), rawBody, CallbackOutcome.DUPLICATE);
        }

        if (result.isSuccess()) {
            return applySuccess(payment, result, providerName, rawBody);
        }
        return applyFailure(payment, result, providerName, rawBody);
    }

    private CallbackOutcome applySuccess(PaymentEntity payment, CallbackResult result, PaymentProviderName providerName,
            String rawBody) {
        // Never mark paid for a different amount than we asked for (or an amount the provider didn't state).
        boolean sameAmount = result.amountMinor() != null && result.amountMinor() == payment.getAmountMinor();
        if (!sameAmount) {
            log.error("Payment {} succeeded for amount {} but {} was asked. Not marked paid; staff must check.",
                    payment.getId(), result.amountMinor(), payment.getAmountMinor());
            return record(providerName, result.providerReference(), rawBody, CallbackOutcome.AMOUNT_MISMATCH);
        }

        payment.succeed(result.receiptNumber(), result.resultCode());
        events.publishEvent(new PaymentSucceededEvent(TenantId.of(payment.getTenantId()), payment.getOrderId(),
                payment.getId(), result.receiptNumber(), payment.getAmountMinor()));
        return record(providerName, result.providerReference(), rawBody, CallbackOutcome.APPLIED);
    }

    private CallbackOutcome applyFailure(PaymentEntity payment, CallbackResult result, PaymentProviderName providerName,
            String rawBody) {
        String reason = PaymentResultCodes.friendlyReason(result.resultCode(), result.resultDescription());
        boolean retryable = PaymentResultCodes.isRetryable(result.resultCode());

        payment.fail(reason, result.resultCode());
        events.publishEvent(new PaymentFailedEvent(TenantId.of(payment.getTenantId()), payment.getOrderId(),
                payment.getId(), reason, retryable));
        return record(providerName, result.providerReference(), rawBody, CallbackOutcome.APPLIED);
    }

    private CallbackOutcome record(PaymentProviderName provider, String reference, String rawBody,
            CallbackOutcome outcome) {
        callbackLog.save(CallbackLogEntity.newEntry(provider, reference, rawBody, outcome));
        return outcome;
    }

}
