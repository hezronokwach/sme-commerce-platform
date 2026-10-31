package com.hezron.smecommerce.identity.internal.application;

import com.hezron.smecommerce.identity.internal.persistence.StaffUserEntity;
import com.hezron.smecommerce.identity.internal.persistence.StaffUserRepository;
import com.hezron.smecommerce.shared.TenantId;
import com.hezron.smecommerce.tenant.TenantApi;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
class AuthServiceImpl implements AuthService {

    /** Name of the unique constraint on staff_users.email in V20. */
    private static final String EMAIL_UNIQUE_CONSTRAINT = "uq_staff_users_email";

    private final StaffUserRepository staffUsers;
    private final TenantApi tenants;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate transaction;

    AuthServiceImpl(StaffUserRepository staffUsers, TenantApi tenants, PasswordEncoder passwordEncoder,
            TransactionTemplate transaction) {
        this.staffUsers = staffUsers;
        this.tenants = tenants;
        this.passwordEncoder = passwordEncoder;
        this.transaction = transaction;
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
            return new SignUpResult(owner.getId(), tenantId);
        });
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
