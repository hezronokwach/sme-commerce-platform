package com.hezron.smecommerce;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Counts requests per key in one-minute windows. In memory, so each running instance counts on its own:
 * enough to slow down password guessing and scripted abuse.
 */
final class RequestRateLimiter {

    private static final Duration WINDOW = Duration.ofMinutes(1);
    /** Above this many keys, finished windows are cleared so the map can't grow forever. */
    private static final int CLEAN_UP_ABOVE = 10_000;

    private final Clock clock;
    private final Map<String, Window> windows = new HashMap<>();

    RequestRateLimiter(Clock clock) {
        this.clock = clock;
    }

    /** True if this key is still under {@code perMinute} (and counts this request). */
    synchronized boolean tryAcquire(String key, int perMinute) {
        Instant now = clock.instant();
        if (windows.size() > CLEAN_UP_ABOVE) {
            removeFinished(now);
        }
        Window window = windows.get(key);
        if (window == null || !now.isBefore(window.endsAt)) {
            window = new Window(now.plus(WINDOW));
            windows.put(key, window);
        }
        if (window.requests >= perMinute) {
            return false;
        }
        window.requests = window.requests + 1;
        return true;
    }

    private void removeFinished(Instant now) {
        Iterator<Window> all = windows.values().iterator();
        while (all.hasNext()) {
            if (!now.isBefore(all.next().endsAt)) {
                all.remove();
            }
        }
    }

    private static final class Window {

        private final Instant endsAt;
        private int requests;

        private Window(Instant endsAt) {
            this.endsAt = endsAt;
        }
    }

}
