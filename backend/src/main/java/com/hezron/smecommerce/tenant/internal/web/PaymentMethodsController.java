package com.hezron.smecommerce.tenant.internal.web;

import com.hezron.smecommerce.shared.AccessTokenClaims;
import com.hezron.smecommerce.tenant.internal.application.PaymentMethodResult;
import com.hezron.smecommerce.tenant.internal.application.PaymentMethodService;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The owner's "Payment methods" settings: which ways of paying the shop offers. OWNER only; staff get
 * 403. The shop is always the caller's own, from the access token.
 */
@RestController
@RequestMapping("/api/admin/settings/payment-methods")
@PreAuthorize("hasRole('OWNER')")
class PaymentMethodsController {

    private final PaymentMethodService paymentMethods;

    PaymentMethodsController(PaymentMethodService paymentMethods) {
        this.paymentMethods = paymentMethods;
    }

    /** {@code GET /api/admin/settings/payment-methods}: every method, each with on/off and whether it works yet. */
    @GetMapping
    List<PaymentMethodResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return toResponses(paymentMethods.listForOwner(callerTenant(jwt)));
    }

    /** {@code PUT /api/admin/settings/payment-methods/CARD} with {"enabled": true}. Returns the new list. */
    @PutMapping("/{provider}")
    List<PaymentMethodResponse> set(@AuthenticationPrincipal Jwt jwt, @PathVariable String provider,
            @Valid @RequestBody SetPaymentMethodRequest request) {
        return toResponses(paymentMethods.setEnabled(callerTenant(jwt), provider, request.enabled()));
    }

    private static UUID callerTenant(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString(AccessTokenClaims.TENANT_ID));
    }

    private static List<PaymentMethodResponse> toResponses(List<PaymentMethodResult> results) {
        List<PaymentMethodResponse> responses = new ArrayList<>();
        for (PaymentMethodResult result : results) {
            responses.add(PaymentMethodResponse.fromResult(result));
        }
        return responses;
    }

}
