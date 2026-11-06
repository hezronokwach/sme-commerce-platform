package com.hezron.smecommerce.ordering.internal.application;

import com.hezron.smecommerce.shared.BaseException;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/**
 * A product in the cart can't be bought: it doesn't exist in this shop, or it's a draft or archived.
 * Returns 409 with the product's id, so the storefront can point at the line to remove.
 */
public class ProductUnavailableException extends BaseException {

    public ProductUnavailableException(UUID productId) {
        super(HttpStatus.CONFLICT, "PRODUCT_UNAVAILABLE", "Product unavailable",
                "One of the products in your cart is no longer available.");
        getBody().setProperty("productId", productId);
    }

}
