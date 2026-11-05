package com.hezron.smecommerce.inventory.internal.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * The stock history. Only ever add rows (save); never update or delete them. Every query names the
 * shop (ADR 0005); the history queries are added with the stock endpoints.
 */
public interface StockMovementRepository extends JpaRepository<StockMovementEntity, UUID> {
}
