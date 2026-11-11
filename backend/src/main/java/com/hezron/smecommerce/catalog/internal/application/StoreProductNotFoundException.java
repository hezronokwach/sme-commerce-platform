package com.hezron.smecommerce.catalog.internal.application;

import com.hezron.smecommerce.shared.BaseException;
import org.springframework.http.HttpStatus;

/**
 * The storefront asked for a product page the shop doesn't show: the web name is unknown, belongs to
 * another shop, or the product is a draft or archived. All give the same 404, so customers can't find
 * out about products that aren't on sale.
 */
public class StoreProductNotFoundException extends BaseException {

    public StoreProductNotFoundException() {
        super(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Product not found",
                "This shop has no product at this address.");
    }

}
