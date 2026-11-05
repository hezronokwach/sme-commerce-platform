package com.hezron.smecommerce.inventory.internal.application;

import com.hezron.smecommerce.inventory.InsufficientStockException;
import com.hezron.smecommerce.inventory.InvalidReservationException;
import com.hezron.smecommerce.inventory.InventoryApi;
import com.hezron.smecommerce.inventory.ProductNotStockedException;
import com.hezron.smecommerce.inventory.ReservationLine;
import com.hezron.smecommerce.inventory.StockLowEvent;
import com.hezron.smecommerce.inventory.StockReleasedEvent;
import com.hezron.smecommerce.inventory.StockReservedEvent;
import com.hezron.smecommerce.inventory.internal.domain.MovementReason;
import com.hezron.smecommerce.inventory.internal.domain.ReservationStatus;
import com.hezron.smecommerce.inventory.internal.persistence.ReservationEntity;
import com.hezron.smecommerce.inventory.internal.persistence.ReservationRepository;
import com.hezron.smecommerce.inventory.internal.persistence.StockItemEntity;
import com.hezron.smecommerce.inventory.internal.persistence.StockItemRepository;
import com.hezron.smecommerce.inventory.internal.persistence.StockMovementEntity;
import com.hezron.smecommerce.inventory.internal.persistence.StockMovementRepository;
import com.hezron.smecommerce.shared.TenantId;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Holds, releases and sells stock for orders (Guide 3.4). The only implementation of InventoryApi; other
 * modules see the interface, never this class.
 * <p>
 * Each method is {@code @Transactional}: called from ordering's transaction, it joins it, so the order and
 * its holds are saved together or not at all.
 */
@Service
class ReservationService implements InventoryApi {

    /** How long a hold lasts. Ordering's expiry job releases holds whose payment never came. */
    static final Duration HOLD_DURATION = Duration.ofMinutes(15);

    private final StockItemRepository stockItems;
    private final ReservationRepository reservations;
    private final StockMovementRepository movements;
    private final ApplicationEventPublisher events;

    ReservationService(StockItemRepository stockItems, ReservationRepository reservations,
            StockMovementRepository movements, ApplicationEventPublisher events) {
        this.stockItems = stockItems;
        this.reservations = reservations;
        this.movements = movements;
        this.events = events;
    }

    @Override
    @Transactional
    public void reserve(TenantId tenantId, UUID orderId, List<ReservationLine> lines) {
        checkLines(lines);
        UUID tenant = tenantId.value();
        Instant now = Instant.now();
        Instant expiresAt = now.plus(HOLD_DURATION);

        // Always in the same order (by product id). If two orders lock the same products in different
        // orders, each can end up waiting for the other forever (a deadlock); a fixed order prevents that.
        List<ReservationLine> sortedLines = new ArrayList<>(lines);
        sortedLines.sort(new ByProductId());

        for (ReservationLine line : sortedLines) {
            int changedRows = stockItems.reserveIfAvailable(tenant, line.productId(), line.quantity(), now);
            if (changedRows == 0) {
                // Throwing rolls back the lines already held above: all or nothing.
                throw whyItCouldNotBeHeld(tenant, line);
            }
            reservations.save(ReservationEntity.newHold(tenant, orderId, line.productId(), line.quantity(), expiresAt));
        }

        events.publishEvent(new StockReservedEvent(tenantId, orderId));
        for (ReservationLine line : sortedLines) {
            announceIfLow(tenantId, line.productId());
        }
    }

    @Override
    @Transactional
    public void release(TenantId tenantId, UUID orderId) {
        UUID tenant = tenantId.value();
        Instant now = Instant.now();
        // Only holds that are still HELD: a second call finds none and changes nothing.
        List<ReservationEntity> held =
                reservations.findAllByOrderIdAndTenantIdAndStatus(orderId, tenant, ReservationStatus.HELD);
        if (held.isEmpty()) {
            return;
        }

        for (ReservationEntity reservation : held) {
            stockItems.releaseHeld(tenant, reservation.getProductId(), reservation.getQuantity(), now);
            reservation.markReleased();
            reservations.save(reservation);
        }
        events.publishEvent(new StockReleasedEvent(tenantId, orderId));
    }

    @Override
    @Transactional
    public void confirm(TenantId tenantId, UUID orderId, String orderReference) {
        UUID tenant = tenantId.value();
        Instant now = Instant.now();
        // Only holds that are still HELD: a second call finds none and changes nothing.
        List<ReservationEntity> held =
                reservations.findAllByOrderIdAndTenantIdAndStatus(orderId, tenant, ReservationStatus.HELD);

        for (ReservationEntity reservation : held) {
            stockItems.sellHeld(tenant, reservation.getProductId(), reservation.getQuantity(), now);
            // The stock history: -2 SALE "MM-000123". No actor: the system did it when the payment came in.
            movements.save(StockMovementEntity.newMovement(tenant, reservation.getProductId(),
                    -reservation.getQuantity(), MovementReason.SALE, orderReference, null));
            reservation.markConfirmed();
            reservations.save(reservation);
        }
    }

    /** At least one line, every quantity 1 or more, and each product only once. */
    private static void checkLines(List<ReservationLine> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new InvalidReservationException("An order needs at least one line.");
        }
        Set<UUID> seenProducts = new HashSet<>();
        for (ReservationLine line : lines) {
            if (line.quantity() < 1) {
                throw new InvalidReservationException("Every quantity must be 1 or more.");
            }
            boolean isNew = seenProducts.add(line.productId());
            if (!isNew) {
                throw new InvalidReservationException("Each product may appear only once per order.");
            }
        }
    }

    /** The UPDATE changed nothing. Either the shop doesn't stock the product, or too few are available. */
    private RuntimeException whyItCouldNotBeHeld(UUID tenantId, ReservationLine line) {
        Optional<StockItemEntity> item = stockItems.findByProductIdAndTenantId(line.productId(), tenantId);
        if (item.isEmpty()) {
            return new ProductNotStockedException(line.productId());
        }
        return new InsufficientStockException(line.productId(), line.quantity(), item.get().getAvailable());
    }

    private void announceIfLow(TenantId tenantId, UUID productId) {
        Optional<StockItemEntity> item = stockItems.findByProductIdAndTenantId(productId, tenantId.value());
        if (item.isPresent() && item.get().isLow()) {
            events.publishEvent(new StockLowEvent(tenantId, productId, item.get().getAvailable(),
                    item.get().getLowStockThreshold()));
        }
    }

    /** Sorts reservation lines by product id. */
    private static final class ByProductId implements Comparator<ReservationLine> {

        @Override
        public int compare(ReservationLine first, ReservationLine second) {
            return first.productId().compareTo(second.productId());
        }

    }

}
