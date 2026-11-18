package com.hezron.smecommerce.tenant.internal.web;

import com.hezron.smecommerce.tenant.internal.application.PaymentMethodResult;

/** <pre>{"provider": "CARD", "displayName": "Card", "enabled": true, "available": true}</pre> */
record PaymentMethodResponse(String provider, String displayName, boolean enabled, boolean available) {

    static PaymentMethodResponse fromResult(PaymentMethodResult result) {
        return new PaymentMethodResponse(result.provider(), result.displayName(), result.enabled(),
                result.available());
    }

}
