package com.hezron.smecommerce.ordering.internal.application;

import com.hezron.smecommerce.ordering.internal.persistence.OrderNumberCounters;
import com.hezron.smecommerce.ordering.internal.persistence.OrderNumberCounters.TakenNumber;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Readable order numbers such as MM-000123 (Guide 4.2): the shop's prefix, then its own count, padded
 * to 6 digits. Each shop counts 1, 2, 3, ... on its own.
 */
@Service
public class OrderNumberService {

    /** The prefix column is varchar(6). */
    private static final int PREFIX_MAX_LENGTH = 6;

    private final OrderNumberCounters counters;

    OrderNumberService(OrderNumberCounters counters) {
        this.counters = counters;
    }

    /**
     * The shop's next order number. MANDATORY: it must run inside the order's transaction, so the
     * counter row stays locked until the order is saved, and a failed order gives its number back
     * (the counter change is rolled back with it).
     *
     * @param shopSlug the shop's web name, used for the prefix the first time the shop takes an order
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public String nextOrderNumber(UUID tenantId, String shopSlug) {
        counters.createIfMissing(tenantId, prefixFromSlug(shopSlug));
        TakenNumber taken = counters.takeNext(tenantId);
        // %06d: the number with leading zeros to 6 digits, so 123 becomes 000123.
        return String.format(Locale.ROOT, "%s-%06d", taken.prefix(), taken.number());
    }

    /**
     * The first letter of each word of the web name, in capitals: mama-mboga gives MM,
     * duka-la-mama-njeri gives DLMN. A one-word name gives its first two letters: kamau gives KA.
     * At most 6 characters (the column's size).
     */
    static String prefixFromSlug(String shopSlug) {
        String[] words = shopSlug.split("-");

        StringBuilder prefix = new StringBuilder();
        if (words.length == 1) {
            String word = words[0];
            if (word.length() >= 2) {
                prefix.append(word, 0, 2);
            } else {
                prefix.append(word);
            }
        } else {
            for (String word : words) {
                if (!word.isEmpty() && prefix.length() < PREFIX_MAX_LENGTH) {
                    prefix.append(word.charAt(0));
                }
            }
        }
        return prefix.toString().toUpperCase(Locale.ROOT);
    }

}
