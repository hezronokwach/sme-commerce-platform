package com.hezron.smecommerce.identity.internal.application;

import com.hezron.smecommerce.identity.internal.domain.StaffStatus;
import com.hezron.smecommerce.identity.internal.persistence.StaffUserEntity;
import com.hezron.smecommerce.identity.internal.persistence.StaffUserRepository;
import com.hezron.smecommerce.shared.TenantId;
import com.hezron.smecommerce.tenant.TenantApi;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
class AuthServiceImpl implements AuthService {

    /** Name of the unique constraint on staff_users.email in V20. */
    private static final String EMAIL_UNIQUE_CONSTRAINT = "uq_staff_users_email";

    static final int MAX_FAILED_LOGINS = 5;
    static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final StaffUserRepository staffUsers;
    private final TenantApi tenants;
    private final TokenService tokens;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate transaction;

    /** Checked against when the email is unknown, so that path costs one bcrypt check like the others. */
    private final String dummyPasswordHash;

    AuthServiceImpl(StaffUserRepository staffUsers, TenantApi tenants, TokenService tokens,
            PasswordEncoder passwordEncoder, TransactionTemplate transaction) {
        this.staffUsers = staffUsers;
        this.tenants = tenants;
        this.tokens = tokens;
        this.passwordEncoder = passwordEncoder;
        this.transaction = transaction;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Override
    public SignUpResult signUp(SignUpCommand command) {
        String email = normaliseEmail(command.email());

        // Cheap check first, so a typo'd second sign-up fails fast. The unique index below is the real guard.
        if (staffUsers.existsByEmail(email)) {
            throw new EmailTakenException();
        }

        // bcrypt is slow on purpose (~100 ms). Hash before the transaction so no database
        // connection is held open while the CPU works.
        String passwordHash = passwordEncoder.encode(command.password());

        // Everything inside commits together. If saving the owner fails, the shop is rolled back too,
        // so there is never a shop without an owner.
        // "_" (Java 22+): the transaction status is passed in, but this code doesn't need it.
        return transaction.execute(_ -> {
            TenantId tenantId = tenants.createTenant(command.shopName(), command.shopSlug());

            StaffUserEntity owner = StaffUserEntity.newOwner(
                    tenantId.value(), email, command.ownerName().strip(), passwordHash);
            try {
                staffUsers.saveAndFlush(owner);
            } catch (DataIntegrityViolationException e) {
                // Two sign-ups with the same email at the same moment: one passed the check above too late.
                if (isEmailUniqueViolation(e)) {
                    throw new EmailTakenException();
                }
                throw e;
            }
            // A new owner starts their first login session (a new token family).
            return new SignUpResult(owner.getId(), tenantId, tokens.issue(owner, UUID.randomUUID()));
        });
    }

    @Override
    public AuthTokens login(LoginCommand command) {
        Optional<StaffUserEntity> found = staffUsers.findByEmail(normaliseEmail(command.email()));

        // Exactly one bcrypt check on every path. Skipping it for unknown emails would make those answers
        // ~100 ms faster, and the speed alone would tell an attacker which emails have accounts.
        String hash = found.map(StaffUserEntity::getPasswordHash).orElse(dummyPasswordHash);
        boolean passwordMatches = passwordEncoder.matches(command.password(), hash);

        StaffUserEntity user = found
                .filter(candidate -> candidate.getStatus() == StaffStatus.ACTIVE)
                .orElseThrow(InvalidCredentialsException::new);

        Instant now = Instant.now();
        // Checked before the password result, so a locked account can't be used to test guesses.
        if (user.isLockedAt(now)) {
            throw new TooManyAttemptsException();
        }

        if (!passwordMatches) {
            // Saved in its own transaction, then we throw: the count must stick even though login fails.
            transaction.executeWithoutResult(_ -> {
                user.recordFailedLogin(now, MAX_FAILED_LOGINS, LOCK_DURATION);
                staffUsers.save(user);
            });
            throw new InvalidCredentialsException();
        }

        return transaction.execute(_ -> {
            user.recordSuccessfulLogin(now);
            staffUsers.save(user);
            return tokens.issue(user, UUID.randomUUID());
        });
    }

    @Override
    public AuthTokens refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new InvalidRefreshTokenException();
        }
        return tokens.rotate(refreshToken);
    }

    @Override
    public void logout(String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            tokens.revokeFamilyOf(refreshToken);
        }
    }

    /** Emails are compared case-insensitively: Wanjiru@Example.com and wanjiru@example.com are one account. */
    private static String normaliseEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    private static boolean isEmailUniqueViolation(DataIntegrityViolationException e) {
        String message = e.getMostSpecificCause().getMessage();
        return message != null && message.contains(EMAIL_UNIQUE_CONSTRAINT);
    }

}
