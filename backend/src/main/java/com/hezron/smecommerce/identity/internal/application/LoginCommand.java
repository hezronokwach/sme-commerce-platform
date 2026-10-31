package com.hezron.smecommerce.identity.internal.application;

import org.jspecify.annotations.NonNull;

/** Input of the login use case. */
public record LoginCommand(String email, String password) {

    /** Keeps the password out of logs and exception messages if the command is ever printed. */
    @Override
    public @NonNull String toString() {
        return "LoginCommand[email=" + email + ", password=***]";
    }

}
