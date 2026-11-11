package com.hezron.smecommerce.catalog.internal.application;

import com.hezron.smecommerce.catalog.ProductChangedEvent;
import com.hezron.smecommerce.catalog.ProductCreatedEvent;
import com.hezron.smecommerce.catalog.internal.domain.ProductStatus;
import com.hezron.smecommerce.catalog.internal.domain.Slugs;
import com.hezron.smecommerce.catalog.internal.persistence.CategoryEntity;
import com.hezron.smecommerce.catalog.internal.persistence.CategoryRepository;
import com.hezron.smecommerce.catalog.internal.persistence.ProductEntity;
import com.hezron.smecommerce.catalog.internal.persistence.ProductRepository;
import com.hezron.smecommerce.shared.Money;
import com.hezron.smecommerce.shared.TenantId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A shop's products (Guide 3.1, workflow W3). Every method takes the caller's {@code tenantId} and passes
 * it into each query, so a shop only ever reads or changes its own products.
 */
@Service
public class ProductService {

    /**
     * Every shop sells in KES for now. The docs say a product copies its shop's currency; that waits
     * until shops can pick another currency (catalog may now ask tenant, ADR 0011).
     */
    private static final String SHOP_CURRENCY = "KES";

    /** The slug column is varchar(140). The base is kept shorter so "-2", "-3"... still fit. */
    private static final int SLUG_BASE_MAX_LENGTH = 130;

    /** Name of the unique constraint in V30. It appears in the database's error message. */
    private static final String SLUG_UNIQUE_CONSTRAINT = "uq_products_tenant_slug";

    private final ProductRepository products;
    private final CategoryRepository categories;
    private final ApplicationEventPublisher events;

    ProductService(ProductRepository products, CategoryRepository categories, ApplicationEventPublisher events) {
        this.products = products;
        this.categories = categories;
        this.events = events;
    }

    /** All the shop's products, newest first. With a status, only products in that status. */
    @Transactional(readOnly = true)
    public List<ProductResult> list(UUID tenantId, ProductStatus status) {
        List<ProductEntity> entities;
        if (status == null) {
            entities = products.findAllByTenantIdOrderByCreatedAtDesc(tenantId);
        } else {
            entities = products.findAllByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, status);
        }

