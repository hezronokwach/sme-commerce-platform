package com.hezron.smecommerce.shared;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * An amount of money: whole minor units (cents) plus a 3-letter currency code. KES 50.00 is
 * {@code new Money(5000, "KES")}.
 * <p>
 * A {@code long} of cents, never a {@code double}: {@code 0.1 + 0.2} is {@code 0.30000000000000004} in
 * floating point, and a shop's totals must add up to the exact cent.
 */
public record Money(long amountMinor, String currency) {

    private static final Pattern CURRENCY_CODE = Pattern.compile("^[A-Z]{3}$");

    public Money {
        Objects.requireNonNull(currency, "currency must not be null");
        if (!CURRENCY_CODE.matcher(currency).matches()) {
            throw new IllegalArgumentException("currency must be a 3-letter code such as KES, got " + currency);
        }
    }

    public static Money of(long amountMinor, String currency) {
        return new Money(amountMinor, currency);
    }

}
