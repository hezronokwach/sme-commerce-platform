package com.hezron.smecommerce.payments.internal.provider;

/**
 * The provider's answer to "please start this payment".
 *
 * @param providerReference the provider's id for this attempt; only for ACCEPTED
 * @param reason            words for people; only for REJECTED
 */
public record StartResult(Outcome outcome, String providerReference, String reason) {

    public enum Outcome {
        /** Started; a callback will follow. */
        ACCEPTED,
        /** Refused straight away (bad phone number, provider not set up, ...). */
        REJECTED,
        /** No clear answer (timeout, connection dropped): it may or may not have started. */
        UNKNOWN
    }

    public static StartResult accepted(String providerReference) {
        return new StartResult(Outcome.ACCEPTED, providerReference, null);
    }

    public static StartResult rejected(String reason) {
        return new StartResult(Outcome.REJECTED, null, reason);
    }

    public static StartResult unknown() {
        return new StartResult(Outcome.UNKNOWN, null, null);
    }

}
