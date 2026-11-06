package com.hezron.smecommerce.identity.internal.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/** Every query names the shop (ADR 0005). */
public interface CustomerRepository extends JpaRepository<CustomerEntity, UUID> {

    /** {@code WHERE tenant_id = ? AND phone = ?}: the unique index uq_customers_tenant_phone finds it. */
    Optional<CustomerEntity> findByTenantIdAndPhone(UUID tenantId, String phone);

    /**
     * Adds the customer unless this shop already has one with this phone. Plain SQL, because of
     * "ON CONFLICT DO NOTHING": if two first orders from the same phone arrive at the same moment, the
     * second insert is quietly skipped instead of failing with a duplicate-key error, which would undo
     * that customer's whole order.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(nativeQuery = true, value = """
            INSERT INTO identity.customers (id, tenant_id, phone, full_name, created_at, updated_at)
            VALUES (:id, :tenantId, :phone, :fullName, :now, :now)
            ON CONFLICT (tenant_id, phone) DO NOTHING
            """)
    int insertIfMissing(UUID id, UUID tenantId, String phone, String fullName, Instant now);

}
