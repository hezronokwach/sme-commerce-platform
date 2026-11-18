package com.hezron.smecommerce.tenant.internal.web;

import jakarta.validation.constraints.NotNull;

/** <pre>{"enabled": true}</pre> */
record SetPaymentMethodRequest(@NotNull Boolean enabled) {
}
