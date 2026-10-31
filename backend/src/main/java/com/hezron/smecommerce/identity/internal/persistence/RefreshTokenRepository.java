package com.hezron.smecommerce.identity.internal.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, UUID> {

    Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);

    /**
     * Marks the token used, but only if nobody did already. One SQL statement, so of two requests
     * refreshing with the same token at the same moment, exactly one gets 1 back.
     *
     * @return 1 if this call used the token, 0 if it had been used before
     */
    @Modifying
    @Query("UPDATE RefreshTokenEntity t SET t.usedAt = :now WHERE t.id = :id AND t.usedAt IS NULL")
    int markUsed(UUID id, Instant now);

    /** Revokes every token of one login session (logout, or a reused token). */
    @Modifying
    @Query("UPDATE RefreshTokenEntity t SET t.revokedAt = :now WHERE t.familyId = :familyId AND t.revokedAt IS NULL")
    void revokeFamily(UUID familyId, Instant now);

}
