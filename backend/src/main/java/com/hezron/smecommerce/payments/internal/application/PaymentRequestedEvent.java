package com.hezron.smecommerce.payments.internal.application;

import java.util.UUID;

/**
 * Payments' own internal event: "a payment was saved, now ask the provider". Published inside the
 * order's transaction, delivered only after it commits (see PaymentRequestedListener). Internal: no
 * other module sees or listens to it.
 */
public record PaymentRequestedEvent(UUID tenantId, UUID paymentId, String orderNumber) {
}
