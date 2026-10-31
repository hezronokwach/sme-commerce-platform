package com.hezron.smecommerce.identity.internal.application;

import org.jspecify.annotations.NonNull;

/**
 * Input of the sign-up use case. Separate from the HTTP request record so the use case doesn't
 * depend on web concerns (JSON names, validation annotations) and could be called from a test or a job.
 */
public record SignUpCommand(String shopName, String shopSlug, String ownerName, String email, String password) {

    /** Keeps the password out of logs and exception messages if the command is ever printed. */
    @Override
    public @NonNull String toString() {
        return "SignUpCommand[shopName=" + shopName + ", shopSlug=" + shopSlug
                + ", ownerName=" + ownerName + ", email=" + email + ", password=***]";
    }

}
