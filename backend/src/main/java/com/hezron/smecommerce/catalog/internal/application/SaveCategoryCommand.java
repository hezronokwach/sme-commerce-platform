package com.hezron.smecommerce.catalog.internal.application;

/**
 * Input for creating or editing a category. {@code slug} may be null: it is then built from the name.
 */
public record SaveCategoryCommand(String name, String slug, int sortOrder) {
}
