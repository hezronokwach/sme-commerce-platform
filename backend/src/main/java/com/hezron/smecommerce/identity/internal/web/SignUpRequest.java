package com.hezron.smecommerce.identity.internal.web;

import com.hezron.smecommerce.identity.internal.application.SignUpCommand;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.NonNull;

/**
 * JSON body of {@code POST /api/auth/signup}. Validation here covers shape only (present, length,
 * format); business rules such as "slug not reserved" stay in the modules that own them.
 */
record SignUpRequest(
        @NotBlank @Size(max = 120) String shopName,
        @NotBlank @Pattern(regexp = "^[a-z0-9-]{3,50}$",
                message = "use 3 to 50 lowercase letters, digits or hyphens") String shopSlug,
        @NotBlank @Size(max = 120) String ownerName,
        @NotBlank @Email @Size(max = 254) String email,
        // bcrypt only reads the first 72 bytes, so longer passwords would silently be cut.
        @NotBlank @Size(min = 10, max = 72) String password) {

    SignUpCommand toCommand() {
        return new SignUpCommand(shopName, shopSlug, ownerName, email, password);
    }

    @Override
    public @NonNull String toString() {
        return "SignUpRequest[shopName=" + shopName + ", shopSlug=" + shopSlug
                + ", ownerName=" + ownerName + ", email=" + email + ", password=***]";
    }

}
