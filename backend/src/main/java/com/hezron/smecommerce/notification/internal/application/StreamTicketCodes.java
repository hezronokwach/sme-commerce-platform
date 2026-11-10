package com.hezron.smecommerce.notification.internal.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Making a ticket and the hash we store for it. (Ordering has the same few lines for its order links;
 * modules don't share internal code, and the shared kernel holds values only, so each keeps its own.)
 */
final class StreamTicketCodes {

    private static final int TICKET_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private StreamTicketCodes() {
    }

    /** 32 random bytes in base64url: safe in a URL, and impossible to guess. */
    static String newTicket() {
        byte[] bytes = new byte[TICKET_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** SHA-256 as 64 hex characters: the only form the database ever sees. */
    static String hash(String ticket) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(ticket.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

}
