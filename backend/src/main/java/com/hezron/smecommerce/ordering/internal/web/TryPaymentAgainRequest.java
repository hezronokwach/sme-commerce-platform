package com.hezron.smecommerce.ordering.internal.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * <pre>{"orderToken": "..."}</pre>
 * The private order link goes in the body, not the URL: URLs end up in logs and browser history.
 */
record TryPaymentAgainRequest(@NotBlank @Size(max = 100) String orderToken) {
}
