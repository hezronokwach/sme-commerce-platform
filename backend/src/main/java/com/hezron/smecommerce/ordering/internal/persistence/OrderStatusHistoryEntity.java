package com.hezron.smecommerce.ordering.internal.persistence;

import com.hezron.smecommerce.ordering.internal.domain.ActorType;
import com.hezron.smecommerce.ordering.internal.domain.OrderStatus;
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
 * Row in {@code ordering.order_status_history}: one status change of an order, with why and who.
 * Rows are only ever added.
 */
@Entity
@Table(schema = "ordering", name = "order_status_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA
public class OrderStatusHistoryEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    /** Null for the first row. */
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", updatable = false)
    private OrderStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, updatable = false)
    private OrderStatus toStatus;

    @Column(updatable = false)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, updatable = false)
    private ActorType actorType;

    /** Null when the system did it. */
    @Column(name = "actor_id", updatable = false)
    private UUID actorId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Transient
    @Getter(AccessLevel.NONE)
    private boolean isNew = true;

    public static OrderStatusHistoryEntity newEntry(UUID tenantId, UUID orderId, OrderStatus fromStatus,
            OrderStatus toStatus, String reason, ActorType actorType, UUID actorId) {
        OrderStatusHistoryEntity entry = new OrderStatusHistoryEntity();
        entry.id = UUID.randomUUID();
        entry.tenantId = tenantId;
        entry.orderId = orderId;
        entry.fromStatus = fromStatus;
        entry.toStatus = toStatus;
        entry.reason = reason;
        entry.actorType = actorType;
        entry.actorId = actorId;
        entry.createdAt = Instant.now();
        return entry;
    }

    /** Written out only to say it's never null: every entry gets its id in newEntry. */
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
