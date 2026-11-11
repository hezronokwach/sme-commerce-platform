package com.hezron.smecommerce.catalog.internal.web;

import com.hezron.smecommerce.catalog.internal.application.CategoryResult;
import com.hezron.smecommerce.catalog.internal.application.ProductResult;
import com.hezron.smecommerce.catalog.internal.application.StorefrontCatalogService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public storefront endpoints: no login, the security chain leaves {@code /api/store/**} open.
 * The shop comes from the web name in the URL, never from a token.
 */
@RestController
@RequestMapping("/api/store/{slug}")
class StorefrontCatalogController {

    private final StorefrontCatalogService storefrontCatalog;

    StorefrontCatalogController(StorefrontCatalogService storefrontCatalog) {
        this.storefrontCatalog = storefrontCatalog;
    }

    /** {@code GET /api/store/mamamboga/products} or {@code ...?categoryId=...}. */
    @GetMapping("/products")
    List<StorefrontProductResponse> listProducts(@PathVariable String slug,
            @RequestParam(required = false) UUID categoryId) {
        List<ProductResult> results = storefrontCatalog.listProducts(slug, categoryId);

        List<StorefrontProductResponse> responses = new ArrayList<>();
        for (ProductResult result : results) {
            responses.add(StorefrontProductResponse.fromResult(result));
        }
        return responses;
    }

    /** {@code GET /api/store/mamamboga/products/sukuma-bundle}: by the product's web name, not its id. */
    @GetMapping("/products/{productSlug}")
    StorefrontProductResponse getProduct(@PathVariable String slug, @PathVariable String productSlug) {
        ProductResult result = storefrontCatalog.getProduct(slug, productSlug);
        return StorefrontProductResponse.fromResult(result);
    }

    /** {@code GET /api/store/mamamboga/categories}: the menu, in the owner's order. */
    @GetMapping("/categories")
    List<CategoryResponse> listCategories(@PathVariable String slug) {
        List<CategoryResult> results = storefrontCatalog.listCategories(slug);

        List<CategoryResponse> responses = new ArrayList<>();
        for (CategoryResult result : results) {
            responses.add(CategoryResponse.fromResult(result));
        }
        return responses;
    }

}
