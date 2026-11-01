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
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Persistable;

/**
 * Row in {@code tenant.tenants}. Never leaves the tenant module: other modules get a {@code TenantId}.
 * <p>
 * Implements {@link Persistable} because the id is set in Java before saving. Without it Spring Data
 * sees a non-null id, assumes the row exists, and runs a SELECT before every INSERT.
 */
@Entity
@Table(schema = "tenant", name = "tenants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA needs it; protected so our code can't build an empty tenant
public class TenantEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    private String slug;

    private String name;

    private String status;

    private String plan;

    private String currency;

    // contact_phone and contact_email are not mapped yet: nothing sets them until shop settings exist,
    // and unmapped nullable columns are simply left null on INSERT.

    // JPA writes every mapped column on INSERT, so the database defaults never apply; set them here instead.
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Transient
    @Getter(AccessLevel.NONE)
    private boolean isNew = true;

    public TenantEntity(UUID id, String slug, String name) {
        Instant now = Instant.now();
        this.id = id;
        this.slug = slug;
        this.name = name;
        this.status = "ACTIVE";
        this.plan = "FREE";
        this.currency = "KES";
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * Written out (Lombok would generate it) only to say it's never null: Persistable declares getId()
     * as nullable, but every shop gets its id in the constructor.
     */
    @Override
    public @NonNull UUID getId() {
        return id;
    }

    /** Open for business. A SUSPENDED shop keeps its data but its storefront is closed. */
    public boolean isActive() {
        return "ACTIVE".equals(status);
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
