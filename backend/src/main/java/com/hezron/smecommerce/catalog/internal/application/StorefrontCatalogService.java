package com.hezron.smecommerce.catalog.internal.application;

import com.hezron.smecommerce.catalog.internal.domain.ProductStatus;
import com.hezron.smecommerce.catalog.internal.persistence.CategoryEntity;
import com.hezron.smecommerce.catalog.internal.persistence.CategoryRepository;
import com.hezron.smecommerce.catalog.internal.persistence.ProductEntity;
import com.hezron.smecommerce.catalog.internal.persistence.ProductRepository;
import com.hezron.smecommerce.tenant.TenantApi;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What a customer may see of a shop's catalog (workflow W5 step 4). No login: the shop comes from the
 * web name in the address. Each method first asks tenant which shop that is (an unknown or suspended
 * shop throws {@link com.hezron.smecommerce.tenant.ShopNotFoundException}, which becomes 404), then
 * passes the shop's id into every query, the same as the admin side does (ADR 0005).
 * <p>
 * Only ACTIVE products are ever returned. Drafts and archived products don't exist for customers.
 */
@Service
public class StorefrontCatalogService {

    private final TenantApi tenantApi;
    private final ProductRepository products;
    private final CategoryRepository categories;

    StorefrontCatalogService(TenantApi tenantApi, ProductRepository products, CategoryRepository categories) {
        this.tenantApi = tenantApi;
        this.products = products;
        this.categories = categories;
    }

    /** The shop's products on sale, newest first. With a category, only that category's products. */
    @Transactional(readOnly = true)
    public List<ProductResult> listProducts(String shopSlug, UUID categoryId) {
        UUID tenantId = tenantApi.findActiveTenantIdBySlug(shopSlug).value();

        List<ProductEntity> entities;
        if (categoryId == null) {
            entities = products.findAllByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, ProductStatus.ACTIVE);
        } else {
            entities = products.findAllByTenantIdAndStatusAndCategoryIdOrderByCreatedAtDesc(tenantId,
                    ProductStatus.ACTIVE, categoryId);
        }

        List<ProductResult> results = new ArrayList<>();
        for (ProductEntity entity : entities) {
            results.add(ProductResult.fromEntity(entity));
        }
        return results;
    }

    /** @throws StoreProductNotFoundException if this shop has no product on sale with this web name */
    @Transactional(readOnly = true)
    public ProductResult getProduct(String shopSlug, String productSlug) {
        UUID tenantId = tenantApi.findActiveTenantIdBySlug(shopSlug).value();

        Optional<ProductEntity> found = products.findByTenantIdAndSlugAndStatus(tenantId, productSlug,
                ProductStatus.ACTIVE);
        if (found.isEmpty()) {
            throw new StoreProductNotFoundException();
        }
        return ProductResult.fromEntity(found.get());
    }

    /** The shop's menu, in the order the owner chose. */
    @Transactional(readOnly = true)
    public List<CategoryResult> listCategories(String shopSlug) {
        UUID tenantId = tenantApi.findActiveTenantIdBySlug(shopSlug).value();

        List<CategoryResult> results = new ArrayList<>();
        for (CategoryEntity entity : categories.findAllByTenantIdOrderBySortOrderAscNameAsc(tenantId)) {
            results.add(CategoryResult.fromEntity(entity));
        }
        return results;
    }

}
