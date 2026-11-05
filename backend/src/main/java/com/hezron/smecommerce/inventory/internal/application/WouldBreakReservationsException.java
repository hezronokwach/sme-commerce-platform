package com.hezron.smecommerce.inventory.internal.application;

import com.hezron.smecommerce.shared.BaseException;
import org.springframework.http.HttpStatus;

/**
 * Removing this much would leave less in the shop than unpaid orders are holding (or less than zero).
 * Returns 409.
 */
public class WouldBreakReservationsException extends BaseException {

    public WouldBreakReservationsException(int onHand, int reserved) {
        super(HttpStatus.CONFLICT, "WOULD_BREAK_RESERVATIONS", "Stock is held for orders",
                "There are " + onHand + " in the shop and " + reserved + " are held for unpaid orders, "
                        + "so at most " + (onHand - reserved) + " can be removed.");
    }

}
