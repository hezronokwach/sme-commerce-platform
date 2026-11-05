package com.hezron.smecommerce.catalog.internal.application;

import com.hezron.smecommerce.shared.BaseException;
import org.springframework.http.HttpStatus;

/**
 * No product with this id in the caller's shop. Another shop's id gives the same 404, not 403, so a
 * caller can't learn that the id exists elsewhere.
 */
public class ProductNotFoundException extends BaseException {

    public ProductNotFoundException() {
        super(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Product not found",
                "There is no product with this id in your shop.");
    }

}
