package com.hezron.smecommerce.catalog.internal.persistence;

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

/** Row in {@code catalog.categories}: a group of products on a shop's storefront menu. */
@Entity
@Table(schema = "catalog", name = "categories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA
public class CategoryEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    private String name;

    /** Unique within the shop, lowercase with hyphens, e.g. "fresh-vegetables". */
    private String slug;

    /** Position on the storefront menu; lower comes first. */
    @Column(name = "sort_order")
    private int sortOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Transient
    @Getter(AccessLevel.NONE)
    private boolean isNew = true;

    public static CategoryEntity newCategory(UUID tenantId, String name, String slug, int sortOrder) {
        Instant now = Instant.now();
        CategoryEntity category = new CategoryEntity();
        category.id = UUID.randomUUID();
        category.tenantId = tenantId;
        category.name = name;
        category.slug = slug;
        category.sortOrder = sortOrder;
        category.createdAt = now;
        category.updatedAt = now;
        return category;
    }

    /** Owner edits the category. The id and shop never change. */
    public void changeDetails(String name, String slug, int sortOrder) {
        this.name = name;
        this.slug = slug;
        this.sortOrder = sortOrder;
    }

    /** Written out only to say it's never null: every category gets its id in newCategory. */
    @Override
    public @NonNull UUID getId() {
        return id;
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
