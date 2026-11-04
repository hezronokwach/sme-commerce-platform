package com.hezron.smecommerce.catalog.internal.application;

import com.hezron.smecommerce.shared.BaseException;
import org.springframework.http.HttpStatus;

/** No category with this id in the caller's shop. Another shop's id gives the same 404. */
public class CategoryNotFoundException extends BaseException {

    public CategoryNotFoundException() {
        super(HttpStatus.NOT_FOUND, "CATEGORY_NOT_FOUND", "Category not found",
                "There is no category with this id in your shop.");
    }

}
