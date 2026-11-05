package com.hezron.smecommerce.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hezron.smecommerce.TestcontainersConfiguration;
import com.hezron.smecommerce.catalog.internal.domain.ProductStatus;
import com.hezron.smecommerce.catalog.internal.persistence.CategoryEntity;
import com.hezron.smecommerce.catalog.internal.persistence.CategoryRepository;
import com.hezron.smecommerce.catalog.internal.persistence.ProductEntity;
import com.hezron.smecommerce.catalog.internal.persistence.ProductRepository;
import com.hezron.smecommerce.shared.Money;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

/**
 * What V30 guarantees even if the Java code has a bug: the database refuses the row. Plus a round trip
 * through the entities, so the mapping matches the table.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class CatalogDatabaseRulesTests {

    private static final Money FIFTY_SHILLINGS = Money.of(5000, "KES");

    @Autowired
    CategoryRepository categories;

    @Autowired
    ProductRepository products;

    @Test
    void productIsSavedAndReadBackOnlyForItsOwnShop() {
        UUID shopA = UUID.randomUUID();
        ProductEntity saved = products.saveAndFlush(product(shopA, null, "sukuma-bundle", FIFTY_SHILLINGS));

        assertThat(products.findByIdAndTenantId(saved.getId(), shopA))
                .hasValueSatisfying(found -> {
                    assertThat(found.getPrice()).isEqualTo(FIFTY_SHILLINGS);
                    assertThat(found.getStatus()).isEqualTo(ProductStatus.ACTIVE);
                    assertThat(found.getVersion()).isZero();
                });
        assertThat(products.findByIdAndTenantId(saved.getId(), UUID.randomUUID())).isEmpty();
    }

    @Test
    void productCannotUseAnotherShopsCategory() {
        UUID shopA = UUID.randomUUID();
        UUID shopB = UUID.randomUUID();
        CategoryEntity categoryOfA = categories.saveAndFlush(CategoryEntity.newCategory(shopA, "Vegetables", "vegetables", 0));

        // fk_products_category checks (tenant_id, category_id) together: the category exists, but not in shop B.
        assertThatThrownBy(() -> products.saveAndFlush(product(shopB, categoryOfA.getId(), "stolen-category", FIFTY_SHILLINGS)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_products_category");
    }

    @Test
    void sameSlugIsFineInTwoShopsButNotTwiceInOne() {
        UUID shopA = UUID.randomUUID();
        products.saveAndFlush(product(shopA, null, "mandazi", FIFTY_SHILLINGS));
        products.saveAndFlush(product(UUID.randomUUID(), null, "mandazi", FIFTY_SHILLINGS));

        assertThatThrownBy(() -> products.saveAndFlush(product(shopA, null, "mandazi", FIFTY_SHILLINGS)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_products_tenant_slug");
    }

    @Test
    void negativePriceIsRefused() {
        assertThatThrownBy(() -> products.saveAndFlush(product(UUID.randomUUID(), null, "refund", Money.of(-1, "KES"))))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_products_price_not_negative");
    }

    private static ProductEntity product(UUID tenantId, UUID categoryId, String slug, Money price) {
        return ProductEntity.newProduct(tenantId, categoryId, "Product " + slug, slug, null, price, null,
                ProductStatus.ACTIVE);
    }

}
