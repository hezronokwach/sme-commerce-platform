package com.hezron.smecommerce.tenant.internal.application;

import java.util.List;

/**
 * The ways of paying the platform knows about, with the name customers see and whether the platform
 * can take that kind of payment yet. An owner can only turn on a method that is available.
 */
final class KnownPaymentMethods {

    /** One entry: the provider code (as stored), its name for people, and whether it works yet. */
    record Method(String provider, String displayName, boolean available) {
    }

    static final String FAKE = "FAKE";
    static final String CARD = "CARD";
    static final String MPESA = "MPESA";

    /** In the order the admin lists them. */
    static final List<Method> ALL = List.of(
            // Standing in for M-Pesa until M-Pesa is connected: the phone's last digit picks the outcome.
            new Method(FAKE, "M-Pesa (test)", true),
            new Method(CARD, "Card", true),
            // Daraja (ADR 0019). Until its keys are set, an attempt is refused with "not set up yet".
            new Method(MPESA, "M-Pesa", true));

    private KnownPaymentMethods() {
    }

    /** The entry for a provider code, or null if the platform doesn't know it. */
    static Method find(String provider) {
        for (Method method : ALL) {
            if (method.provider().equals(provider)) {
                return method;
            }
        }
        return null;
    }

}
