package com.hezron.smecommerce.assistant.internal.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * At most N questions per caller per minute. Each caller gets a one-minute window that starts with
 * their first question. It lives in memory, so each running instance counts on its own, which is enough
 * to stop one browser from running up the bill. The daily budget in the database is the hard limit.
 */
final class QuestionRateLimiter {

    private static final Duration WINDOW = Duration.ofMinutes(1);
    /** Above this many callers, finished windows are cleared so the map can't grow forever. */
    private static final int CLEAN_UP_ABOVE = 10_000;

    private final int perMinute;
    private final Clock clock;
    private final Map<String, CallerWindow> windows = new HashMap<>();

    QuestionRateLimiter(int perMinute, Clock clock) {
        this.perMinute = perMinute;
        this.clock = clock;
    }

    /** True if this caller may ask now (and counts the question). */
    synchronized boolean tryAcquire(String caller) {
        Instant now = clock.instant();
        if (windows.size() > CLEAN_UP_ABOVE) {
            removeFinishedWindows(now);
        }
        CallerWindow window = windows.get(caller);
        if (window == null || !now.isBefore(window.endsAt)) {
            window = new CallerWindow(now.plus(WINDOW));
            windows.put(caller, window);
        }
        if (window.questions >= perMinute) {
            return false;
        }
        window.questions = window.questions + 1;
        return true;
    }

    private void removeFinishedWindows(Instant now) {
        Iterator<CallerWindow> all = windows.values().iterator();
        while (all.hasNext()) {
            if (!now.isBefore(all.next().endsAt)) {
                all.remove();
            }
        }
    }

    private static final class CallerWindow {

        private final Instant endsAt;
        private int questions;

        private CallerWindow(Instant endsAt) {
            this.endsAt = endsAt;
        }
    }

}
