package com.hezron.smecommerce;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Issue "Reliable events": a listener that fails on purpose is delivered again by EventRetryJob.
 * <p>
 * The probe event and listener exist only in this test, so no real module is changed to fail. The
 * scheduled run is pushed out to an hour so the test can call the job itself, at the moment it
 * chooses, instead of racing the timer.
 */
@SpringBootTest(properties = {
        "app.events.retry-every=1h",
        "app.events.retry-older-than=0s",
        "app.events.alert-older-than=2s"
})
@Import({TestcontainersConfiguration.class, EventRetryTests.ProbeConfiguration.class})
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class EventRetryTests {

    private static final long WAIT_AT_MOST_MILLIS = 15_000;

    @Autowired
    ApplicationEventPublisher events;

    @Autowired
    TransactionTemplate transactions;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EventRetryJob retryJob;

    @Autowired
    ProbeListener probe;

    @Test
    void aListenerThatFailsIsDeliveredAgainByTheRetryJob() throws InterruptedException {
        String id = UUID.randomUUID().toString();
        probe.failTimes(id, 1);
        publishProbe(id);

        // First delivery throws: the row is kept, marked FAILED, not lost.
        waitForStatus(id, "FAILED");
        assertThat(probe.successes(id)).isZero();

        retryJob.resubmitFailedEvents();

        waitForStatus(id, "COMPLETED");
        assertThat(probe.successes(id)).isEqualTo(1);
        assertThat(probe.attempts(id)).isEqualTo(2);
    }

    @Test
    void aListenerThatKeepsFailingRaisesAnAlert(CapturedOutput output) throws InterruptedException {
        String id = UUID.randomUUID().toString();
        probe.failTimes(id, 1000);
        publishProbe(id);
        waitForStatus(id, "FAILED");

        Thread.sleep(2_500); // older than app.events.alert-older-than (2 s in this test)
        retryJob.resubmitFailedEvents();

        assertThat(output.getAll()).contains("not handled by").contains("ProbeEvent");
        assertThat(probe.successes(id)).isZero();
    }

    @Test
    void aListenerThatWorksCompletesItsRowAtOnce() throws InterruptedException {
        String id = UUID.randomUUID().toString();
        publishProbe(id);

        waitForStatus(id, "COMPLETED");
        assertThat(probe.attempts(id)).isEqualTo(1);
    }

    // --- helpers ---------------------------------------------------------------------------------

    /** Publishes a probe event the way modules do: inside a transaction, delivered after commit. */
    private void publishProbe(String id) {
        transactions.execute(new TransactionCallbackWithoutResult() {
            @Override
            protected void doInTransactionWithoutResult(TransactionStatus status) {
                events.publishEvent(new ProbeEvent(id));
            }
        });
    }

    /** The registry row for one probe event (the event is stored as JSON, which contains its id). */
    private String statusOf(String id) {
        List<String> statuses = jdbc.queryForList(
                "SELECT status FROM event_publication WHERE event_type LIKE '%ProbeEvent' AND serialized_event LIKE ?",
                String.class, "%" + id + "%");
        if (statuses.isEmpty()) {
            return null;
        }
        return statuses.get(0);
    }

    private void waitForStatus(String id, String expected) throws InterruptedException {
        long giveUpAt = System.currentTimeMillis() + WAIT_AT_MOST_MILLIS;
        while (System.currentTimeMillis() < giveUpAt) {
            if (expected.equals(statusOf(id))) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Probe event never became " + expected + "; it is " + statusOf(id));
    }

    // --- the probe -------------------------------------------------------------------------------

    /** A test-only event. A record, so the registry can store it as JSON and rebuild it for a retry. */
    record ProbeEvent(String id) {
    }

    /**
     * Fails each event as many times as it is told to, then works. Counts per event id, so a probe
     * left FAILED by one test (and retried during another) never mixes up the other test's numbers.
     */
    static class ProbeListener {

        private final Map<String, AtomicInteger> failuresLeft = new ConcurrentHashMap<>();
        private final Map<String, AtomicInteger> attempts = new ConcurrentHashMap<>();
        private final Map<String, AtomicInteger> successes = new ConcurrentHashMap<>();

        /** The same annotation real modules use: after commit, on another thread, own transaction. */
        @ApplicationModuleListener
        void on(ProbeEvent event) {
            counter(attempts, event.id()).incrementAndGet();
            if (counter(failuresLeft, event.id()).getAndDecrement() > 0) {
                throw new IllegalStateException("Probe listener failing on purpose");
            }
            counter(successes, event.id()).incrementAndGet();
        }

        void failTimes(String id, int times) {
            counter(failuresLeft, id).set(times);
        }

        int attempts(String id) {
            return counter(attempts, id).get();
        }

        int successes(String id) {
            return counter(successes, id).get();
        }

        /** The counter for one id, created at 0 the first time it's asked for. */
        private static AtomicInteger counter(Map<String, AtomicInteger> counters, String id) {
            AtomicInteger counter = counters.get(id);
            if (counter == null) {
                counters.putIfAbsent(id, new AtomicInteger());
                counter = counters.get(id);
            }
            return counter;
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ProbeConfiguration {

        @Bean
        ProbeListener probeListener() {
            return new ProbeListener();
        }
    }

}
