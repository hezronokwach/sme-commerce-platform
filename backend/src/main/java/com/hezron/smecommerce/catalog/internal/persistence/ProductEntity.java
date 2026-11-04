package com.hezron.smecommerce.catalog.internal.persistence;

import com.hezron.smecommerce.catalog.internal.domain.ProductStatus;
import com.hezron.smecommerce.shared.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Persistable;

/**
 * Row in {@code catalog.products}: something a shop sells.
 * <p>
 * {@code categoryId} is a plain column, not a {@code @ManyToOne}: the catalog code always knows when it
 * needs the category and loads it itself, so there are no hidden extra queries.
 */
@Entity
@Table(schema = "catalog", name = "products")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA
public class ProductEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    /** Null when the product isn't in any category. */
    @Column(name = "category_id")
    private UUID categoryId;

    private String name;

    /** Unique within the shop; used in the product page address. */
    private String slug;

    private String description;

    @Column(name = "price_minor", nullable = false)
    private long priceMinor;

    private String currency;

    @Column(name = "image_url")
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    private ProductStatus status;

    /**
     * Optimistic locking. Hibernate adds {@code AND version = ?} to every UPDATE and bumps the number.
     * If someone else saved first, no row matches and Hibernate throws instead of overwriting their change.
     * Never set in Java: Hibernate owns this number.
     */
    @SuppressWarnings("unused")
    @Version
    private int version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Transient
    @Getter(AccessLevel.NONE)
    private boolean isNew = true;

    public static ProductEntity newProduct(UUID tenantId, UUID categoryId, String name, String slug,
            String description, Money price, String imageUrl, ProductStatus status) {
        Instant now = Instant.now();
        ProductEntity product = new ProductEntity();
        product.id = UUID.randomUUID();
        product.tenantId = tenantId;
        product.categoryId = categoryId;
        product.name = name;
        product.slug = slug;
        product.description = description;
        product.priceMinor = price.amountMinor();
        product.currency = price.currency();
        product.imageUrl = imageUrl;
        product.status = status;
        product.createdAt = now;
        product.updatedAt = now;
        return product;
    }

    /** The two money columns as one value. */
    public Money getPrice() {
        return Money.of(priceMinor, currency);
    }

    /** Written out only to say it's never null: every product gets its id in newProduct. */
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
