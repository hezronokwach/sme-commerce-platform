package com.hezron.smecommerce.identity.internal.application;

import com.hezron.smecommerce.identity.internal.domain.StaffStatus;
import com.hezron.smecommerce.identity.internal.domain.SubjectType;
import com.hezron.smecommerce.identity.internal.persistence.RefreshTokenEntity;
import com.hezron.smecommerce.identity.internal.persistence.RefreshTokenRepository;
import com.hezron.smecommerce.identity.internal.persistence.StaffUserEntity;
import com.hezron.smecommerce.identity.internal.persistence.StaffUserRepository;
import com.hezron.smecommerce.identity.internal.security.JwtProperties;
import com.hezron.smecommerce.identity.internal.security.TokenClaims;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues, rotates and revokes staff tokens (Guide 2.2).
 * <p>
 * Access token: a signed JWT the API checks on every request without a database lookup. Refresh token:
 * 32 random bytes that can be exchanged once for a new pair. Only its SHA-256 hash is stored, so a
 * leaked database can't be used to log in.
 */
@Service
class TokenService {

    private static final int REFRESH_TOKEN_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwt;
    private final RefreshTokenRepository refreshTokens;
    private final StaffUserRepository staffUsers;

    TokenService(JwtEncoder jwtEncoder, JwtProperties jwt, RefreshTokenRepository refreshTokens,
            StaffUserRepository staffUsers) {
        this.jwtEncoder = jwtEncoder;
        this.jwt = jwt;
        this.refreshTokens = refreshTokens;
        this.staffUsers = staffUsers;
    }

    /**
     * Signs an access token and stores a new refresh token in {@code familyId}.
     * Pass a new family id for a login, and the current one for a refresh.
     */
    @Transactional
    AuthTokens issue(StaffUserEntity user, UUID familyId) {
        Instant now = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwt.issuer())
                .subject(user.getId().toString())
                .claim(TokenClaims.TENANT_ID, user.getTenantId().toString())
                .claim(TokenClaims.ROLE, user.getRole().name())
                .claim(TokenClaims.SUBJECT_TYPE, SubjectType.STAFF.name())
                .issuedAt(now)
                .expiresAt(now.plus(jwt.accessTtl()))
                // Unique id per token, so a single token can be traced or blocked later.
                .id(UUID.randomUUID().toString())
                .build();
        // The header must name the algorithm, or the encoder can't pick our HMAC key.
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String accessToken = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        String refreshToken = newRefreshToken();
        refreshTokens.save(RefreshTokenEntity.newToken(user.getTenantId(), SubjectType.STAFF, user.getId(),
                sha256Hex(refreshToken), familyId, now.plus(jwt.refreshTtl())));

        return new AuthTokens(accessToken, jwt.accessTtl(), refreshToken, jwt.refreshTtl());
    }

    /**
     * Exchanges a refresh token for a new pair in the same family. A token that was already used means
     * two parties hold it (one of them likely stole it), so the whole family is revoked.
     * <p>
     * {@code noRollbackFor}: the revoke must be saved even though we then throw. By default an exception
     * rolls the transaction back, which would undo the revoke.
     */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    AuthTokens rotate(String refreshToken) {
        Instant now = Instant.now();
        RefreshTokenEntity current = refreshTokens.findByTokenHash(sha256Hex(refreshToken))
                .filter(token -> token.isActiveAt(now))
                .filter(token -> token.getSubjectType() == SubjectType.STAFF)
                .orElseThrow(InvalidRefreshTokenException::new);

        if (refreshTokens.markUsed(current.getId(), now) == 0) {
            refreshTokens.revokeFamily(current.getFamilyId(), now);
            throw new InvalidRefreshTokenException();
        }

        // Load the user again: a disabled user must not get new tokens, and a changed role takes effect here.
        StaffUserEntity user = staffUsers.findById(current.getSubjectId())
                .filter(found -> found.getStatus() == StaffStatus.ACTIVE)
                .orElse(null);
        if (user == null) {
            refreshTokens.revokeFamily(current.getFamilyId(), now);
            throw new InvalidRefreshTokenException();
        }
        return issue(user, current.getFamilyId());
    }

    /** Ends the login session the token belongs to. Unknown tokens are ignored: logout always succeeds. */
    @Transactional
    void revokeFamilyOf(String refreshToken) {
        refreshTokens.findByTokenHash(sha256Hex(refreshToken))
                .ifPresent(token -> refreshTokens.revokeFamily(token.getFamilyId(), Instant.now()));
    }

    /** 32 random bytes as URL-safe text (43 characters), so it fits in a cookie as is. */
    private static String newRefreshToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * A plain hash is enough here (no bcrypt): the token is 256 random bits, so there's nothing to guess,
     * and a fast hash lets us find the row with the unique index.
     */
    private static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // Every Java runtime must ship SHA-256, so this can't happen.
            throw new IllegalStateException(e);
        }
    }

}
