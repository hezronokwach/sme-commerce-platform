package com.hezron.smecommerce.inventory;

import com.hezron.smecommerce.shared.BaseException;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/** The product has no stock record in this shop (unknown, another shop's, or not set up yet). 409. */
public class ProductNotStockedException extends BaseException {

    public ProductNotStockedException(UUID productId) {
        super(HttpStatus.CONFLICT, "PRODUCT_NOT_STOCKED", "Product not stocked",
                "This shop has no stock record for one of the products.");
        getBody().setProperty("productId", productId);
    }

}
