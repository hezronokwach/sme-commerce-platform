package com.hezron.smecommerce.catalog.internal.application;

import com.hezron.smecommerce.shared.BaseException;
import org.springframework.http.HttpStatus;

/** The category still has products (archived ones count too), so it can't be deleted. Returns 409. */
public class CategoryInUseException extends BaseException {

    public CategoryInUseException() {
        super(HttpStatus.CONFLICT, "CATEGORY_IN_USE", "Category still has products",
                "Move its products to another category (or none) before deleting it.");
    }

}
