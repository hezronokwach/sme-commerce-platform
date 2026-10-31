package com.hezron.smecommerce.identity.internal.web;

import com.hezron.smecommerce.identity.internal.application.LoginCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.NonNull;

/**
 * JSON body of {@code POST /api/auth/login}. No minimum length or email format check here: a wrong
 * value should get the normal "incorrect" answer, not a hint about the rules.
 */
record LoginRequest(
        @NotBlank @Size(max = 254) String email,
        // Same bcrypt limit as sign-up: beyond 72 bytes bcrypt would ignore the rest.
        @NotBlank @Size(max = 72) String password) {

    LoginCommand toCommand() {
        return new LoginCommand(email, password);
    }

    @Override
    public @NonNull String toString() {
        return "LoginRequest[email=" + email + ", password=***]";
    }

}
