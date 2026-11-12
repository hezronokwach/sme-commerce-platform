package com.hezron.smecommerce.payments;

import com.hezron.smecommerce.shared.TenantId;
import java.util.UUID;

/**
 * The provider has been asked for the money, and the payment now waits for the customer: the PIN
 * prompt is on their phone. Announced for every attempt, the first and each "Try again", so a page
 * watching the order can say "check your phone" again after a failed attempt.
 *
 * @param status PENDING when the provider accepted the request, UNKNOWN when we couldn't tell (a timeout):
 *               then the page says "checking", never "failed"
 */
public record PaymentStartedEvent(TenantId tenantId, UUID orderId, UUID paymentId, String status) {
}
