package com.hezron.smecommerce.ordering.internal.persistence;

import com.hezron.smecommerce.ordering.internal.domain.OrderStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Every query names the shop (ADR 0005): don't use the built-in findById / findAll, they cover every shop.
 * <p>
 * The list queries take a Pageable (which page, how many per page, sorted how) and return a Page: that
 * page's orders plus the total count, which Spring Data gets with a second COUNT query.
 */
public interface OrderRepository extends JpaRepository<OrderEntity, UUID> {

    /** {@code WHERE id = ? AND tenant_id = ?}: empty if the order belongs to another shop. */
    Optional<OrderEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    /** An order by its readable number, such as MM-000123, within one shop. */
    Optional<OrderEntity> findByTenantIdAndOrderNumber(UUID tenantId, String orderNumber);

    /** One page of the shop's orders. */
    Page<OrderEntity> findAllByTenantId(UUID tenantId, Pageable pageable);

    /** One page of the shop's orders in one status. */
    Page<OrderEntity> findAllByTenantIdAndStatus(UUID tenantId, OrderStatus status, Pageable pageable);

    /**
     * The same order, LOCKED until the transaction ends ({@code SELECT ... FOR UPDATE}). Used by the two
     * things that may change an unpaid order at the same moment, the expiry job and a payment result, so
     * one waits for the other instead of both acting on the old status.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM OrderEntity o WHERE o.id = :id AND o.tenantId = :tenantId")
    Optional<OrderEntity> lockByIdAndTenantId(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

    /**
     * Unpaid orders past their deadline, oldest deadline first, for the expiry job. This one spans every
     * shop on purpose: it's the system's own job, not a shop's request. Each row carries its tenant_id,
     * and everything done with it afterwards uses that.
     */
    @Query("""
            SELECT o FROM OrderEntity o
            WHERE o.status = :status AND o.expiresAt IS NOT NULL AND o.expiresAt < :now
            ORDER BY o.expiresAt
            """)
    List<OrderEntity> findPastDeadline(@Param("status") OrderStatus status, @Param("now") Instant now,
            Pageable page);

}
