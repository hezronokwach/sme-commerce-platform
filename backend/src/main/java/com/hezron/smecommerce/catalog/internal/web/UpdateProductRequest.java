package com.hezron.smecommerce.catalog.internal.web;

import com.hezron.smecommerce.catalog.internal.application.UpdateProductCommand;
import com.hezron.smecommerce.catalog.internal.domain.ProductStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * JSON body of {@code PUT /api/admin/products/{id}}: the whole product as the form shows it, plus the
 * {@code version} it was loaded with. Send back exactly the version you got from GET.
 */
record UpdateProductRequest(
        @NotNull Integer version,
        UUID categoryId,
        @NotBlank @Size(max = 120) String name,
        @Size(max = 5000) String description,
        @NotNull @PositiveOrZero Long priceMinor,
        @Size(max = 2000) String imageUrl,
        @NotNull ProductStatus status) {

    UpdateProductCommand toCommand() {
        return new UpdateProductCommand(version, categoryId, name, description, priceMinor, imageUrl, status);
    }

}