        List<ProductResult> results = new ArrayList<>();
        for (ProductEntity entity : entities) {
            results.add(ProductResult.fromEntity(entity));
        }
        return results;
    }

    /** @throws ProductNotFoundException if the id is unknown or belongs to another shop */
    @Transactional(readOnly = true)
    public ProductResult get(UUID tenantId, UUID productId) {
        ProductEntity product = findProductInShop(tenantId, productId);
        return ProductResult.fromEntity(product);
    }

    @Transactional
    public ProductResult create(UUID tenantId, CreateProductCommand command) {
        ProductStatus status = command.status();
        if (status == null) {
            status = ProductStatus.ACTIVE;
        }
        checkStatusAllowed(status);
        checkCategoryInShop(tenantId, command.categoryId());

        String slug = findFreeSlug(tenantId, chooseBaseSlug(command));
        Money price = Money.of(command.priceMinor(), SHOP_CURRENCY);
        ProductEntity product = ProductEntity.newProduct(tenantId, command.categoryId(), command.name().strip(),
                slug, command.description(), price, command.imageUrl(), status);

        try {
            // Flush now so a duplicate slug shows up here, where we can catch it.
            products.saveAndFlush(product);
        } catch (DataIntegrityViolationException e) {
            // Two products got the same free slug at the same moment; this one lost.
            if (errorMentions(e, SLUG_UNIQUE_CONSTRAINT)) {
                throw new ProductSlugTakenException();
            }
            throw e;
        }

        // Tells other modules (inventory, assistant) once they listen. Nobody does yet.
        events.publishEvent(new ProductCreatedEvent(TenantId.of(tenantId), product.getId(), product.getName(),
                product.getDescription()));
        return ProductResult.fromEntity(product);
    }

    /**
     * @throws ProductNotFoundException                if the id is unknown or belongs to another shop
     * @throws ObjectOptimisticLockingFailureException if the product changed since the caller loaded it
     *                                                 (GlobalErrorHandler turns it into 409 CONCURRENT_UPDATE)
     */
    @Transactional
    public ProductResult update(UUID tenantId, UUID productId, UpdateProductCommand command) {
        ProductEntity product = findProductInShop(tenantId, productId);

        // The caller edited the version they loaded. If it isn't the current one, someone saved in between.
        if (product.getVersion() != command.expectedVersion()) {
            throw new ObjectOptimisticLockingFailureException(ProductEntity.class, productId);
        }
        checkStatusAllowed(command.status());
        checkCategoryInShop(tenantId, command.categoryId());

        // TODO(audit issue): record PRODUCT_PRICE_CHANGED with the old and new price (Guide 3.1).
        product.changeDetails(command.categoryId(), command.name().strip(), command.description(),
                command.priceMinor(), command.imageUrl(), command.status());
        // Flush so the new version number is in the result. If someone saved between our check above and
        // here, @Version makes this UPDATE match no row and Hibernate throws the same lock exception.
        products.flush();

        publishChanged(tenantId, product);
        return ProductResult.fromEntity(product);
    }

    /**
     * Takes the product off sale for good. It is never deleted, because past orders point at it.
     * Archiving an archived product changes nothing.
     *
     * @throws ProductNotFoundException if the id is unknown or belongs to another shop
     */
    @Transactional
    public void archive(UUID tenantId, UUID productId) {
        ProductEntity product = findProductInShop(tenantId, productId);
        if (product.getStatus() == ProductStatus.ARCHIVED) {
            return;
        }
        product.archive();
        publishChanged(tenantId, product);
    }

    /** {@code WHERE id = ? AND tenant_id = ?}. Another shop's product is treated as not found. */
    private ProductEntity findProductInShop(UUID tenantId, UUID productId) {
        Optional<ProductEntity> found = products.findByIdAndTenantId(productId, tenantId);
        if (found.isEmpty()) {
            throw new ProductNotFoundException();
        }
        return found.get();
    }

    /** A product may only go in a category of its own shop. No category (null) is fine. */
    private void checkCategoryInShop(UUID tenantId, UUID categoryId) {
        if (categoryId == null) {
            return;
        }
        Optional<CategoryEntity> category = categories.findByIdAndTenantId(categoryId, tenantId);
        if (category.isEmpty()) {
            throw new CategoryNotFoundException();
        }
    }

    private static void checkStatusAllowed(ProductStatus status) {
        if (status != ProductStatus.DRAFT && status != ProductStatus.ACTIVE) {
            throw new InvalidProductStatusException();
        }
    }

    /** The slug the owner typed, or, if they left it empty, one built from the name. */
    private static String chooseBaseSlug(CreateProductCommand command) {
        String slug;
        if (command.slug() == null || command.slug().isBlank()) {
            slug = Slugs.fromName(command.name(), SLUG_BASE_MAX_LENGTH);
        } else {
            slug = command.slug().strip();
        }

        if (!Slugs.isValid(slug) || slug.length() > SLUG_BASE_MAX_LENGTH) {
            throw new InvalidSlugException();
        }
        return slug;
    }

    /** "sukuma", or if that's taken "sukuma-2", then "sukuma-3"... until one is free in this shop. */
    private String findFreeSlug(UUID tenantId, String baseSlug) {
        String candidate = baseSlug;
        int number = 2;
        while (products.existsByTenantIdAndSlug(tenantId, candidate)) {
            candidate = baseSlug + "-" + number;
            number++;
        }
        return candidate;
    }

    private void publishChanged(UUID tenantId, ProductEntity product) {
        events.publishEvent(new ProductChangedEvent(TenantId.of(tenantId), product.getId(), product.getName(),
                product.getDescription(), product.getStatus().name()));
    }

    private static boolean errorMentions(DataIntegrityViolationException e, String constraintName) {
        String message = e.getMostSpecificCause().getMessage();
        if (message == null) {
            return false;
        }
        return message.contains(constraintName);
    }

}
