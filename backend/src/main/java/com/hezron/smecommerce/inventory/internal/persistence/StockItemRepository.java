package com.hezron.smecommerce.inventory.internal.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/**
 * Every query names the shop (ADR 0005): don't use the built-in findById / findAll, they cover every shop.
 * <p>
 * The three UPDATEs below change stock for orders in one SQL statement each, with the rule in the WHERE.
 * Postgres locks the row while updating it: if two orders want the last item at the same moment, the
 * second waits, then checks the WHERE again against the new numbers and changes nothing. So overselling
 * is impossible without any retry. Each returns how many rows it changed: 1 means done, 0 means the
 * rule said no (or the product isn't in this shop).
 * <p>
 * They also add 1 to version, so an admin restock that loaded the row earlier still sees the conflict.
 * {@code clearAutomatically}: entities loaded before the UPDATE are forgotten, so the next read is fresh.
 */
public interface StockItemRepository extends JpaRepository<StockItemEntity, UUID> {

    /** {@code WHERE product_id = ? AND tenant_id = ?}: empty if the product belongs to another shop. */
    Optional<StockItemEntity> findByProductIdAndTenantId(UUID productId, UUID tenantId);

    /** Holds {@code quantity} more, but only if that many are available (on_hand - reserved). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE StockItemEntity s
            SET s.reserved = s.reserved + :quantity, s.version = s.version + 1, s.updatedAt = :now
            WHERE s.productId = :productId AND s.tenantId = :tenantId AND s.onHand - s.reserved >= :quantity
            """)
    int reserveIfAvailable(UUID tenantId, UUID productId, int quantity, Instant now);

    /** Gives {@code quantity} held items back to available (an unpaid order was cancelled or expired). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE StockItemEntity s
            SET s.reserved = s.reserved - :quantity, s.version = s.version + 1, s.updatedAt = :now
            WHERE s.productId = :productId AND s.tenantId = :tenantId AND s.reserved >= :quantity
            """)
    int releaseHeld(UUID tenantId, UUID productId, int quantity, Instant now);

    /** Turns {@code quantity} held items into a sale: they leave both reserved and on_hand. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE StockItemEntity s
            SET s.reserved = s.reserved - :quantity, s.onHand = s.onHand - :quantity,
                s.version = s.version + 1, s.updatedAt = :now
            WHERE s.productId = :productId AND s.tenantId = :tenantId AND s.reserved >= :quantity
            """)
    int sellHeld(UUID tenantId, UUID productId, int quantity, Instant now);

}
