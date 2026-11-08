package com.hezron.smecommerce.payments.internal.provider;

import com.hezron.smecommerce.payments.PaymentProviderName;
import java.util.Optional;

/**
 * What every way of taking money must offer (Guide 5.1). FAKE now; M-Pesa and card later. Adding a
 * provider means adding a class that implements this, and nothing in ordering changes.
 * <p>
 * Nothing M-Pesa-specific (shortcode, passkey) appears here: each provider reads what it needs itself.
 */
public interface PaymentProvider {

    /** Which provider this is. Used to pick the right one for a payment. */
    PaymentProviderName name();

    /**
     * Asks the provider to start collecting the money (for M-Pesa: send the PIN prompt to the phone).
     * Called with NO database transaction open, because it may be a slow HTTP call.
     */
    StartResult start(PaymentRequest request);

    /** Asks the provider how an earlier request ended. Used when no callback arrives (reconciliation). */
    StatusResult queryStatus(String providerReference);

    /** Reads the provider's callback body. Empty if it isn't a body this provider could have sent. */
    Optional<CallbackResult> parseCallback(String rawBody);

}
