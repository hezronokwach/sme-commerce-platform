package com.hezron.smecommerce.inventory;

import com.hezron.smecommerce.shared.TenantId;
import java.util.List;
import java.util.UUID;

/**
 * What other modules may ask of inventory (Guide 3.4). Ordering is the only caller:
 * <ul>
 *   <li>reserve: while placing an order, in ordering's transaction, so the order and its holds save together</li>
 *   <li>confirm: while marking the order PAID</li>
 *   <li>release: while cancelling or expiring an unpaid order</li>
 * </ul>
 * Inventory never listens to ordering's events; ordering calls these instead, which keeps the
 * dependency arrows pointing one way.
 */
public interface InventoryApi {

    /**
     * Holds stock for an unpaid order, for 15 minutes. All lines or none: if one line can't be held,
     * nothing is held.
     *
     * @throws InvalidReservationException  if there are no lines, a quantity under 1, or a product twice
     * @throws ProductNotStockedException  if a product has no stock record in this shop
     * @throws InsufficientStockException  if a product has fewer available than asked for
     */
    void reserve(TenantId tenantId, UUID orderId, List<ReservationLine> lines);

    /**
     * Gives an unpaid order's held stock back (cancelled or expired). Calling it twice changes nothing
     * the second time.
     */
    void release(TenantId tenantId, UUID orderId);

    /**
     * Turns the order's holds into a sale: the items leave on_hand, and the stock history gets a SALE row
     * with {@code orderReference} (the order number). Calling it twice changes nothing the second time.
     */
    void confirm(TenantId tenantId, UUID orderId, String orderReference);

}
