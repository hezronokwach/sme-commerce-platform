package com.hezron.smecommerce.payments;

import com.hezron.smecommerce.shared.TenantId;
import java.util.UUID;

/**
 * Everything payments needs to collect the money for one order.
 *
 * @param amountMinor cents, taken from the order (never from the browser)
 * @param payerPhone  2547XXXXXXXX
 */
public record RequestPaymentCommand(TenantId tenantId, UUID orderId, String orderNumber, long amountMinor,
        String currency, PaymentProviderName provider, String payerPhone) {
}
