package com.hezron.smecommerce.tenant;

import com.hezron.smecommerce.shared.BaseException;
import org.springframework.http.HttpStatus;

/**
 * No open shop has this web name: it doesn't exist, or the shop is suspended. Both give the same 404,
 * so a customer can't tell a suspended shop from one that never existed. Public, so the storefront
 * endpoints of other modules can let it pass straight through.
 */
public class ShopNotFoundException extends BaseException {

    public ShopNotFoundException(String slug) {
        super(HttpStatus.NOT_FOUND, "SHOP_NOT_FOUND", "Shop not found",
                "There is no shop at '" + slug + "'.");
    }

}
