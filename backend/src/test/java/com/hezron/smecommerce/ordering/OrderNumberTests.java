package com.hezron.smecommerce.ordering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hezron.smecommerce.TestcontainersConfiguration;
import com.hezron.smecommerce.ordering.internal.application.OrderNumberService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Readable order numbers (Guide 4.2) on real Postgres. nextOrderNumber must run inside a transaction,
 * so every call here goes through TransactionTemplate, the way placing an order will.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class OrderNumberTests {

    @Autowired
    OrderNumberService orderNumbers;

    @Autowired
    TransactionTemplate transaction;

    @Test
    void eachShopCountsOneTwoThree() {
        UUID mamaMboga = UUID.randomUUID();
        UUID kamau = UUID.randomUUID();

        assertThat(takeNumber(mamaMboga, "mama-mboga")).isEqualTo("MM-000001");
        assertThat(takeNumber(mamaMboga, "mama-mboga")).isEqualTo("MM-000002");
        // Another shop starts at 1 on its own.
        assertThat(takeNumber(kamau, "kamau")).isEqualTo("KA-000001");
        assertThat(takeNumber(mamaMboga, "mama-mboga")).isEqualTo("MM-000003");
    }

    @Test
    void aFailedOrderGivesItsNumberBack() {
        UUID shop = UUID.randomUUID();
        takeNumber(shop, "mama-mboga");

        // The order taking number 2 fails (for example out of stock): its transaction is rolled back.
        transaction.execute(new TransactionCallback<String>() {
            @Override
            public String doInTransaction(TransactionStatus status) {
                String number = orderNumbers.nextOrderNumber(shop, "mama-mboga");
                status.setRollbackOnly();
                return number;
            }
        });

        // So the next order gets 2, and the numbers have no gap.
        assertThat(takeNumber(shop, "mama-mboga")).isEqualTo("MM-000002");
    }

    @Test
    void outsideATransactionItRefusesToRun() {
        assertThatThrownBy(() -> orderNumbers.nextOrderNumber(UUID.randomUUID(), "mama-mboga"))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void twentyOrdersAtTheSameMomentGetTwentyDifferentNumbers() throws Exception {
        UUID shop = UUID.randomUUID();
        takeNumber(shop, "mama-mboga"); // the counter row exists before the race starts
        CountDownLatch startGate = new CountDownLatch(1);
        List<NumberAttempt> attempts = new ArrayList<>();
        List<Thread> threads = new ArrayList<>();

        for (int i = 0; i < 20; i++) {
            NumberAttempt attempt = new NumberAttempt(shop, startGate);
            attempts.add(attempt);
            Thread thread = new Thread(attempt);
            threads.add(thread);
            thread.start();
        }
        startGate.countDown();
        for (Thread thread : threads) {
            thread.join(TimeUnit.SECONDS.toMillis(30));
        }

        Set<String> numbers = new HashSet<>();
        for (NumberAttempt attempt : attempts) {
            assertThat(attempt.failure).isNull();
            numbers.add(attempt.number);
        }
        // 20 different numbers (a Set drops duplicates), and exactly 2 to 21 with no gaps.
        assertThat(numbers).hasSize(20);
        for (int n = 2; n <= 21; n++) {
            assertThat(numbers).contains(String.format("MM-%06d", n));
        }
    }

    private String takeNumber(UUID shop, String slug) {
        return transaction.execute(new TransactionCallback<String>() {
            @Override
            public String doInTransaction(TransactionStatus status) {
                return orderNumbers.nextOrderNumber(shop, slug);
            }
        });
    }

    /** One order taking a number on its own thread, in its own transaction. */
    private final class NumberAttempt implements Runnable {

        private final UUID shop;
        private final CountDownLatch startGate;
        private volatile String number;
        private volatile Exception failure;

        NumberAttempt(UUID shop, CountDownLatch startGate) {
            this.shop = shop;
            this.startGate = startGate;
        }

        @Override
        public void run() {
            try {
                startGate.await();
                number = takeNumber(shop, "mama-mboga");
            } catch (Exception e) {
                failure = e;
            }
        }

    }

}
