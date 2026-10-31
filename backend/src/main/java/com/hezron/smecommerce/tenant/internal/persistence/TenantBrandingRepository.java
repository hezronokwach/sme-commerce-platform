package com.hezron.smecommerce.tenant.internal.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantBrandingRepository extends JpaRepository<TenantBrandingEntity, UUID> {
}
