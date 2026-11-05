package com.hezron.smecommerce.inventory;

import com.hezron.smecommerce.shared.BaseException;
import org.springframework.http.HttpStatus;

/** The lines to reserve make no sense: none at all, a quantity under 1, or the same product twice. 400. */
public class InvalidReservationException extends BaseException {

    public InvalidReservationException(String reason) {
        super(HttpStatus.BAD_REQUEST, "RESERVATION_INVALID", "Invalid reservation", reason);
    }

}
