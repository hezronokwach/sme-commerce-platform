package com.hezron.smecommerce.tenant.internal.application;

import com.hezron.smecommerce.tenant.ShopNotFoundException;
import com.hezron.smecommerce.tenant.internal.persistence.TenantBrandingRepository;
import com.hezron.smecommerce.tenant.internal.persistence.TenantEntity;
import com.hezron.smecommerce.tenant.internal.persistence.TenantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The shop profile the storefront loads first: name and look of one shop, found by its web name. */
@Service
public class ShopProfileService {

    private final TenantRepository tenants;
    private final TenantBrandingRepository brandings;

    ShopProfileService(TenantRepository tenants, TenantBrandingRepository brandings) {
        this.tenants = tenants;
        this.brandings = brandings;
    }

    /** @throws ShopNotFoundException if no shop has this slug, or the shop is suspended */
    @Transactional(readOnly = true)
    public ShopProfileResult getProfile(String slug) {
        TenantEntity tenant = tenants.findBySlug(TenantService.normaliseSlug(slug))
                .filter(TenantEntity::isActive)
                .orElseThrow(() -> new ShopNotFoundException(slug));
        // Branding's primary key is the shop's id, so findById is already "this shop's row".
        return brandings.findById(tenant.getId())
                .map(branding -> ShopProfileResult.fromEntities(tenant, branding))
                .orElseThrow(() -> new IllegalStateException("Shop " + tenant.getId() + " has no branding row"));
    }

}
