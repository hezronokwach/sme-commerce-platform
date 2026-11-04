package com.hezron.smecommerce.catalog.internal.web;

import com.hezron.smecommerce.catalog.internal.application.CategoryResult;
import com.hezron.smecommerce.catalog.internal.application.CategoryService;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * A shop's categories in the admin. Owners and staff may both manage them (the security chain already
 * requires one of those roles for /api/admin/**).
 */
@RestController
@RequestMapping("/api/admin/categories")
class CategoryController {

    private final CategoryService categoryService;

    CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    List<CategoryResponse> list(@AuthenticationPrincipal Jwt jwt) {
        UUID tenantId = CallerTenant.idFrom(jwt);
        List<CategoryResult> results = categoryService.list(tenantId);

        List<CategoryResponse> responses = new ArrayList<>();
        for (CategoryResult result : results) {
            responses.add(CategoryResponse.fromResult(result));
        }
        return responses;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CategoryResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SaveCategoryRequest request) {
        UUID tenantId = CallerTenant.idFrom(jwt);
        CategoryResult result = categoryService.create(tenantId, request.toCommand());
        return CategoryResponse.fromResult(result);
    }

    @PutMapping("/{categoryId}")
    CategoryResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID categoryId,
            @Valid @RequestBody SaveCategoryRequest request) {
        UUID tenantId = CallerTenant.idFrom(jwt);
        CategoryResult result = categoryService.update(tenantId, categoryId, request.toCommand());
        return CategoryResponse.fromResult(result);
    }

    @DeleteMapping("/{categoryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID categoryId) {
        UUID tenantId = CallerTenant.idFrom(jwt);
        categoryService.delete(tenantId, categoryId);
    }

}
