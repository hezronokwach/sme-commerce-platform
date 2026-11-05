package com.hezron.smecommerce.catalog.internal.application;

import com.hezron.smecommerce.shared.BaseException;
import org.springframework.http.HttpStatus;

/**
 * Another product took the same web name at the same moment (a race). Rare; saving again picks the
 * next free name. Returns 409.
 */
public class ProductSlugTakenException extends BaseException {

    public ProductSlugTakenException() {
        super(HttpStatus.CONFLICT, "PRODUCT_SLUG_TAKEN", "Product web name taken",
                "Another product got this web name at the same moment. Try saving again.");
    }

}
