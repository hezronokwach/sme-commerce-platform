package com.hezron.smecommerce.inventory.internal.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Row in {@code inventory.stock_items}: how many of one product a shop has. One row per product, so
 * its primary key is the catalog product's id.
 */
@Entity
@Table(schema = "inventory", name = "stock_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA
public class StockItemEntity implements Persistable<UUID> {

    /** New products warn the owner when 5 or fewer are left, until the owner picks another number. */
    static final int DEFAULT_LOW_STOCK_THRESHOLD = 5;

    @Id
    @Column(name = "product_id")
    private UUID productId;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    /** Physically in the shop. */
    @Column(name = "on_hand", nullable = false)
    private int onHand;

    /** Held for unpaid orders; part of onHand but not for sale. */
    @Column(nullable = false)
    private int reserved;

    @Column(name = "low_stock_threshold", nullable = false)
    private int lowStockThreshold;

    /**
     * Optimistic locking. Hibernate adds {@code AND version = ?} to every UPDATE, so of two checkouts
     * that both read "1 left", only the first can save. Never set in Java: Hibernate owns this number.
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

    /** A new product starts with nothing in stock. */
    public static StockItemEntity newStockItem(UUID tenantId, UUID productId) {
        Instant now = Instant.now();
        StockItemEntity item = new StockItemEntity();
        item.productId = productId;
        item.tenantId = tenantId;
        item.onHand = 0;
        item.reserved = 0;
        item.lowStockThreshold = DEFAULT_LOW_STOCK_THRESHOLD;
        item.createdAt = now;
        item.updatedAt = now;
        return item;
    }

    /**
     * Adds to (or, with a negative change, removes from) what is in the shop. The caller has checked
     * that on_hand stays at least as big as reserved; the database's CHECK refuses it otherwise.
     */
    public void changeOnHand(int change) {
        this.onHand = this.onHand + change;
    }

    /** True when available has dropped to the low-stock warning level (or below). */
    public boolean isLow() {
        return getAvailable() <= lowStockThreshold;
    }

    /** What can still be sold: in the shop, minus what unpaid orders are holding. */
    public int getAvailable() {
        return onHand - reserved;
    }

    /** The primary key is the product's id. Written out only to say it's never null. */
    @Override
    public @NonNull UUID getId() {
        return productId;
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
