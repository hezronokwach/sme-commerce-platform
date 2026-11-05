package com.hezron.smecommerce.catalog.internal.domain;

import java.text.Normalizer;
import java.util.Locale;

/** Turns a name into a web name: "Fresh Vegetables & Fruit" becomes "fresh-vegetables-fruit". */
public final class Slugs {

    private Slugs() {
    }

    /**
     * Builds a slug from a name, at most {@code maxLength} characters long.
     * Returns an empty string when the name has no letters or digits at all (for example "!!!");
     * callers treat that as invalid.
     */
    public static String fromName(String name, int maxLength) {
        String lowercase = removeAccents(name).toLowerCase(Locale.ROOT);

        StringBuilder slug = new StringBuilder();
        boolean lastAddedWasHyphen = false;
        for (int i = 0; i < lowercase.length(); i++) {
            char character = lowercase.charAt(i);
            if (isLetterOrDigit(character)) {
                slug.append(character);
                lastAddedWasHyphen = false;
            } else if (slug.length() > 0 && !lastAddedWasHyphen) {
                // Spaces, "&", "/" and so on all become one hyphen. Never one at the start.
                slug.append('-');
                lastAddedWasHyphen = true;
            }
        }

        if (slug.length() > maxLength) {
            slug.setLength(maxLength);
        }
        // A hyphen may be left at the end (from "Fruit!" or from cutting): remove it.
        while (slug.length() > 0 && slug.charAt(slug.length() - 1) == '-') {
            slug.setLength(slug.length() - 1);
        }
        return slug.toString();
    }

    /**
     * Same rule as the slug CHECK constraints in V30: only lowercase letters, digits and single
     * hyphens, and no hyphen at the start or end. "fresh-vegetables" is valid; "-a", "a--b", "A" are not.
     */
    public static boolean isValid(String slug) {
        if (slug.isEmpty()) {
            return false;
        }
        if (slug.charAt(0) == '-' || slug.charAt(slug.length() - 1) == '-') {
            return false;
        }
        for (int i = 0; i < slug.length(); i++) {
            char character = slug.charAt(i);
            boolean isHyphen = character == '-';
            if (!isLetterOrDigit(character) && !isHyphen) {
                return false;
            }
            if (isHyphen && slug.charAt(i - 1) == '-') {
                return false;
            }
        }
        return true;
    }

    private static boolean isLetterOrDigit(char character) {
        boolean isLetter = character >= 'a' && character <= 'z';
        boolean isDigit = character >= '0' && character <= '9';
        return isLetter || isDigit;
    }

    /**
     * "Café" becomes "Cafe". NFD splits "é" into "e" plus a separate accent mark; the replaceAll then
     * deletes every accent mark ({@code \p{M}} means "any mark character").
     */
    private static String removeAccents(String text) {
        String separated = Normalizer.normalize(text, Normalizer.Form.NFD);
        return separated.replaceAll("\\p{M}", "");
    }

}
