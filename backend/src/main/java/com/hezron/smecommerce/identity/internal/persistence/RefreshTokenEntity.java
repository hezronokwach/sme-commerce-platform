package com.hezron.smecommerce.identity.internal.persistence;

import com.hezron.smecommerce.identity.internal.domain.SubjectType;
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
import org.springframework.data.domain.Persistable;

/**
 * Row in {@code identity.refresh_tokens}: one refresh token, stored only as its SHA-256 hash.
 * <p>
 * Every login starts a new family. Each refresh marks the current row used and adds the next one to
 * the same family, so a reused token can be traced to its family and the whole family revoked.
 */
@Entity
@Table(schema = "identity", name = "refresh_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA
public class RefreshTokenEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "subject_type", nullable = false, updatable = false)
    private SubjectType subjectType;

    @Column(name = "subject_id", nullable = false, updatable = false)
    private UUID subjectId;

    @Column(name = "token_hash", nullable = false, updatable = false)
    private String tokenHash;

    @Column(name = "family_id", nullable = false, updatable = false)
    private UUID familyId;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    // used_at and revoked_at are never set in Java: Hibernate fills them when loading, and only the
    // single-statement updates in RefreshTokenRepository write them (the JPQL there needs them mapped).
    @SuppressWarnings("unused")
    @Column(name = "used_at")
    private Instant usedAt;

    @SuppressWarnings("unused")
    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Transient
    @Getter(AccessLevel.NONE)
    private boolean isNew = true;

    public static RefreshTokenEntity newToken(UUID tenantId, SubjectType subjectType, UUID subjectId,
            String tokenHash, UUID familyId, Instant expiresAt) {
        RefreshTokenEntity token = new RefreshTokenEntity();
        token.id = UUID.randomUUID();
        token.tenantId = tenantId;
        token.subjectType = subjectType;
        token.subjectId = subjectId;
        token.tokenHash = tokenHash;
        token.familyId = familyId;
        token.expiresAt = expiresAt;
        token.createdAt = Instant.now();
        return token;
    }

    /** Not revoked and not expired. Whether it was already used is checked separately (that's reuse). */
    public boolean isActiveAt(Instant now) {
        return revokedAt == null && now.isBefore(expiresAt);
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
