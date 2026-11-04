package com.hezron.smecommerce.catalog.internal.application;

import com.hezron.smecommerce.catalog.internal.persistence.CategoryEntity;
import java.util.UUID;

/** What the category use cases return for one category. */
public record CategoryResult(UUID id, String name, String slug, int sortOrder) {

    static CategoryResult fromEntity(CategoryEntity entity) {
        return new CategoryResult(entity.getId(), entity.getName(), entity.getSlug(), entity.getSortOrder());
    }

}
