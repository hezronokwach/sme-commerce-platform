package com.hezron.smecommerce.identity.internal.persistence;

import com.hezron.smecommerce.identity.internal.domain.StaffRole;
import com.hezron.smecommerce.identity.internal.domain.StaffStatus;
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
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Persistable;

/**
 * Row in {@code identity.staff_users}: a shop owner or staff member who uses the admin.
 * <p>
 * {@code tenant_id} is a plain column, not a relation: the shop lives in the tenant module's schema,
 * and modules only store each other's ids.
 */
@Entity
@Table(schema = "identity", name = "staff_users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA
public class StaffUserEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    /** Always lowercase, so lookups and the unique index agree. */
    private String email;

    @Column(name = "full_name")
    private String fullName;

    /** bcrypt hash with its {@code {bcrypt}} prefix. Never the password itself. */
    @Column(name = "password_hash")
    private String passwordHash;

    // Stored by name ("OWNER"), never by position: reordering the enum must not change saved data.
    @Enumerated(EnumType.STRING)
    private StaffRole role;

    @Enumerated(EnumType.STRING)
    private StaffStatus status;

    /** Wrong passwords in a row. Reset by a successful login. */
    @Column(name = "failed_logins")
    private int failedLogins;

    /** Set once failedLogins reaches the limit; login is refused until then. Added by V21. */
    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Transient
    @Getter(AccessLevel.NONE)
    private boolean isNew = true;

    /** The person who signs a shop up: an ACTIVE owner of that shop. */
    public static StaffUserEntity newOwner(UUID tenantId, String email, String fullName, String passwordHash) {
        Instant now = Instant.now();
        StaffUserEntity user = new StaffUserEntity();
        user.id = UUID.randomUUID();
        user.tenantId = tenantId;
        user.email = email;
        user.fullName = fullName;
        user.passwordHash = passwordHash;
        user.role = StaffRole.OWNER;
        user.status = StaffStatus.ACTIVE;
        user.failedLogins = 0;
        user.createdAt = now;
        user.updatedAt = now;
        return user;
    }

    public boolean isLockedAt(Instant now) {
        return lockedUntil != null && now.isBefore(lockedUntil);
    }

    /** Counts a wrong password and locks the account once the count reaches {@code maxAttempts}. */
    public void recordFailedLogin(Instant now, int maxAttempts, Duration lockFor) {
        if (lockedUntil != null && !now.isBefore(lockedUntil)) {
            // An earlier lock has run out: the user gets a fresh set of attempts.
            failedLogins = 0;
            lockedUntil = null;
        }
        failedLogins++;
        if (failedLogins >= maxAttempts) {
            lockedUntil = now.plus(lockFor);
        }
    }

    public void recordSuccessfulLogin(Instant now) {
        failedLogins = 0;
        lockedUntil = null;
        lastLoginAt = now;
    }

    /**
     * Written out (Lombok would generate it) only to say it's never null: Persistable declares getId()
     * as nullable, but every staff user gets its id in newOwner.
     */
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
