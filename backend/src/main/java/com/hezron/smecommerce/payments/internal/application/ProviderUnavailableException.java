package com.hezron.smecommerce.payments.internal.application;

/** The provider's circuit breaker is open: it wasn't called at all. Ask again later. */
class ProviderUnavailableException extends RuntimeException {

    ProviderUnavailableException(String providerName) {
        super("Payment provider " + providerName + " is not being called right now (circuit breaker open)");
    }

}
