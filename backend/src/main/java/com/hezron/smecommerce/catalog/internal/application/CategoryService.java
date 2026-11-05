package com.hezron.smecommerce.catalog.internal.application;

import com.hezron.smecommerce.catalog.internal.domain.Slugs;
import com.hezron.smecommerce.catalog.internal.persistence.CategoryEntity;
import com.hezron.smecommerce.catalog.internal.persistence.CategoryRepository;
import com.hezron.smecommerce.catalog.internal.persistence.ProductRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A shop's categories. Every method takes the caller's {@code tenantId} and passes it into each query,
 * so a shop only ever reads or changes its own categories.
 */
@Service
public class CategoryService {

    /** Same as the slug column, varchar(80). */
    private static final int SLUG_MAX_LENGTH = 80;

    /** Names of the constraints in V30. They appear in the database's error message. */
    private static final String SLUG_UNIQUE_CONSTRAINT = "uq_categories_tenant_slug";
    private static final String PRODUCT_CATEGORY_FOREIGN_KEY = "fk_products_category";

    private final CategoryRepository categories;
    private final ProductRepository products;

    CategoryService(CategoryRepository categories, ProductRepository products) {
        this.categories = categories;
        this.products = products;
    }

    @Transactional(readOnly = true)
    public List<CategoryResult> list(UUID tenantId) {
        List<CategoryEntity> entities = categories.findAllByTenantIdOrderBySortOrderAscNameAsc(tenantId);

        List<CategoryResult> results = new ArrayList<>();
        for (CategoryEntity entity : entities) {
            results.add(CategoryResult.fromEntity(entity));
        }
        return results;
    }

    @Transactional
    public CategoryResult create(UUID tenantId, SaveCategoryCommand command) {
        String slug = chooseSlug(command);
        if (categories.existsByTenantIdAndSlug(tenantId, slug)) {
            throw new CategorySlugTakenException(slug);
        }

        String name = command.name().strip();
        CategoryEntity category = CategoryEntity.newCategory(tenantId, name, slug, command.sortOrder());
        saveCategory(category, slug);
        return CategoryResult.fromEntity(category);
    }

    /** @throws CategoryNotFoundException if the id is unknown or belongs to another shop */
    @Transactional
    public CategoryResult update(UUID tenantId, UUID categoryId, SaveCategoryCommand command) {
        CategoryEntity category = findCategoryInShop(tenantId, categoryId);

        String slug = chooseSlug(command);
        if (categories.existsByTenantIdAndSlugAndIdNot(tenantId, slug, categoryId)) {
            throw new CategorySlugTakenException(slug);
        }

        String name = command.name().strip();
        category.changeDetails(name, slug, command.sortOrder());
        saveCategory(category, slug);
        return CategoryResult.fromEntity(category);
    }

    /**
     * Deletes the category for good. Categories are only menu groups, so unlike products they aren't
     * archived. A category that still has products is refused, so no product loses its category by surprise.
     *
     * @throws CategoryNotFoundException if the id is unknown or belongs to another shop
     * @throws CategoryInUseException    if any product (archived ones too) is still in it
     */
    @Transactional
    public void delete(UUID tenantId, UUID categoryId) {
        CategoryEntity category = findCategoryInShop(tenantId, categoryId);

        if (products.existsByTenantIdAndCategoryId(tenantId, categoryId)) {
            throw new CategoryInUseException();
        }

        try {
            categories.delete(category);
            // Flush now so a database error shows up here, where we can catch it.
            categories.flush();
        } catch (DataIntegrityViolationException e) {
            // Someone put a product in this category between the check above and the delete.
            if (errorMentions(e, PRODUCT_CATEGORY_FOREIGN_KEY)) {
                throw new CategoryInUseException();
            }
            throw e;
        }
    }

    /** {@code WHERE id = ? AND tenant_id = ?}. Another shop's category is treated as not found. */
    private CategoryEntity findCategoryInShop(UUID tenantId, UUID categoryId) {
        Optional<CategoryEntity> found = categories.findByIdAndTenantId(categoryId, tenantId);
        if (found.isEmpty()) {
            throw new CategoryNotFoundException();
        }
        return found.get();
    }

    /** The slug the owner typed, or, if they left it empty, one built from the name. */
    private static String chooseSlug(SaveCategoryCommand command) {
        String slug;
        if (command.slug() == null || command.slug().isBlank()) {
            slug = Slugs.fromName(command.name(), SLUG_MAX_LENGTH);
        } else {
            slug = command.slug().strip();
        }

        if (!Slugs.isValid(slug)) {
            throw new InvalidSlugException();
        }
        return slug;
    }

    private void saveCategory(CategoryEntity category, String slug) {
        try {
            // Flush now so a duplicate slug shows up here, where we can catch it.
            categories.saveAndFlush(category);
        } catch (DataIntegrityViolationException e) {
            // Two saves with the same slug at the same moment: both passed the exists check, one loses here.
            if (errorMentions(e, SLUG_UNIQUE_CONSTRAINT)) {
                throw new CategorySlugTakenException(slug);
            }
            throw e;
        }
    }

    private static boolean errorMentions(DataIntegrityViolationException e, String constraintName) {
        String message = e.getMostSpecificCause().getMessage();
        if (message == null) {
            return false;
        }
        return message.contains(constraintName);
    }

}
