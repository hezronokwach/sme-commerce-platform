package com.hezron.smecommerce.inventory.internal.application;

import com.hezron.smecommerce.shared.BaseException;
import org.springframework.http.HttpStatus;

/** No stock record for this product in the caller's shop. Another shop's product gives the same 404. */
public class StockItemNotFoundException extends BaseException {

    public StockItemNotFoundException() {
        super(HttpStatus.NOT_FOUND, "STOCK_ITEM_NOT_FOUND", "Stock not found",
                "There is no stock record for this product in your shop.");
    }

}
