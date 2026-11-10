package com.hezron.smecommerce;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.CompletedEventPublications;
import org.springframework.modulith.events.FailedEventPublications;
import org.springframework.modulith.events.ResubmissionOptions;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Looks after the event publication registry (Guide 6.1, "your own retry job"), so an event whose
 * listener failed is delivered again without waiting for the next restart.
 * <p>
 * It belongs to the platform, not to a module (like GlobalErrorHandler): every module's events go
 * through the same registry. It only uses Modulith's own API, plus one read-only query for the alert.
 * <p>
 * How a stuck event gets here:
 * <ul>
 *   <li>The listener threw: Modulith marks the row FAILED straight away.</li>
 *   <li>The app died before or while the listener ran: the row stays PUBLISHED or PROCESSING. Modulith's
 *       staleness monitor (spring.modulith.events.staleness.* in application.properties) marks such
 *       rows FAILED after a few minutes. It runs on the same scheduler as this job.</li>
 * </ul>
 * Either way, resubmitFailedEvents() below picks it up.
 * <p>
 * {@code @EnableScheduling} switches on Spring's {@code @Scheduled} methods for the whole app. It sits
 * here, next to the only scheduled methods, so it's easy to find.
 */
@Component
@EnableScheduling
class EventRetryJob {

    private static final Logger log = LoggerFactory.getLogger(EventRetryJob.class);

    private final FailedEventPublications failedEvents;
    private final CompletedEventPublications completedEvents;
    private final JdbcTemplate jdbc;
    private final Duration retryOlderThan;
    private final Duration alertOlderThan;
    private final Duration keepCompletedFor;

    EventRetryJob(FailedEventPublications failedEvents, CompletedEventPublications completedEvents, JdbcTemplate jdbc,
            @Value("${app.events.retry-older-than}") Duration retryOlderThan,
            @Value("${app.events.alert-older-than}") Duration alertOlderThan,
            @Value("${app.events.keep-completed-for}") Duration keepCompletedFor) {
        this.failedEvents = failedEvents;
        this.completedEvents = completedEvents;
        this.jdbc = jdbc;
        this.retryOlderThan = retryOlderThan;
        this.alertOlderThan = alertOlderThan;
        this.keepCompletedFor = keepCompletedFor;
    }

    /**
     * Every few minutes: deliver failed events again, then warn about any that are still not done
     * long after they were published.
     * <p>
     * fixedDelay: the next run starts that long after the previous one FINISHED, so two runs never
     * overlap even if one is slow.
     */
    @Scheduled(fixedDelayString = "${app.events.retry-every}", initialDelayString = "${app.events.retry-every}")
    void resubmitFailedEvents() {
        // Only rows that failed a little while ago: a listener that just threw isn't hammered at once.
        failedEvents.resubmit(ResubmissionOptions.defaults().withMinAge(retryOlderThan));
        warnAboutEventsStuckTooLong();
    }

    /** Once a day: forget finished events older than a week, so the table doesn't grow forever. */
    @Scheduled(cron = "${app.events.cleanup-cron}")
    void deleteOldCompletedEvents() {
        completedEvents.deletePublicationsOlderThan(keepCompletedFor);
    }

    /**
     * One ERROR line per listener whose events stay unfinished. Retrying is automatic; this is for a
     * human: a listener that keeps failing has a bug, or something it needs is down.
     */
    void warnAboutEventsStuckTooLong() {
        Timestamp publishedBefore = Timestamp.from(Instant.now().minus(alertOlderThan));
        List<Map<String, Object>> stuck = jdbc.queryForList("""
                SELECT listener_id, event_type, count(*) AS events, min(publication_date) AS oldest
                FROM event_publication
                WHERE completion_date IS NULL AND publication_date < ?
                GROUP BY listener_id, event_type
                """, publishedBefore);

        for (Map<String, Object> row : stuck) {
            log.error("{} event(s) of type {} not handled by {} for over {} (oldest published {})",
                    row.get("events"), row.get("event_type"), row.get("listener_id"), alertOlderThan, row.get("oldest"));
        }
    }

}
