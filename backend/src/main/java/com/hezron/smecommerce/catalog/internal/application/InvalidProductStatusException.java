package com.hezron.smecommerce.catalog.internal.application;

import com.hezron.smecommerce.shared.BaseException;
import org.springframework.http.HttpStatus;

/** Create and edit only take DRAFT or ACTIVE; archiving has its own action (delete). Returns 400. */
public class InvalidProductStatusException extends BaseException {

    public InvalidProductStatusException() {
        super(HttpStatus.BAD_REQUEST, "PRODUCT_STATUS_INVALID", "Invalid product status",
                "Use DRAFT or ACTIVE. To take a product off sale for good, delete (archive) it.");
    }

}
