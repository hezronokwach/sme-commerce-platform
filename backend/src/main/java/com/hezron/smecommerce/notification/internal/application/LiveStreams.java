package com.hezron.smecommerce.notification.internal.application;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * The live SSE streams that are open right now, grouped by a key: an order's id (OrderStreams, for the
 * customer's order page) or a shop's id (ShopFeeds, for the admin's orders list). Several tabs may
 * share a key. The two kinds differ only in the key and the message; the bookkeeping is all here.
 * <p>
 * Lives in this app's memory only. With two copies of the app behind a load balancer, an event could
 * be handled by the copy that doesn't hold the stream; ADR 0010 explains the fix (a shared broker such
 * as Redis pub/sub). Not needed while there is one copy.
 * <p>
 * Thread-safe: listener threads send, the keep-alive job sends, and requests add and remove streams
 * at the same time. ConcurrentHashMap and CopyOnWriteArrayList allow that without locks of our own.
 */
abstract class LiveStreams {

    private static final Logger log = LoggerFactory.getLogger(LiveStreams.class);

    private final Map<UUID, List<SseEmitter>> byKey = new ConcurrentHashMap<>();

    /** Starts tracking a stream; it forgets it by itself once the stream ends in any way. */
    void addStream(UUID key, SseEmitter stream) {
        List<SseEmitter> streams = byKey.get(key);
        if (streams == null) {
            // putIfAbsent: if another request added a list for this key just now, keep theirs.
            byKey.putIfAbsent(key, new CopyOnWriteArrayList<>());
            streams = byKey.get(key);
        }
        streams.add(stream);

        Forget forget = new Forget(key, stream);
        stream.onCompletion(forget);
        stream.onTimeout(forget);
        stream.onError(forget);
    }

    /**
     * Sends one named event to every stream with this key.
     *
     * @param closeAfter true to end those streams after this message (nothing more will come)
     */
    void sendToAll(UUID key, String eventName, Object data, boolean closeAfter) {
        List<SseEmitter> streams = byKey.get(key);
        if (streams == null) {
            return; // nobody is watching
        }
        for (SseEmitter stream : streams) {
            boolean sent = sendTo(stream, SseEmitter.event().name(eventName).data(data));
            if (sent && closeAfter) {
                stream.complete();
            }
        }
    }

    /** How many streams are open for one key (for tests, and to skip work when nobody watches). */
    public int countFor(UUID key) {
        List<SseEmitter> streams = byKey.get(key);
        if (streams == null) {
            return 0;
        }
        return streams.size();
    }

    /**
     * Every 20 seconds, a comment line (":keep-alive") on every open stream. Browsers ignore comments,
     * but proxies and load balancers see traffic and don't cut a quiet connection off. Each subclass is
     * its own bean, and Spring finds this inherited @Scheduled method on each.
     */
    @Scheduled(fixedRate = 20_000)
    void keepAlive() {
        for (List<SseEmitter> streams : byKey.values()) {
            for (SseEmitter stream : streams) {
                sendTo(stream, SseEmitter.event().comment("keep-alive"));
            }
        }
    }

    /** False if the browser has gone (closed tab, lost network); the stream is then closed and forgotten. */
    private boolean sendTo(SseEmitter stream, SseEmitter.SseEventBuilder event) {
        try {
            stream.send(event);
            return true;
        } catch (IOException | IllegalStateException gone) {
            log.debug("Live stream closed by the browser: {}", gone.getMessage());
            stream.completeWithError(gone);
            return false;
        }
    }

    private void forget(UUID key, SseEmitter stream) {
        List<SseEmitter> streams = byKey.get(key);
        if (streams == null) {
            return;
        }
        streams.remove(stream);
        if (streams.isEmpty()) {
            byKey.remove(key, streams);
        }
    }

    /**
     * What Spring runs when a stream completes, times out or fails: forget it. One class serves all
     * three, since onError hands over the error (a Consumer) while the other two take a Runnable.
     */
    private class Forget implements Runnable, Consumer<Throwable> {

        private final UUID key;
        private final SseEmitter stream;

        Forget(UUID key, SseEmitter stream) {
            this.key = key;
            this.stream = stream;
        }

        @Override
        public void run() {
            forget(key, stream);
        }

        @Override
        public void accept(Throwable error) {
            forget(key, stream);
        }
    }

}
