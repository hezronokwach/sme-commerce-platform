package com.hezron.smecommerce.catalog.internal.web;

import com.hezron.smecommerce.catalog.internal.application.UpdateProductCommand;
import com.hezron.smecommerce.catalog.internal.domain.ProductStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;
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
        // Up to five photos, each an https address (an upload to Cloudinary, ADR 0022).
        @Size(max = 5) List<@NotBlank @Size(max = 2000) @Pattern(regexp = "^https://.+",
                message = "must be an https address") String> imageUrls,
        @NotNull ProductStatus status) {

    UpdateProductCommand toCommand() {
        return new UpdateProductCommand(version, categoryId, name, description, priceMinor, photosOrNone(), status);
    }

    /** A missing list means no photos. */
    private List<String> photosOrNone() {
        if (imageUrls == null) {
            return new ArrayList<>();
        }
        return imageUrls;
    }

}
