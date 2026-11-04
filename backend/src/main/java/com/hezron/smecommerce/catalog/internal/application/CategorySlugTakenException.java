package com.hezron.smecommerce.catalog.internal.application;

import com.hezron.smecommerce.shared.BaseException;
import org.springframework.http.HttpStatus;

/** The shop already has a category with this web name. Returns 409. */
public class CategorySlugTakenException extends BaseException {

    public CategorySlugTakenException(String slug) {
        super(HttpStatus.CONFLICT, "CATEGORY_SLUG_TAKEN", "Category web name taken",
                "Your shop already has a category at '" + slug + "'. Choose another name or web name.");
    }

}
