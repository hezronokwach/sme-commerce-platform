package com.hezron.smecommerce.tenant.internal.persistence;

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

/**
 * One way of paying a shop offers, a row in {@code tenant.tenant_payment_providers}: FAKE, CARD or
 * MPESA, turned on or off by the owner.
 * <p>
 * public_config and secret_config aren't mapped yet: there are no per-shop settings until each shop
 * gets its own provider keys (a later issue). The database fills public_config with its default '{}'.
 */
@Entity
@Table(schema = "tenant", name = "tenant_payment_providers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA
public class PaymentMethodEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    /** FAKE, CARD or MPESA (the database checks it). */
    @Column(nullable = false, updatable = false)
    private String provider;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Transient
    @Getter(AccessLevel.NONE)
    private boolean isNew = true;

    public static PaymentMethodEntity newMethod(UUID tenantId, String provider, String displayName, boolean enabled) {
        PaymentMethodEntity method = new PaymentMethodEntity();
        method.id = UUID.randomUUID();
        method.tenantId = tenantId;
        method.provider = provider;
        method.displayName = displayName;
        method.enabled = enabled;
        method.createdAt = Instant.now();
        method.updatedAt = method.createdAt;
        return method;
    }

    public void turnOn() {
        this.enabled = true;
        this.updatedAt = Instant.now();
    }

    public void turnOff() {
        this.enabled = false;
        this.updatedAt = Instant.now();
    }

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
