package com.hezron.smecommerce.catalog.internal.web;

import com.hezron.smecommerce.catalog.internal.application.CreateProductCommand;
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
 * JSON body of {@code POST /api/admin/products}. Only {@code name} and {@code priceMinor} are required.
 *
 * <pre>{"name": "Sukuma bundle", "priceMinor": 5000, "categoryId": "...", "status": "ACTIVE"}</pre>
 *
 * The price is in cents, so KES 50.00 is sent as 5000. The admin app converts what the owner types.
 */
record CreateProductRequest(
        UUID categoryId,
        @NotBlank @Size(max = 120) String name,
        @Size(max = 130) String slug,
        @Size(max = 5000) String description,
        @NotNull @PositiveOrZero Long priceMinor,
        // Up to five photos, each an https address (an upload to Cloudinary, ADR 0022).
        @Size(max = 5) List<@NotBlank @Size(max = 2000) @Pattern(regexp = "^https://.+",
                message = "must be an https address") String> imageUrls,
        ProductStatus status) {

    CreateProductCommand toCommand() {
        return new CreateProductCommand(categoryId, name, slug, description, priceMinor, photosOrNone(), status);
    }

    /** A missing list means no photos. */
    private List<String> photosOrNone() {
        if (imageUrls == null) {
            return new ArrayList<>();
        }
        return imageUrls;
    }

}
