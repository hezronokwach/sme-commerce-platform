package com.hezron.smecommerce.payments.internal.provider;

import com.hezron.smecommerce.payments.PaymentProviderName;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Finds the provider for a payment. Spring passes in every PaymentProvider bean it knows, so adding a
 * provider is only adding its class: nothing here or in ordering changes.
 */
@Component
public class PaymentProviders {

    private final Map<PaymentProviderName, PaymentProvider> byName = new EnumMap<>(PaymentProviderName.class);

    PaymentProviders(List<PaymentProvider> providers) {
        for (PaymentProvider provider : providers) {
            byName.put(provider.name(), provider);
        }
    }

    /** Empty while a provider isn't built yet (today: MPESA and CARD). */
    public Optional<PaymentProvider> find(PaymentProviderName name) {
        PaymentProvider provider = byName.get(name);
        if (provider == null) {
            return Optional.empty();
        }
        return Optional.of(provider);
    }

}
