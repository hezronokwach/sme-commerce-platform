package com.hezron.smecommerce.shared;

import org.jspecify.annotations.Nullable;

/**
 * A Kenyan mobile number in the one form the whole system stores and compares: 2547XXXXXXXX
 * (12 digits, no plus sign). People type it in many ways; {@link #parse} accepts them all:
 *
 * <pre>
 * 0712 345 678   +254 712 345 678   254712345678   712345678   (0712) 345-678
 *                              all become 254712345678
 * </pre>
 *
 * {@link #toString()} hides the middle digits ("2547******78"): a full phone number is personal data
 * and must never end up in the logs (CLAUDE.md). Use {@link #value()} when the full number is needed.
 */
public record PhoneNumber(String value) {

    private static final String COUNTRY_CODE = "254";
    private static final int LENGTH = 12;

    /**
     * The compact constructor checks the stored form, so a PhoneNumber object is always valid. To turn
     * what a person typed into a PhoneNumber, use {@link #parse}.
     */
    public PhoneNumber {
        if (!isStoredForm(value)) {
            throw new InvalidPhoneNumberException();
        }
    }

    /** @throws InvalidPhoneNumberException if it isn't a Kenyan mobile number in any accepted form */
    public static PhoneNumber parse(@Nullable String typed) {
        if (typed == null) {
            throw new InvalidPhoneNumberException();
        }

        // Keep only the digits: spaces, "+", "-", "(" and ")" are just how people write numbers.
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < typed.length(); i++) {
            char character = typed.charAt(i);
            if (character >= '0' && character <= '9') {
                digits.append(character);
            } else if (character != ' ' && character != '+' && character != '-'
                    && character != '(' && character != ')') {
                // Letters or other symbols: not a phone number.
                throw new InvalidPhoneNumberException();
            }
        }

        String number = digits.toString();
        if (number.startsWith(COUNTRY_CODE)) {
            // Already 2547XXXXXXXX.
            return new PhoneNumber(number);
        }
        if (number.startsWith("0")) {
            // 0712345678: replace the leading 0 with 254.
            return new PhoneNumber(COUNTRY_CODE + number.substring(1));
        }
        // 712345678: add 254 in front.
        return new PhoneNumber(COUNTRY_CODE + number);
    }

    /** "2547******78": enough to recognise it, not enough to call it. */
    @Override
    public String toString() {
        return value.substring(0, 4) + "******" + value.substring(LENGTH - 2);
    }

    /** Exactly 12 digits, starting with 2547. */
    private static boolean isStoredForm(@Nullable String value) {
        if (value == null || value.length() != LENGTH || !value.startsWith("2547")) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character < '0' || character > '9') {
                return false;
            }
        }
        return true;
    }

}
