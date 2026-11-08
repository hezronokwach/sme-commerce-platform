package com.hezron.smecommerce.payments;

import com.hezron.smecommerce.shared.TenantId;
import java.util.UUID;

/**
 * A payment attempt ended without money.
 *
 * @param reason    words for the customer, e.g. "You cancelled the payment on your phone."
 * @param retryable true when the customer can simply try again (cancelled, wrong PIN, phone not
 *                  reached): ordering then keeps the order waiting until it expires. False for a hard
 *                  "no": ordering cancels the order and gives the stock back.
 */
public record PaymentFailedEvent(TenantId tenantId, UUID orderId, UUID paymentId, String reason,
        boolean retryable) {
}
