package com.hezron.smecommerce.inventory.internal.application;

import com.hezron.smecommerce.shared.BaseException;
import org.springframework.http.HttpStatus;

/** A change of 0 would only add an empty history row. Returns 400. */
public class ZeroStockChangeException extends BaseException {

    public ZeroStockChangeException() {
        super(HttpStatus.BAD_REQUEST, "STOCK_CHANGE_ZERO", "Nothing to change",
                "Send a positive number to add stock or a negative number to remove it.");
    }

}
