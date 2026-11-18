package com.hezron.smecommerce.tenant.internal.application;

/**
 * One way of paying, as the owner's settings show it.
 *
 * @param provider    FAKE, CARD or MPESA
 * @param displayName what customers see, e.g. "Card"
 * @param enabled     whether this shop offers it
 * @param available   whether the platform can take this kind of payment yet (M-Pesa: not yet)
 */
public record PaymentMethodResult(String provider, String displayName, boolean enabled, boolean available) {
}
