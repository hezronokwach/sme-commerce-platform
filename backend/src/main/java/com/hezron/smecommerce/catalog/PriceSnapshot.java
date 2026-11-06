package com.hezron.smecommerce.catalog;

import java.util.UUID;

/**
 * Only what ordering needs to know about a product at checkout: the server's current price, never the
 * one a browser sent.
 *
 * @param priceMinor cents: KES 50.00 is 5000
 * @param active     true only for ACTIVE products; drafts and archived products can't be ordered
 */
public record PriceSnapshot(UUID productId, String name, long priceMinor, String currency, boolean active) {
}
