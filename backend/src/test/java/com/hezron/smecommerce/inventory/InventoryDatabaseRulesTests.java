package com.hezron.smecommerce.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hezron.smecommerce.TestcontainersConfiguration;
import com.hezron.smecommerce.inventory.internal.domain.MovementReason;
import com.hezron.smecommerce.inventory.internal.persistence.ReservationEntity;
import com.hezron.smecommerce.inventory.internal.persistence.ReservationRepository;
import com.hezron.smecommerce.inventory.internal.persistence.StockItemEntity;
import com.hezron.smecommerce.inventory.internal.persistence.StockItemRepository;
import com.hezron.smecommerce.inventory.internal.persistence.StockMovementEntity;
import com.hezron.smecommerce.inventory.internal.persistence.StockMovementRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * What V40 guarantees even if the Java code has a bug: the database refuses the row. Plus round trips
 * through the entities, so the mapping matches the tables.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class InventoryDatabaseRulesTests {

    @Autowired
    StockItemRepository stockItems;

    @Autowired
    ReservationRepository reservations;

    @Autowired
    StockMovementRepository movements;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void newStockItemStartsEmptyAndIsOnlyFoundInItsOwnShop() {
        UUID shopA = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        stockItems.saveAndFlush(StockItemEntity.newStockItem(shopA, productId));

        Optional<StockItemEntity> found = stockItems.findByProductIdAndTenantId(productId, shopA);
        assertThat(found).isPresent();
        assertThat(found.get().getOnHand()).isZero();
        assertThat(found.get().getReserved()).isZero();
        assertThat(found.get().getAvailable()).isZero();
        assertThat(found.get().getLowStockThreshold()).isEqualTo(5);

        assertThat(stockItems.findByProductIdAndTenantId(productId, UUID.randomUUID())).isEmpty();
    }

    @Test
    void cannotReserveMoreThanIsOnHand() {
        UUID productId = newStockItemWithOnHand(UUID.randomUUID(), 3);

        // The last line of defence against overselling: reserved may never exceed on_hand.
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE inventory.stock_items SET reserved = 4 WHERE product_id = ?", productId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_stock_items_reserved");
    }

    @Test
    void onHandCannotGoBelowZero() {
        UUID productId = newStockItemWithOnHand(UUID.randomUUID(), 0);

        assertThatThrownBy(() -> jdbc.update(
                "UPDATE inventory.stock_items SET on_hand = -1 WHERE product_id = ?", productId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_stock_items_on_hand");
    }

    @Test
    void holdIsSavedForAProductOfItsOwnShop() {
        UUID shopA = UUID.randomUUID();
        UUID productId = newStockItemWithOnHand(shopA, 10);

        ReservationEntity hold = ReservationEntity.newHold(shopA, UUID.randomUUID(), productId, 2, inFifteenMinutes());
        reservations.saveAndFlush(hold);

        assertThat(reservations.existsById(hold.getId())).isTrue();
    }

    @Test
    void holdCannotUseAnotherShopsStock() {
        UUID shopA = UUID.randomUUID();
        UUID productOfA = newStockItemWithOnHand(shopA, 10);
        UUID shopB = UUID.randomUUID();

        // fk_reservations_stock_item checks (tenant_id, product_id) together.
        ReservationEntity hold = ReservationEntity.newHold(shopB, UUID.randomUUID(), productOfA, 1, inFifteenMinutes());
        assertThatThrownBy(() -> reservations.saveAndFlush(hold))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_reservations_stock_item");
    }

    @Test
    void sameOrderCannotHoldTheSameProductTwice() {
        UUID shopA = UUID.randomUUID();
        UUID productId = newStockItemWithOnHand(shopA, 10);
        UUID orderId = UUID.randomUUID();
        reservations.saveAndFlush(ReservationEntity.newHold(shopA, orderId, productId, 1, inFifteenMinutes()));

        ReservationEntity again = ReservationEntity.newHold(shopA, orderId, productId, 1, inFifteenMinutes());
        assertThatThrownBy(() -> reservations.saveAndFlush(again))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_reservations_order_product");
    }

    @Test
    void movementIsSavedButAZeroChangeIsRefused() {
        UUID shopA = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        StockMovementEntity restock = StockMovementEntity.newMovement(shopA, productId, 40, MovementReason.RESTOCK,
                "First delivery", null);
        movements.saveAndFlush(restock);
        assertThat(movements.existsById(restock.getId())).isTrue();

        StockMovementEntity nothing = StockMovementEntity.newMovement(shopA, productId, 0, MovementReason.ADJUSTMENT,
                null, null);
        assertThatThrownBy(() -> movements.saveAndFlush(nothing))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_stock_movements_change");
    }

    /** Saves a new stock item, then sets on_hand directly (the restock endpoint comes in a later step). */
    private UUID newStockItemWithOnHand(UUID tenantId, int onHand) {
        UUID productId = UUID.randomUUID();
        stockItems.saveAndFlush(StockItemEntity.newStockItem(tenantId, productId));
        jdbc.update("UPDATE inventory.stock_items SET on_hand = ? WHERE product_id = ?", onHand, productId);
        return productId;
    }

    private static Instant inFifteenMinutes() {
        return Instant.now().plusSeconds(15 * 60);
    }

}
