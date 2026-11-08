package com.hezron.smecommerce.payments.internal.application;

import com.hezron.smecommerce.payments.internal.application.PaymentStartService.StartJob;
import com.hezron.smecommerce.payments.internal.provider.PaymentProvider;
import com.hezron.smecommerce.payments.internal.provider.PaymentProviders;
import com.hezron.smecommerce.payments.internal.provider.StartResult;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Asks the provider to start a payment, once the order that requested it has been saved for good.
 * <p>
 * Why not {@code @ApplicationModuleListener} like the other listeners? That one also wraps the whole
 * method in a transaction, and this method makes a slow call to the provider, which must happen with
 * no transaction open (see PaymentStartService). So it uses the two parts we do want:
 * <ul>
 *   <li>{@code @TransactionalEventListener}: run only after ordering's transaction commits. If the
 *       order is rolled back, the provider is never asked, so no customer gets a PIN prompt for an
 *       order that doesn't exist. Modulith's event registry records it like any other listener.</li>
 *   <li>{@code @Async}: run on another thread, so placing the order doesn't wait for the provider.</li>
 * </ul>
 */
@Component
class PaymentRequestedListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentRequestedListener.class);

    private final PaymentStartService startService;
    private final PaymentProviders providers;

    PaymentRequestedListener(PaymentStartService startService, PaymentProviders providers) {
        this.startService = startService;
        this.providers = providers;
    }

    @Async
    @TransactionalEventListener
    void on(PaymentRequestedEvent event) {
        Optional<StartJob> job = startService.loadStartJob(event.tenantId(), event.paymentId(), event.orderNumber());
        if (job.isEmpty()) {
            return; // already started: this event was delivered twice
        }

        StartResult result = askProvider(job.get());
        startService.saveStartResult(event.tenantId(), event.paymentId(), result);
    }

    private StartResult askProvider(StartJob job) {
        Optional<PaymentProvider> provider = providers.find(job.provider());
        if (provider.isEmpty()) {
            // MPESA and CARD come in later issues. Until then the customer is told plainly.
            return StartResult.rejected(job.provider() + " payments are not connected yet. Please choose another way to pay.");
        }
        try {
            return provider.get().start(job.request());
        } catch (RuntimeException e) {
            // We don't know whether the provider got the request: UNKNOWN, never FAILED.
            log.warn("Provider {} did not answer clearly for payment {}", job.provider(), job.request().paymentId(), e);
            return StartResult.unknown();
        }
    }

}
