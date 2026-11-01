package com.hezron.smecommerce.tenant.internal.application;

import com.hezron.smecommerce.shared.TenantId;
import com.hezron.smecommerce.tenant.ShopNotFoundException;
import com.hezron.smecommerce.tenant.SlugNotAllowedException;
import com.hezron.smecommerce.tenant.SlugTakenException;
import com.hezron.smecommerce.tenant.TenantApi;
import com.hezron.smecommerce.tenant.TenantCreatedEvent;
import com.hezron.smecommerce.tenant.internal.persistence.TenantBrandingEntity;
import com.hezron.smecommerce.tenant.internal.persistence.TenantBrandingRepository;
import com.hezron.smecommerce.tenant.internal.persistence.TenantEntity;
import com.hezron.smecommerce.tenant.internal.persistence.TenantRepository;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class TenantService implements TenantApi {

    /** Same rules as the database CHECK constraints, checked first so the user gets a clear message. */
    private static final Pattern SLUG_FORMAT = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");
    private static final int SLUG_MIN = 3;
    private static final int SLUG_MAX = 50;
    private static final Set<String> RESERVED_SLUGS = Set.of("admin", "api", "www", "s", "login");

    /** Name of the unique constraint on tenants.slug in V10. */
    private static final String SLUG_UNIQUE_CONSTRAINT = "uq_tenants_slug";

    private final TenantRepository tenants;
    private final TenantBrandingRepository brandings;
    private final ApplicationEventPublisher events;

    TenantService(TenantRepository tenants, TenantBrandingRepository brandings,
            ApplicationEventPublisher events) {
        this.tenants = tenants;
        this.brandings = brandings;
        this.events = events;
    }

    @Override
    @Transactional
    public TenantId createTenant(String shopName, String slug) {
        checkSlugAllowed(slug);
        if (tenants.existsBySlug(slug)) {
            throw new SlugTakenException(slug);
        }

        UUID id = UUID.randomUUID();
        try {
            // Flush now so a unique-constraint failure surfaces here, not at commit in the caller.
            tenants.saveAndFlush(new TenantEntity(id, slug, shopName));
        } catch (DataIntegrityViolationException e) {
            // Two sign-ups with the same slug at the same moment: both passed existsBySlug, one loses here.
            if (isSlugUniqueViolation(e)) {
                throw new SlugTakenException(slug);
            }
            throw e;
        }
        brandings.save(TenantBrandingEntity.defaultsFor(id));

        TenantId tenantId = TenantId.of(id);
        events.publishEvent(new TenantCreatedEvent(tenantId, slug, shopName));
        return tenantId;
    }

    @Override
    @Transactional(readOnly = true)
    public TenantId findActiveTenantIdBySlug(String slug) {
        return tenants.findBySlug(normaliseSlug(slug))
                .filter(TenantEntity::isActive)
                .map(tenant -> TenantId.of(tenant.getId()))
                .orElseThrow(() -> new ShopNotFoundException(slug));
    }

    /** Slugs are stored lowercase, so /s/Mama-Mboga and /s/mama-mboga find the same shop. */
    static String normaliseSlug(String slug) {
        return slug.strip().toLowerCase(Locale.ROOT);
    }

    private static void checkSlugAllowed(String slug) {
        if (slug == null || slug.length() < SLUG_MIN || slug.length() > SLUG_MAX) {
            throw new SlugNotAllowedException(slug, "it must be " + SLUG_MIN + " to " + SLUG_MAX + " characters");
        }
        if (!SLUG_FORMAT.matcher(slug).matches()) {
            throw new SlugNotAllowedException(slug, "use lowercase letters, digits and single hyphens");
        }
        if (RESERVED_SLUGS.contains(slug)) {
            throw new SlugNotAllowedException(slug, "it is reserved");
        }
    }

    private static boolean isSlugUniqueViolation(DataIntegrityViolationException e) {
        String message = e.getMostSpecificCause().getMessage();
        return message != null && message.contains(SLUG_UNIQUE_CONSTRAINT);
    }

}
