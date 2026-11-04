package com.hezron.smecommerce.catalog.internal.web;

import com.hezron.smecommerce.catalog.internal.application.SaveCategoryCommand;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * JSON body for creating ({@code POST}) or editing ({@code PUT}) a category.
 * {@code slug} and {@code sortOrder} may be left out: the slug is then built from the name, the order is 0.
 * The slug's format is checked by CategoryService (SLUG_INVALID), so the rule lives in one place.
 */
record SaveCategoryRequest(
        @NotBlank @Size(max = 80) String name,
        @Size(max = 80) String slug,
        @Min(0) @Max(10_000) Integer sortOrder) {

    SaveCategoryCommand toCommand() {
        int order = 0;
        if (sortOrder != null) {
            order = sortOrder;
        }
        return new SaveCategoryCommand(name, slug, order);
    }

}
