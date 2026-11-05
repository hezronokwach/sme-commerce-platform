package com.hezron.smecommerce.inventory.internal.persistence;

import com.hezron.smecommerce.inventory.internal.domain.MovementReason;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
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
 * Row in {@code inventory.stock_movements}: one change to a product's on_hand ("restock +40 by
 * Wanjiru"). Rows are only ever added: every column is {@code updatable = false}, and there is no
 * method to change one.
 */
@Entity
@Table(schema = "inventory", name = "stock_movements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA
public class StockMovementEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;

    /** +40 for a restock, -2 for a sale. */
    @Column(nullable = false, updatable = false)
    private int change;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private MovementReason reason;

    /** Order number or the owner's note; may be null. */
    @Column(updatable = false)
    private String reference;

    /** The staff user who made the change, or null when the system did (a sale). */
    @Column(name = "actor_id", updatable = false)
    private UUID actorId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Transient
    @Getter(AccessLevel.NONE)
    private boolean isNew = true;

    public static StockMovementEntity newMovement(UUID tenantId, UUID productId, int change,
            MovementReason reason, String reference, UUID actorId) {
        StockMovementEntity movement = new StockMovementEntity();
        movement.id = UUID.randomUUID();
        movement.tenantId = tenantId;
        movement.productId = productId;
        movement.change = change;
        movement.reason = reason;
        movement.reference = reference;
        movement.actorId = actorId;
        movement.createdAt = Instant.now();
        return movement;
    }

    /** Written out only to say it's never null: every movement gets its id in newMovement. */
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

}
