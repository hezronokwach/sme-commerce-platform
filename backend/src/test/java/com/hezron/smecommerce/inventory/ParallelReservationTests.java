package com.hezron.smecommerce.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.hezron.smecommerce.TestcontainersConfiguration;
import com.hezron.smecommerce.inventory.internal.application.AdjustStockCommand;
import com.hezron.smecommerce.inventory.internal.application.StockService;
import com.hezron.smecommerce.inventory.internal.domain.MovementReason;
import com.hezron.smecommerce.shared.TenantId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * The issue's "Done when": two parallel reservations cannot oversell, tested on real Postgres
 * (Testcontainers), with real threads and real database connections.
 * <p>
 * Each attempt runs on its own thread. A CountDownLatch is a starting gate: every thread waits at
 * {@code startGate.await()} until the test calls {@code startGate.countDown()}, so they all hit the
 * database at the same moment instead of one after the other.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class ParallelReservationTests {

    /** Races are about timing, so each race is run many times to give the bad timing a chance to happen. */
    private static final int ROUNDS = 20;

    @Autowired
    InventoryApi inventory;

    @Autowired
    StockService stockService;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void twoOrdersForTheLastItemAtTheSameMomentExactlyOneWins() throws Exception {
        for (int round = 1; round <= ROUNDS; round++) {
            TenantId shop = TenantId.of(UUID.randomUUID());
            UUID lastItem = stockedProduct(shop, 1);

            List<ReserveAttempt> attempts = runAtTheSameMoment(shop, lastItem, 2);

            assertThat(countSucceeded(attempts)).as("round %d: orders that got the item", round).isEqualTo(1);
            assertThat(countRefusedForStock(attempts)).as("round %d: orders told 'not enough'", round).isEqualTo(1);
            assertThat(reservedOf(lastItem)).isEqualTo(1);
            assertThat(holdsFor(lastItem)).isEqualTo(1);
        }
    }

    @Test
    void tenOrdersForFiveItemsExactlyFiveWin() throws Exception {
        for (int round = 1; round <= ROUNDS; round++) {
            TenantId shop = TenantId.of(UUID.randomUUID());
            UUID product = stockedProduct(shop, 5);

            List<ReserveAttempt> attempts = runAtTheSameMoment(shop, product, 10);

            assertThat(countSucceeded(attempts)).as("round %d: orders that got an item", round).isEqualTo(5);
            assertThat(countRefusedForStock(attempts)).as("round %d: orders told 'not enough'", round).isEqualTo(5);
            // Never more held than there is: the oversell the issue is about.
            assertThat(reservedOf(product)).isEqualTo(5);
            assertThat(holdsFor(product)).isEqualTo(5);
        }
    }

    /** Starts {@code orders} threads, each reserving 1 of the product for its own order, all at once. */
    private List<ReserveAttempt> runAtTheSameMoment(TenantId shop, UUID productId, int orders) throws Exception {
        CountDownLatch startGate = new CountDownLatch(1);
        List<ReserveAttempt> attempts = new ArrayList<>();
        List<Thread> threads = new ArrayList<>();

        for (int i = 0; i < orders; i++) {
            ReserveAttempt attempt = new ReserveAttempt(inventory, shop, productId, startGate);
            attempts.add(attempt);
            Thread thread = new Thread(attempt);
            threads.add(thread);
            thread.start();
        }

        startGate.countDown(); // open the gate: every thread calls reserve now
        for (Thread thread : threads) {
            thread.join(TimeUnit.SECONDS.toMillis(30));
        }
        return attempts;
    }

    private static int countSucceeded(List<ReserveAttempt> attempts) {
        int count = 0;
        for (ReserveAttempt attempt : attempts) {
            if (attempt.succeeded) {
                count++;
            }
        }
        return count;
    }

    /** Losers must fail cleanly with InsufficientStock, not with a deadlock or any other error. */
    private static int countRefusedForStock(List<ReserveAttempt> attempts) {
        int count = 0;
        for (ReserveAttempt attempt : attempts) {
            if (attempt.failure instanceof InsufficientStockException) {
                count++;
            }
        }
        return count;
    }

    private UUID stockedProduct(TenantId shop, int onHand) {
        UUID productId = UUID.randomUUID();
        stockService.createStockItemForProduct(shop.value(), productId);
        stockService.adjust(shop.value(), productId, new AdjustStockCommand(onHand, MovementReason.RESTOCK, null, null));
        return productId;
    }

    private int reservedOf(UUID productId) {
        Integer reserved = jdbc.queryForObject(
                "SELECT reserved FROM inventory.stock_items WHERE product_id = ?", Integer.class, productId);
        return reserved;
    }

    private int holdsFor(UUID productId) {
        Integer holds = jdbc.queryForObject(
                "SELECT count(*) FROM inventory.reservations WHERE product_id = ? AND status = 'HELD'",
                Integer.class, productId);
        return holds;
    }

    /** One order trying to reserve 1 item, on its own thread. Remembers whether it worked. */
    private static final class ReserveAttempt implements Runnable {

        private final InventoryApi inventory;
        private final TenantId shop;
        private final UUID productId;
        private final CountDownLatch startGate;

        // volatile: written by this attempt's thread, read by the test's thread afterwards.
        private volatile boolean succeeded;
        private volatile Exception failure;

        ReserveAttempt(InventoryApi inventory, TenantId shop, UUID productId, CountDownLatch startGate) {
            this.inventory = inventory;
            this.shop = shop;
            this.productId = productId;
            this.startGate = startGate;
        }

        @Override
        public void run() {
            try {
                startGate.await();
                List<ReservationLine> lines = new ArrayList<>();
                lines.add(new ReservationLine(productId, 1));
                inventory.reserve(shop, UUID.randomUUID(), lines);
                succeeded = true;
            } catch (Exception e) {
                failure = e;
            }
        }

    }

}
