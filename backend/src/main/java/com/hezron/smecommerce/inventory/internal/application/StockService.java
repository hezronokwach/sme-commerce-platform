package com.hezron.smecommerce.inventory.internal.application;

import com.hezron.smecommerce.inventory.StockLowEvent;
import com.hezron.smecommerce.inventory.internal.domain.MovementReason;
import com.hezron.smecommerce.inventory.internal.persistence.StockItemEntity;
import com.hezron.smecommerce.inventory.internal.persistence.StockItemRepository;
import com.hezron.smecommerce.inventory.internal.persistence.StockMovementEntity;
import com.hezron.smecommerce.inventory.internal.persistence.StockMovementRepository;
import com.hezron.smecommerce.shared.TenantId;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** A shop's stock levels. Every method takes the shop's {@code tenantId} and passes it into each query. */
@Service
public class StockService {

    private final StockItemRepository stockItems;
    private final StockMovementRepository movements;
    private final ApplicationEventPublisher events;

    StockService(StockItemRepository stockItems, StockMovementRepository movements,
            ApplicationEventPublisher events) {
        this.stockItems = stockItems;
        this.movements = movements;
        this.events = events;
    }

    /**
     * Gives a new product its stock record, with nothing in stock (Guide 3.2).
     * <p>
     * Safe to call twice with the same product: an event can be delivered more than once (for example
     * when a failed delivery is retried), and the second time must change nothing.
     */
    @Transactional
    public void createStockItemForProduct(UUID tenantId, UUID productId) {
        Optional<StockItemEntity> existing = stockItems.findByProductIdAndTenantId(productId, tenantId);
        if (existing.isPresent()) {
            return;
        }
        stockItems.save(StockItemEntity.newStockItem(tenantId, productId));
    }

    /** @throws StockItemNotFoundException if the product is unknown or belongs to another shop */
    @Transactional(readOnly = true)
    public StockResult get(UUID tenantId, UUID productId) {
        StockItemEntity item = findStockItemInShop(tenantId, productId);
        return StockResult.fromEntity(item);
    }

    /**
     * The owner changes what is in the shop: a delivery arrived (+40), or a count found fewer (-3)
     * (Guide 3.3). Every change is also written to the stock history, so on_hand always equals the sum
     * of the history.
     *
     * @throws ZeroStockChangeException        if the change is 0
     * @throws StockItemNotFoundException      if the product is unknown or belongs to another shop
     * @throws InvalidStockReasonException     for SALE, which only paid orders may record
     * @throws WouldBreakReservationsException if fewer would be left than unpaid orders are holding
     */
    @Transactional
    public StockResult adjust(UUID tenantId, UUID productId, AdjustStockCommand command) {
        if (command.change() == 0) {
            throw new ZeroStockChangeException();
        }
        if (command.reason() == MovementReason.SALE) {
            throw new InvalidStockReasonException();
        }

        StockItemEntity item = findStockItemInShop(tenantId, productId);

        // Removing stock may not eat into what unpaid orders hold (that also keeps on_hand at 0 or more).
        int newOnHand = item.getOnHand() + command.change();
        if (newOnHand < item.getReserved()) {
            throw new WouldBreakReservationsException(item.getOnHand(), item.getReserved());
        }

        item.changeOnHand(command.change());
        movements.save(StockMovementEntity.newMovement(tenantId, productId, command.change(), command.reason(),
                command.note(), command.actorId()));
        // Flush now: if someone else changed this stock in the meantime, @Version makes this fail here
        // (GlobalErrorHandler turns it into 409 CONCURRENT_UPDATE), and the result shows the saved numbers.
        stockItems.flush();

        if (item.isLow()) {
            events.publishEvent(new StockLowEvent(TenantId.of(tenantId), productId, item.getAvailable(),
                    item.getLowStockThreshold()));
        }
        return StockResult.fromEntity(item);
    }

    /** {@code WHERE product_id = ? AND tenant_id = ?}. Another shop's product is treated as not found. */
    private StockItemEntity findStockItemInShop(UUID tenantId, UUID productId) {
        Optional<StockItemEntity> found = stockItems.findByProductIdAndTenantId(productId, tenantId);
        if (found.isEmpty()) {
            throw new StockItemNotFoundException();
        }
        return found.get();
    }

}
