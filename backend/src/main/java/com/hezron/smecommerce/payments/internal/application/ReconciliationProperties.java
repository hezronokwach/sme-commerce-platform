package com.hezron.smecommerce.payments.internal.application;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * When reconciliation (Guide 5.7) asks providers again about payments with no answer.
 *
 * @param firstCheckAfterAccepted the first question after a provider accepted (a callback usually comes first)
 * @param firstCheckAfterUnknown  the first question after a start with no clear answer
 * @param backoff                 the waits between later questions; the last one repeats
 * @param unknownGiveUpAfter      a start with no answer AND no reference can't be asked about: after this
 *                                long it is given up as failed (retryable)
 * @param timedOutAfter           still no answer this long after the start: TIMED_OUT ("checking")
 * @param timedOutCheckEvery      how often a TIMED_OUT payment is still asked about
 * @param stopAfter               after this long, stop asking at all (people look at it instead)
 * @param batchSize               at most this many payments per run
 */
@ConfigurationProperties("app.payments.reconciliation")
public record ReconciliationProperties(Duration firstCheckAfterAccepted, Duration firstCheckAfterUnknown,
        List<Duration> backoff, Duration unknownGiveUpAfter, Duration timedOutAfter, Duration timedOutCheckEvery,
        Duration stopAfter, int batchSize) {

    public ReconciliationProperties {
        if (firstCheckAfterAccepted == null) {
            firstCheckAfterAccepted = Duration.ofSeconds(90);
        }
        if (firstCheckAfterUnknown == null) {
            firstCheckAfterUnknown = Duration.ofSeconds(60);
        }
        if (backoff == null || backoff.isEmpty()) {
            backoff = List.of(Duration.ofSeconds(30), Duration.ofMinutes(1), Duration.ofMinutes(2),
                    Duration.ofMinutes(5), Duration.ofMinutes(10));
        }
        if (unknownGiveUpAfter == null) {
            unknownGiveUpAfter = Duration.ofMinutes(3);
        }
        if (timedOutAfter == null) {
            timedOutAfter = Duration.ofMinutes(10);
        }
        if (timedOutCheckEvery == null) {
            timedOutCheckEvery = Duration.ofHours(1);
        }
        if (stopAfter == null) {
            stopAfter = Duration.ofHours(24);
        }
        if (batchSize <= 0) {
            batchSize = 50;
        }
    }

    /** The wait after the given number of questions already asked: 30 s, 1 min, 2 min, 5 min, then 10 min. */
    public Duration waitAfter(int questionsAsked) {
        int index = questionsAsked;
        if (index >= backoff.size()) {
            index = backoff.size() - 1;
        }
        return backoff.get(index);
    }

}
