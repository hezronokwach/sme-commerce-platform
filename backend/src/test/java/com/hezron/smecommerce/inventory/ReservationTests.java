package com.hezron.smecommerce.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hezron.smecommerce.TestcontainersConfiguration;
import com.hezron.smecommerce.inventory.internal.application.AdjustStockCommand;
import com.hezron.smecommerce.inventory.internal.application.StockService;
import com.hezron.smecommerce.inventory.internal.domain.MovementReason;
import com.hezron.smecommerce.shared.TenantId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

/**
 * InventoryApi as ordering will use it (Guide 3.4): reserve, release and confirm. Each test uses its own
 * shop id and products, stocked through StockService.
 */
@SpringBootTest
@RecordApplicationEvents
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class ReservationTests {

    @Autowired
    InventoryApi inventory;

    @Autowired
    StockService stockService;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ApplicationEvents events;

    private TenantId shop;
    private UUID sukuma;
    private UUID mandazi;

    @BeforeEach
    void shopWithTwoProducts() {
        shop = TenantId.of(UUID.randomUUID());
        sukuma = stockedProduct(10);
        mandazi = stockedProduct(3);
    }

    @Test
    void reserveHoldsTheItemsSoTheyAreNoLongerAvailable() {
        UUID orderId = UUID.randomUUID();

        inventory.reserve(shop, orderId, lines(sukuma, 4));

        assertThat(numberFor(sukuma, "on_hand")).isEqualTo(10);
        assertThat(numberFor(sukuma, "reserved")).isEqualTo(4);
        assertThat(stockService.get(shop.value(), sukuma).available()).isEqualTo(6);
        String status = jdbc.queryForObject(
                "SELECT status FROM inventory.reservations WHERE order_id = ?", String.class, orderId);
        assertThat(status).isEqualTo("HELD");
        assertThat(events.stream(StockReservedEvent.class).count()).isEqualTo(1);
    }

    @Test
    void reservingMoreThanAvailableIsRefusedWithWhatIsLeft() {
        assertThatThrownBy(() -> inventory.reserve(shop, UUID.randomUUID(), lines(mandazi, 4)))
                .isInstanceOf(InsufficientStockException.class)
                .hasFieldOrPropertyWithValue("available", 3);

        assertThat(numberFor(mandazi, "reserved")).isZero();
    }

    @Test
    void allLinesOrNoneAreHeld() {
        List<ReservationLine> twoLines = new ArrayList<>();
        twoLines.add(new ReservationLine(sukuma, 2));
        twoLines.add(new ReservationLine(mandazi, 5)); // only 3 mandazi

        assertThatThrownBy(() -> inventory.reserve(shop, UUID.randomUUID(), twoLines))
                .isInstanceOf(InsufficientStockException.class);

        // The sukuma line was fine, but it was rolled back with the rest.
        assertThat(numberFor(sukuma, "reserved")).isZero();
        Integer holds = jdbc.queryForObject(
                "SELECT count(*) FROM inventory.reservations WHERE tenant_id = ?", Integer.class, shop.value());
        assertThat(holds).isZero();
    }

    @Test
    void aProductWithoutStockInThisShopIsRefused() {
        TenantId otherShop = TenantId.of(UUID.randomUUID());

        assertThatThrownBy(() -> inventory.reserve(shop, UUID.randomUUID(), lines(UUID.randomUUID(), 1)))
                .isInstanceOf(ProductNotStockedException.class);
        // Shop A's product, asked for by another shop, is "not stocked" there.
        assertThatThrownBy(() -> inventory.reserve(otherShop, UUID.randomUUID(), lines(sukuma, 1)))
                .isInstanceOf(ProductNotStockedException.class);
    }

    @Test
    void nonsenseLinesAreRefused() {
        List<ReservationLine> sameProductTwice = new ArrayList<>();
        sameProductTwice.add(new ReservationLine(sukuma, 1));
        sameProductTwice.add(new ReservationLine(sukuma, 1));

        assertThatThrownBy(() -> inventory.reserve(shop, UUID.randomUUID(), new ArrayList<>()))
                .isInstanceOf(InvalidReservationException.class);
        assertThatThrownBy(() -> inventory.reserve(shop, UUID.randomUUID(), lines(sukuma, 0)))
                .isInstanceOf(InvalidReservationException.class);
        assertThatThrownBy(() -> inventory.reserve(shop, UUID.randomUUID(), sameProductTwice))
                .isInstanceOf(InvalidReservationException.class);
    }

    @Test
    void releaseGivesTheItemsBackAndASecondReleaseChangesNothing() {
        UUID orderId = UUID.randomUUID();
        inventory.reserve(shop, orderId, lines(sukuma, 4));

        inventory.release(shop, orderId);
        inventory.release(shop, orderId);

        assertThat(numberFor(sukuma, "reserved")).isZero();
        assertThat(numberFor(sukuma, "on_hand")).isEqualTo(10);
        String status = jdbc.queryForObject(
                "SELECT status FROM inventory.reservations WHERE order_id = ?", String.class, orderId);
        assertThat(status).isEqualTo("RELEASED");
        assertThat(events.stream(StockReleasedEvent.class).count()).isEqualTo(1);
    }

    @Test
    void confirmSellsTheItemsAndWritesTheHistory() {
        UUID orderId = UUID.randomUUID();
        inventory.reserve(shop, orderId, lines(sukuma, 4));

        inventory.confirm(shop, orderId, "MM-000123");
        inventory.confirm(shop, orderId, "MM-000123");

        assertThat(numberFor(sukuma, "on_hand")).isEqualTo(6);
        assertThat(numberFor(sukuma, "reserved")).isZero();
        String sale = jdbc.queryForObject("""
                SELECT change || ' ' || reason || ' ' || reference FROM inventory.stock_movements
                WHERE product_id = ? AND reason = 'SALE'
                """, String.class, sukuma);
        assertThat(sale).isEqualTo("-4 SALE MM-000123");
        Integer sumOfHistory = jdbc.queryForObject(
                "SELECT sum(change) FROM inventory.stock_movements WHERE product_id = ?", Integer.class, sukuma);
        assertThat(sumOfHistory).isEqualTo(6);
    }

    @Test
    void releasingAfterConfirmDoesNotGiveSoldItemsBack() {
        UUID orderId = UUID.randomUUID();
        inventory.reserve(shop, orderId, lines(sukuma, 4));
        inventory.confirm(shop, orderId, "MM-000124");

        inventory.release(shop, orderId);

        assertThat(numberFor(sukuma, "on_hand")).isEqualTo(6);
        assertThat(numberFor(sukuma, "reserved")).isZero();
    }

    @Test
    void holdingTheLastItemsAnnouncesStockLow() {
        inventory.reserve(shop, UUID.randomUUID(), lines(sukuma, 6));

        // 10 on hand - 6 held = 4 available, below the warning level of 5. (Mandazi's setup restock to 3
        // was already low and announced too, so only sukuma's announcements are counted.)
        int sukumaWarnings = 0;
        for (StockLowEvent event : events.stream(StockLowEvent.class).toList()) {
            if (event.productId().equals(sukuma)) {
                sukumaWarnings++;
            }
        }
        assertThat(sukumaWarnings).isEqualTo(1);
    }

    private UUID stockedProduct(int onHand) {
        UUID productId = UUID.randomUUID();
        stockService.createStockItemForProduct(shop.value(), productId);
        stockService.adjust(shop.value(), productId, new AdjustStockCommand(onHand, MovementReason.RESTOCK, null, null));
        return productId;
    }

    private static List<ReservationLine> lines(UUID productId, int quantity) {
        List<ReservationLine> lines = new ArrayList<>();
        lines.add(new ReservationLine(productId, quantity));
        return lines;
    }

    /** Reads on_hand or reserved straight from the table. */
    private int numberFor(UUID productId, String column) {
        Integer value = jdbc.queryForObject(
                "SELECT " + column + " FROM inventory.stock_items WHERE product_id = ?", Integer.class, productId);
        return value;
    }

}
