package com.hezron.smecommerce.catalog.internal.web;

import com.hezron.smecommerce.catalog.internal.application.CategoryResult;
import java.util.UUID;

/** One category as the admin shows it. */
record CategoryResponse(UUID id, String name, String slug, int sortOrder) {

    static CategoryResponse fromResult(CategoryResult result) {
        return new CategoryResponse(result.id(), result.name(), result.slug(), result.sortOrder());
    }

}
