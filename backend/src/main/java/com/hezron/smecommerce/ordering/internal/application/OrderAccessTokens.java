package com.hezron.smecommerce.ordering.internal.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * The private order-link token: a guest's proof that an order is theirs, without an account.
 * <p>
 * The customer gets the token once, when placing the order. Only its SHA-256 is stored (in
 * orders.access_token_hash), so someone reading the database can't use it.
 */
final class OrderAccessTokens {

    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private OrderAccessTokens() {
    }

    /** 32 random bytes, written in base64url (letters, digits, - and _), safe to put in a link. */
    static String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** SHA-256 of the token, as 64 hex characters: what the database stores. */
    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            // Every Java runtime must support SHA-256.
            throw new IllegalStateException(impossible);
        }
    }

    /**
     * Does this token belong to the stored hash? MessageDigest.isEqual takes the same time whether
     * the first or the last character differs, so timing can't reveal how close a guess was.
     */
    static boolean matches(String token, String storedHash) {
        byte[] given = hash(token).getBytes(StandardCharsets.US_ASCII);
        byte[] stored = storedHash.getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(given, stored);
    }

}
