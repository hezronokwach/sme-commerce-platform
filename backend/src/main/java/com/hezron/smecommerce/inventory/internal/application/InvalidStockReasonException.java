package com.hezron.smecommerce.inventory.internal.application;

import com.hezron.smecommerce.shared.BaseException;
import org.springframework.http.HttpStatus;

/** Sales are recorded only when an order is paid, never by hand. Returns 400. */
public class InvalidStockReasonException extends BaseException {

    public InvalidStockReasonException() {
        super(HttpStatus.BAD_REQUEST, "STOCK_REASON_INVALID", "Invalid reason",
                "Use RESTOCK, ADJUSTMENT or RETURN. Sales are recorded automatically when an order is paid.");
    }

}
