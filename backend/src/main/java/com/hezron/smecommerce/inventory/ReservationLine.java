package com.hezron.smecommerce.inventory;

import java.util.UUID;

/** One line of an order to hold stock for: this many of this product. */
public record ReservationLine(UUID productId, int quantity) {
}
