package com.hezron.smecommerce.tenant.internal.application;

import com.hezron.smecommerce.audit.AuditApi;
import com.hezron.smecommerce.shared.TenantId;
import com.hezron.smecommerce.tenant.internal.persistence.PaymentMethodEntity;
import com.hezron.smecommerce.tenant.internal.persistence.PaymentMethodRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Which ways of paying a shop offers (issue "Second payment provider"). The owner switches them; the
 * storefront shows only the shop's methods; payments refuses any other.
 */
@Service
public class PaymentMethodService {

    private final PaymentMethodRepository methods;
    private final AuditApi audit;

    PaymentMethodService(PaymentMethodRepository methods, AuditApi audit) {
        this.methods = methods;
        this.audit = audit;
    }

    /** Every method the platform knows, each with whether this shop has it on. For the owner's settings. */
    @Transactional(readOnly = true)
    public List<PaymentMethodResult> listForOwner(UUID tenantId) {
        List<PaymentMethodEntity> rows = methods.findAllByTenantIdOrderByProviderAsc(tenantId);

        List<PaymentMethodResult> results = new ArrayList<>();
        for (KnownPaymentMethods.Method known : KnownPaymentMethods.ALL) {
            boolean enabled = false;
            for (PaymentMethodEntity row : rows) {
                if (row.getProvider().equals(known.provider()) && row.isEnabled()) {
                    enabled = true;
                }
            }
            results.add(new PaymentMethodResult(known.provider(), known.displayName(), enabled, known.available()));
        }
        return results;
    }

    /** The provider codes this shop has on, in the admin's order: what checkout offers. */
    @Transactional(readOnly = true)
    public List<String> enabledProviders(UUID tenantId) {
        List<PaymentMethodEntity> rows = methods.findAllByTenantIdOrderByProviderAsc(tenantId);

        List<String> enabled = new ArrayList<>();
        for (KnownPaymentMethods.Method known : KnownPaymentMethods.ALL) {
            for (PaymentMethodEntity row : rows) {
                if (row.getProvider().equals(known.provider()) && row.isEnabled()) {
                    enabled.add(known.provider());
                }
            }
        }
        return enabled;
    }

    @Transactional(readOnly = true)
    public boolean isEnabled(UUID tenantId, String provider) {
        return methods.existsByTenantIdAndProviderAndEnabledTrue(tenantId, provider);
    }

    /**
     * Turns one method on or off for this shop. The shop's rows are locked first, so two tabs can't
     * both turn off "the other one" and leave the shop with no way to be paid.
     *
     * @throws UnknownPaymentMethodException     if the platform has no such method
     * @throws PaymentMethodUnavailableException if turning on a method the platform can't take yet
     * @throws LastPaymentMethodException        if it would leave the shop with nothing turned on
     */
    @Transactional
    public List<PaymentMethodResult> setEnabled(UUID tenantId, String provider, boolean enabled) {
        KnownPaymentMethods.Method known = KnownPaymentMethods.find(provider);
        if (known == null) {
            throw new UnknownPaymentMethodException(provider);
        }
        if (enabled && !known.available()) {
            throw new PaymentMethodUnavailableException(known.displayName());
        }

        List<PaymentMethodEntity> rows = methods.lockAllForShop(tenantId);
        PaymentMethodEntity row = null;
        int othersOn = 0;
        for (PaymentMethodEntity existing : rows) {
            if (existing.getProvider().equals(provider)) {
                row = existing;
            } else if (existing.isEnabled()) {
                othersOn++;
            }
        }

        if (enabled) {
            if (row == null) {
                methods.save(PaymentMethodEntity.newMethod(tenantId, provider, known.displayName(), true));
            } else {
                row.turnOn();
            }
        } else {
            if (othersOn == 0) {
                throw new LastPaymentMethodException();
            }
            if (row != null) {
                row.turnOff();
            }
        }
        methods.flush();
        audit.record(TenantId.of(tenantId), "PAYMENT_SETTINGS_UPDATED", "PAYMENT_METHOD", null,
                Map.of("provider", provider, "enabled", enabled));
        return listForOwner(tenantId);
    }

    /** A new shop starts with the fake provider on (standing in for M-Pesa), like V12 did for existing shops. */
    void addDefaultsFor(UUID tenantId) {
        KnownPaymentMethods.Method fake = KnownPaymentMethods.find(KnownPaymentMethods.FAKE);
        methods.save(PaymentMethodEntity.newMethod(tenantId, fake.provider(), fake.displayName(), true));
    }

}
