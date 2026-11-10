package com.hezron.smecommerce.notification.internal.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/** Row in {@code notification.stream_tickets}: a one-time pass to watch one order live. */
@Entity
@Table(schema = "notification", name = "stream_tickets")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA
public class StreamTicketEntity implements Persistable<String> {

    /** SHA-256 of the ticket. The id we choose ourselves, which is why isNew() is written out below. */
    @Id
    @Column(name = "ticket_hash")
    private String ticketHash;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Transient
    @Getter(AccessLevel.NONE)
    private boolean isNew = true;

    public static StreamTicketEntity newTicket(String ticketHash, UUID tenantId, UUID orderId, Instant expiresAt) {
        StreamTicketEntity ticket = new StreamTicketEntity();
        ticket.ticketHash = ticketHash;
        ticket.tenantId = tenantId;
        ticket.orderId = orderId;
        ticket.expiresAt = expiresAt;
        ticket.createdAt = Instant.now();
        return ticket;
    }

    @Override
    public @NonNull String getId() {
        return ticketHash;
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
