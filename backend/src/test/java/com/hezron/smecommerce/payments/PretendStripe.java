package com.hezron.smecommerce.payments;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A pretend Stripe for tests, on a free port, built on the JDK's own little HTTP server (no new
 * library). It answers the two calls our StripeClient makes and remembers every request, so a test can
 * check exactly what we sent. No real Stripe account or network is needed, so CI runs without keys.
 */
final class PretendStripe {

    /** One request our code sent. */
    record SentRequest(String method, String path, Map<String, String> form, String authorization,
            String idempotencyKey) {
    }

    private final HttpServer server;
    private final List<SentRequest> requests = new CopyOnWriteArrayList<>();
    private final Map<String, String> intentStatus = new ConcurrentHashMap<>();
    private final AtomicInteger nextId = new AtomicInteger(1);
    private volatile String refuseWith = null;

    PretendStripe() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/v1/payment_intents", this::handle);
        server.start();
    }

    String url() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    List<SentRequest> requests() {
        return requests;
    }

    /** The next intent creation answers 400 with this message, like Stripe refusing a request. */
    void refuseNextWith(String message) {
        refuseWith = message;
    }

    /** What GET returns for this intent from now on (e.g. "succeeded"). */
    void setStatus(String intentId, String status) {
        intentStatus.put(intentId, status);
    }

    private void handle(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String path = exchange.getRequestURI().getPath();
        requests.add(new SentRequest(exchange.getRequestMethod(), path, parseForm(body),
                exchange.getRequestHeaders().getFirst("Authorization"),
                exchange.getRequestHeaders().getFirst("Idempotency-Key")));

        if ("POST".equals(exchange.getRequestMethod())) {
            String refusal = refuseWith;
            if (refusal != null) {
                refuseWith = null;
                answer(exchange, 400, "{\"error\": {\"message\": \"" + refusal + "\"}}");
                return;
            }
            String id = "pi_test_" + nextId.getAndIncrement();
            intentStatus.put(id, "requires_payment_method");
            answer(exchange, 200, intentJson(id));
            return;
        }

        // GET /v1/payment_intents/{id}
        String id = path.substring(path.lastIndexOf('/') + 1);
        if (!intentStatus.containsKey(id)) {
            answer(exchange, 404, "{\"error\": {\"message\": \"No such payment_intent\"}}");
            return;
        }
        answer(exchange, 200, intentJson(id));
    }

    private String intentJson(String id) {
        return """
                {"id": "%s", "object": "payment_intent", "status": "%s", "client_secret": "%s_secret_pretend",
                 "amount_received": 0}
                """.formatted(id, intentStatus.get(id), id);
    }

    private static void answer(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static Map<String, String> parseForm(String body) {
        Map<String, String> form = new ConcurrentHashMap<>();
        if (body.isBlank()) {
            return form;
        }
        for (String pair : body.split("&")) {
            int equals = pair.indexOf('=');
            if (equals > 0) {
                form.put(URLDecoder.decode(pair.substring(0, equals), StandardCharsets.UTF_8),
                        URLDecoder.decode(pair.substring(equals + 1), StandardCharsets.UTF_8));
            }
        }
        return form;
    }

}
