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
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * The live streams that are open right now, by order id. Several tabs may watch the same order.
 * <p>
 * Lives in this app's memory only. With two copies of the app behind a load balancer, an event could
 * be handled by the copy that doesn't hold the customer's stream; ADR 0010 explains the fix (a shared
 * broker such as Redis pub/sub). Not needed while there is one copy.
 * <p>
 * Thread-safe: listener threads send, the keep-alive job sends, and requests add and remove streams
 * at the same time. ConcurrentHashMap and CopyOnWriteArrayList allow that without locks of our own.
 */
@Component
public class OrderStreams {

    private static final Logger log = LoggerFactory.getLogger(OrderStreams.class);

    private final Map<UUID, List<SseEmitter>> byOrder = new ConcurrentHashMap<>();

    /** Starts tracking a stream; it forgets it by itself once the stream ends in any way. */
    public void add(UUID orderId, SseEmitter stream) {
        List<SseEmitter> streams = byOrder.get(orderId);
        if (streams == null) {
            // putIfAbsent: if another request added a list for this order just now, keep theirs.
            byOrder.putIfAbsent(orderId, new CopyOnWriteArrayList<>());
            streams = byOrder.get(orderId);
        }
        streams.add(stream);

        Forget forget = new Forget(orderId, stream);
        stream.onCompletion(forget);
        stream.onTimeout(forget);
        stream.onError(forget);
    }

    /** Sends a message to every stream watching this order, and closes them if it's the last one. */
    public void send(UUID orderId, StatusMessage message) {
        List<SseEmitter> streams = byOrder.get(orderId);
        if (streams == null) {
            return; // nobody is watching
        }
        for (SseEmitter stream : streams) {
            boolean sent = sendTo(stream, SseEmitter.event().name("status").data(message));
            if (sent && message.last()) {
                stream.complete();
            }
        }
    }

    /** How many streams are open for one order (for tests and debugging). */
    public int countFor(UUID orderId) {
        List<SseEmitter> streams = byOrder.get(orderId);
        if (streams == null) {
            return 0;
        }
        return streams.size();
    }

    /**
     * Every 20 seconds, a comment line (":keep-alive") on every open stream. Browsers ignore comments,
     * but proxies and load balancers see traffic and don't cut a quiet connection off.
     */
    @Scheduled(fixedRate = 20_000)
    void keepAlive() {
        for (List<SseEmitter> streams : byOrder.values()) {
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

    private void forget(UUID orderId, SseEmitter stream) {
        List<SseEmitter> streams = byOrder.get(orderId);
        if (streams == null) {
            return;
        }
        streams.remove(stream);
        if (streams.isEmpty()) {
            byOrder.remove(orderId, streams);
        }
    }

    /**
     * What Spring runs when a stream completes, times out or fails: forget it. One class serves all
     * three, since onError hands over the error (a Consumer) while the other two take a Runnable.
     */
    private class Forget implements Runnable, Consumer<Throwable> {

        private final UUID orderId;
        private final SseEmitter stream;

        Forget(UUID orderId, SseEmitter stream) {
            this.orderId = orderId;
            this.stream = stream;
        }

        @Override
        public void run() {
            forget(orderId, stream);
        }

        @Override
        public void accept(Throwable error) {
            forget(orderId, stream);
        }
    }

}
