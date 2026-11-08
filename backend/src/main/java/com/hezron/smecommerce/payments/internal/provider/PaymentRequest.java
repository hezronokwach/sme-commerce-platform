package com.hezron.smecommerce.payments.internal.provider;

import java.util.UUID;

/**
 * What a provider needs to start one payment.
 *
 * @param amountMinor cents
 * @param payerPhone  2547XXXXXXXX
 * @param orderNumber shown to the customer by some providers (the M-Pesa prompt's "account reference")
 */
public record PaymentRequest(UUID paymentId, long amountMinor, String currency, String payerPhone,
        String orderNumber) {
}
