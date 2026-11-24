package com.hezron.smecommerce.tenant.internal.application;

import com.hezron.smecommerce.audit.AuditApi;
import com.hezron.smecommerce.shared.TenantId;
import com.hezron.smecommerce.tenant.internal.persistence.TenantBrandingEntity;
import com.hezron.smecommerce.tenant.internal.persistence.TenantBrandingRepository;
import com.hezron.smecommerce.tenant.internal.persistence.TenantEntity;
import com.hezron.smecommerce.tenant.internal.persistence.TenantRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The shop's own details in the admin: its name, tagline, logo and colours. The shop is always the
 * caller's, from the access token, so there is no way to name another shop here.
 */
@Service
public class ShopSettingsService {

    private final TenantRepository tenants;
    private final TenantBrandingRepository brandings;
    private final AuditApi audit;

    ShopSettingsService(TenantRepository tenants, TenantBrandingRepository brandings, AuditApi audit) {
        this.tenants = tenants;
        this.brandings = brandings;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public ShopSettingsResult get(UUID tenantId) {
        return ShopSettingsResult.fromEntities(findShop(tenantId), findBranding(tenantId));
    }

    /** Saves the owner's changes and records which fields changed in the audit log. */
    @Transactional
    public ShopSettingsResult update(UUID tenantId, UpdateShopSettingsCommand command) {
        TenantEntity tenant = findShop(tenantId);
        TenantBrandingEntity branding = findBranding(tenantId);

        String name = command.name().strip();
        String tagline = emptyToNull(command.tagline());
        String logoUrl = emptyToNull(command.logoUrl());
        String primaryColor = command.primaryColor().toUpperCase();
        String accentColor = command.accentColor().toUpperCase();

        List<String> changed = new ArrayList<>();
        addIfChanged(changed, "name", tenant.getName(), name);
        addIfChanged(changed, "tagline", branding.getTagline(), tagline);
        addIfChanged(changed, "logo", branding.getLogoUrl(), logoUrl);
        addIfChanged(changed, "primaryColor", branding.getPrimaryColor(), primaryColor);
        addIfChanged(changed, "accentColor", branding.getAccentColor(), accentColor);
        if (changed.isEmpty()) {
            return ShopSettingsResult.fromEntities(tenant, branding);
        }

        tenant.rename(name);
        branding.changeLook(tagline, logoUrl, primaryColor, accentColor);
        tenants.flush();
        audit.record(TenantId.of(tenantId), "SHOP_SETTINGS_UPDATED", "SHOP", tenantId, Map.of("changed", changed));
        return ShopSettingsResult.fromEntities(tenant, branding);
    }

    private TenantEntity findShop(UUID tenantId) {
        Optional<TenantEntity> tenant = tenants.findById(tenantId);
        if (tenant.isEmpty()) {
            // Can't happen for a valid token: the shop in it was created at sign-up and is never deleted.
            throw new IllegalStateException("Shop " + tenantId + " not found");
        }
        return tenant.get();
    }

    private TenantBrandingEntity findBranding(UUID tenantId) {
        Optional<TenantBrandingEntity> branding = brandings.findById(tenantId);
        if (branding.isEmpty()) {
            throw new IllegalStateException("Shop " + tenantId + " has no branding row");
        }
        return branding.get();
    }

    private static void addIfChanged(List<String> changed, String field, String before, String after) {
        if (!Objects.equals(before, after)) {
            changed.add(field);
        }
    }

    private static String emptyToNull(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return text.strip();
    }

}
