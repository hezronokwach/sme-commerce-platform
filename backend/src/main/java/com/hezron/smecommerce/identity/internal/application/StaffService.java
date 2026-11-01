package com.hezron.smecommerce.identity.internal.application;

import com.hezron.smecommerce.identity.internal.persistence.StaffUserRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Reading a shop's staff. Every method takes the caller's {@code tenantId} and passes it into the
 * query, so a shop only ever sees its own people.
 */
@Service
public class StaffService {

    private final StaffUserRepository staffUsers;

    StaffService(StaffUserRepository staffUsers) {
        this.staffUsers = staffUsers;
    }

    public List<StaffResult> list(UUID tenantId) {
        return staffUsers.findAllByTenantIdOrderByCreatedAtAsc(tenantId).stream()
                .map(StaffResult::fromEntity)
                .toList();
    }

    /** @throws StaffNotFoundException if the id is unknown or belongs to another shop */
    public StaffResult get(UUID tenantId, UUID staffId) {
        return staffUsers.findByIdAndTenantId(staffId, tenantId)
                .map(StaffResult::fromEntity)
                .orElseThrow(StaffNotFoundException::new);
    }

}
