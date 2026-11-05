package com.hezron.smecommerce.inventory.internal.persistence;

import com.hezron.smecommerce.inventory.internal.domain.ReservationStatus;
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
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Persistable;

/**
 * Row in {@code inventory.reservations}: some items of one product held for one unpaid order.
 * An order with three products has three rows.
 */
@Entity
@Table(schema = "inventory", name = "reservations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA
public class ReservationEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    /** The ordering module's order id. */
    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;

    @Column(nullable = false, updatable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Transient
    @Getter(AccessLevel.NONE)
    private boolean isNew = true;

    public static ReservationEntity newHold(UUID tenantId, UUID orderId, UUID productId, int quantity,
            Instant expiresAt) {
        Instant now = Instant.now();
        ReservationEntity reservation = new ReservationEntity();
        reservation.id = UUID.randomUUID();
        reservation.tenantId = tenantId;
        reservation.orderId = orderId;
        reservation.productId = productId;
        reservation.quantity = quantity;
        reservation.status = ReservationStatus.HELD;
        reservation.expiresAt = expiresAt;
        reservation.createdAt = now;
        reservation.updatedAt = now;
        return reservation;
    }

    /** The order was cancelled or expired: the items went back to available. */
    public void markReleased() {
        this.status = ReservationStatus.RELEASED;
    }

    /** The order was paid: the items are sold. */
    public void markConfirmed() {
        this.status = ReservationStatus.CONFIRMED;
    }

    /** Written out only to say it's never null: every reservation gets its id in newHold. */
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
