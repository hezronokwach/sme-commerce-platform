package com.hezron.smecommerce.assistant.internal.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A shopper's question: at most 500 characters, which also limits what one question can cost. */
record AskRequest(@NotBlank @Size(max = 500) String question) {
}
