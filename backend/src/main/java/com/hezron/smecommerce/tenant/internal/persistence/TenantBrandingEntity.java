package com.hezron.smecommerce.tenant.internal.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

/** Row in {@code tenant.tenant_branding}: one per shop, keyed by the shop's id. */
@Entity
@Table(schema = "tenant", name = "tenant_branding")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA
public class TenantBrandingEntity implements Persistable<UUID> {

    /** Colours a new shop starts with until the owner picks its own. */
    static final String DEFAULT_PRIMARY_COLOR = "#0F766E";
    static final String DEFAULT_ACCENT_COLOR = "#F59E0B";

    @Id
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "primary_color")
    private String primaryColor;

    @Column(name = "accent_color")
    private String accentColor;

    // logo_url and tagline are only read for now (the storefront profile shows them). Nothing sets them
    // until shop settings exist, so they stay null; Hibernate fills them when loading.
    @SuppressWarnings("unused")
    @Column(name = "logo_url")
    private String logoUrl;

    @SuppressWarnings("unused")
    private String tagline;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Transient
    @Getter(AccessLevel.NONE)
    private boolean isNew = true;

    public static TenantBrandingEntity defaultsFor(UUID tenantId) {
        TenantBrandingEntity branding = new TenantBrandingEntity();
        branding.tenantId = tenantId;
        branding.primaryColor = DEFAULT_PRIMARY_COLOR;
        branding.accentColor = DEFAULT_ACCENT_COLOR;
        branding.updatedAt = Instant.now();
        return branding;
    }

    /** {@link Persistable} asks for the id; for branding it is the shop's id. */
    @Override
    public UUID getId() {
        return tenantId;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

}
