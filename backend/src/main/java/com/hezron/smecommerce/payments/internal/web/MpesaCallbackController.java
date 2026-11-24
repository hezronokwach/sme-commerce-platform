package com.hezron.smecommerce.payments.internal.web;

import com.hezron.smecommerce.payments.PaymentProviderName;
import com.hezron.smecommerce.payments.internal.application.PaymentCallbackService;
import com.hezron.smecommerce.payments.internal.mpesa.MpesaProperties;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Safaricom's callback for M-Pesa prompts (Guide 5.5). Public, like every webhook: Safaricom can't log in
 * and doesn't sign its callbacks. What protects it:
 * <ul>
 *   <li>a hard-to-guess last part of the address (anything else gets 404, as if nothing were here);</li>
 *   <li>the CheckoutRequestID must belong to a payment we started;</li>
 *   <li>the amount must match, and a payment already final can't change (PaymentCallbackService).</li>
 * </ul>
 */
@RestController
class MpesaCallbackController {

    private final PaymentCallbackService callbacks;
    private final MpesaProperties properties;

    MpesaCallbackController(PaymentCallbackService callbacks, MpesaProperties properties) {
        this.callbacks = callbacks;
        this.properties = properties;
    }

    /** {@code POST /api/webhooks/mpesa/{secret}}. */
    @PostMapping("/api/webhooks/mpesa/{secret}")
    ResponseEntity<Map<String, Object>> receive(@PathVariable String secret, @RequestBody String rawBody) {
        if (!properties.isCallbackSecret(secret)) {
            return ResponseEntity.notFound().build();
        }
        callbacks.receive(PaymentProviderName.MPESA, rawBody);
        // Safaricom only needs to hear that we got it; what we did with it is in callback_log.
        return ResponseEntity.ok(Map.of("ResultCode", 0, "ResultDesc", "Accepted"));
    }

}
