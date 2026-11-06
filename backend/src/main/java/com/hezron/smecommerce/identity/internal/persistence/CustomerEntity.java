package com.hezron.smecommerce.identity.internal.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Row in {@code identity.customers}: someone who bought from one shop, found by their phone number.
 * The same phone at two shops gives two separate rows. Guests have no password; customer accounts
 * (workflow W9) come later, which is why email and password_hash aren't mapped yet.
 * <p>
 * Rows are created by CustomerRepository.insertIfMissing (plain SQL), never by save(), so this entity
 * only needs to be read and have its name changed.
 */
@Entity
@Table(schema = "identity", name = "customers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA
public class CustomerEntity {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    /** In 2547XXXXXXXX form. Never write it to the logs in full. */
    @Column(nullable = false, updatable = false)
    private String phone;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** The customer typed a different name this time (for example a full name instead of a nickname). */
    public void changeName(String newName) {
        this.fullName = newName;
    }

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

}
