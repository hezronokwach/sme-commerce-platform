package com.hezron.smecommerce;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Issue "Reliable events", Done-when: killing the app mid-flow and restarting does not lose an event.
 * <p>
 * Not a @SpringBootTest: this test starts the WHOLE app itself, twice, on one database it owns:
 * <ol>
 *   <li>Start 1: an event is published and its listener starts working, then gets stuck (as if the
 *       machine froze). The app is shut down while the listener is still in the middle of it.</li>
 *   <li>Start 2: the same app on the same database. On startup Modulith delivers every unfinished
 *       event again (republish-outstanding-events-on-restart), so the listener finishes it.</li>
 * </ol>
 * For the registry, "the listener was cut off" and "the process was killed before the listener ran"
 * look the same: a row that never reached COMPLETED. That row is what this test follows.
 */
class EventSurvivesRestartTests {

    private static final long WAIT_AT_MOST_MILLIS = 20_000;

    /** One database for both starts, like the real Postgres that outlives an app restart. */
    private static PostgreSQLContainer database;

    @BeforeAll
    static void startDatabase() {
        database = new PostgreSQLContainer(DockerImageName.parse("pgvector/pgvector:pg17")
                .asCompatibleSubstituteFor("postgres"));
        database.start();
    }

    @AfterAll
    static void stopDatabase() {
        database.stop();
    }

    @Test
    void anEventCutOffByAShutdownIsDeliveredAfterTheRestart() throws InterruptedException {
        String id = UUID.randomUUID().toString();

        // Start 1: the listener gets stuck half-way; then the app goes down.
        ConfigurableApplicationContext firstRun = startApp(true);
        publish(firstRun, new RestartProbeEvent(id));
        RestartProbeListener stuckListener = firstRun.getBean(RestartProbeListener.class);
        stuckListener.waitUntilStarted();
        firstRun.close();

        // Read the database directly: the first app is gone, and the second hasn't started yet.
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(database.getJdbcUrl(),
                database.getUsername(), database.getPassword()));
        assertThat(statusOf(jdbc, id)).isNotEqualTo("COMPLETED");
        assertThat(stuckListener.finished()).isZero();

        // Start 2: same app, same database. Nobody publishes anything: the registry remembers.
        ConfigurableApplicationContext secondRun = startApp(false);
        try {
            waitForStatus(jdbc, id, "COMPLETED");
            assertThat(secondRun.getBean(RestartProbeListener.class).finished()).isEqualTo(1);
        } finally {
            secondRun.close();
        }
    }

    // --- helpers ---------------------------------------------------------------------------------

    /** Starts the real application (all modules, web server on a free port) against our database. */
    private static ConfigurableApplicationContext startApp(boolean listenerGetsStuck) {
        return new SpringApplicationBuilder(SmeCommerceApplication.class, RestartProbeConfiguration.class)
                .profiles("test")
                .properties(
                        "spring.datasource.url=" + database.getJdbcUrl(),
                        "spring.datasource.username=" + database.getUsername(),
                        "spring.datasource.password=" + database.getPassword(),
                        "spring.docker.compose.enabled=false",
                        "server.port=0",
                        "probe.listener-gets-stuck=" + listenerGetsStuck)
                .run();
    }

    private static void publish(ConfigurableApplicationContext app, Object event) {
        ApplicationEventPublisher events = app;
        TransactionTemplate transactions = app.getBean(TransactionTemplate.class);
        transactions.execute(new TransactionCallbackWithoutResult() {
            @Override
            protected void doInTransactionWithoutResult(TransactionStatus status) {
                events.publishEvent(event);
            }
        });
    }

    private static String statusOf(JdbcTemplate jdbc, String id) {
        List<String> statuses = jdbc.queryForList(
                "SELECT status FROM event_publication WHERE event_type LIKE '%RestartProbeEvent' AND serialized_event LIKE ?",
                String.class, "%" + id + "%");
        if (statuses.isEmpty()) {
            return null;
        }
        return statuses.get(0);
    }

    private static void waitForStatus(JdbcTemplate jdbc, String id, String expected) throws InterruptedException {
        long giveUpAt = System.currentTimeMillis() + WAIT_AT_MOST_MILLIS;
        while (System.currentTimeMillis() < giveUpAt) {
            if (expected.equals(statusOf(jdbc, id))) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Event never became " + expected + "; it is " + statusOf(jdbc, id));
    }

    // --- the probe -------------------------------------------------------------------------------

    record RestartProbeEvent(String id) {
    }

    /** Gets stuck (in the first start) or works (in the second), depending on a setting. */
    static class RestartProbeListener {

        private final boolean getsStuck;
        private final CountDownLatch started = new CountDownLatch(1);
        private final AtomicInteger finished = new AtomicInteger();

        RestartProbeListener(boolean getsStuck) {
            this.getsStuck = getsStuck;
        }

        @ApplicationModuleListener
        void on(RestartProbeEvent event) throws InterruptedException {
            started.countDown();
            if (getsStuck) {
                // Wait "forever". Shutting the app down interrupts this thread, cutting the work off.
                new CountDownLatch(1).await();
            }
            finished.incrementAndGet();
        }

        void waitUntilStarted() throws InterruptedException {
            started.await();
        }

        int finished() {
            return finished.get();
        }
    }

    /** @TestConfiguration: only used where it's passed in, never picked up by other tests' scanning. */
    @TestConfiguration(proxyBeanMethods = false)
    static class RestartProbeConfiguration {

        @Bean
        RestartProbeListener restartProbeListener(@Value("${probe.listener-gets-stuck}") boolean getsStuck) {
            return new RestartProbeListener(getsStuck);
        }
    }

}
