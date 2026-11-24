package com.hezron.smecommerce.payments;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A pretend Safaricom Daraja for tests: a tiny local web server with the three endpoints we call. It
 * records every request, so tests can check exactly what our code sent.
 */
final class PretendDaraja {

    /** One request our code sent. */
    record SentRequest(String method, String path, String authorization, String body) {
    }

    static final String TOKEN = "pretend-access-token";

    private final HttpServer server;
    private final List<SentRequest> requests = new CopyOnWriteArrayList<>();
    private final AtomicInteger nextCheckout = new AtomicInteger(1);
    /** CheckoutRequestID -> the prompt request that got it. */
    private final Map<String, SentRequest> pushByCheckout = new ConcurrentHashMap<>();
    private volatile boolean rejectNextTokenUse = false;
    private volatile String queryAnswer = "{\"errorCode\": \"500.001.1001\", \"errorMessage\": \"The transaction is being processed\"}";
    private volatile int queryStatus = 500;

    PretendDaraja() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    String url() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    List<SentRequest> requests() {
        return requests;
    }

    int tokenRequests() {
        int count = 0;
        for (SentRequest request : requests) {
            if (request.path().startsWith("/oauth/v1/generate")) {
                count = count + 1;
            }
        }
        return count;
    }

    /** The prompt request that was answered with this CheckoutRequestID. */
    SentRequest pushFor(String checkoutRequestId) {
        return pushByCheckout.get(checkoutRequestId);
    }

    /** The next call with a token answers 401, as if the token had been cancelled. */
    void rejectNextTokenUse() {
        rejectNextTokenUse = true;
    }

    /** What every status query answers from now on. */
    void answerQueriesWith(int status, String json) {
        queryStatus = status;
        queryAnswer = json;
    }

    private void handle(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String path = exchange.getRequestURI().getPath();
        String query = exchange.getRequestURI().getQuery();
        if (query != null) {
            path = path + "?" + query;
        }
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        SentRequest sent = new SentRequest(exchange.getRequestMethod(), path, authorization, body);
        requests.add(sent);

        if (path.startsWith("/oauth/v1/generate")) {
            answer(exchange, 200, "{\"access_token\": \"" + TOKEN + "\", \"expires_in\": \"3599\"}");
            return;
        }
        if (rejectNextTokenUse) {
            rejectNextTokenUse = false;
            answer(exchange, 401, "{\"errorCode\": \"404.001.03\", \"errorMessage\": \"Invalid Access Token\"}");
            return;
        }
        if (path.equals("/mpesa/stkpush/v1/processrequest")) {
            int number = nextCheckout.getAndIncrement();
            pushByCheckout.put("ws_CO_pretend_" + number, sent);
            answer(exchange, 200, """
                    {"MerchantRequestID": "29115-34620561-%d", "CheckoutRequestID": "ws_CO_pretend_%d",
                     "ResponseCode": "0", "ResponseDescription": "Success. Request accepted for processing",
                     "CustomerMessage": "Success. Request accepted for processing"}
                    """.formatted(number, number));
            return;
        }
        if (path.equals("/mpesa/stkpushquery/v1/query")) {
            answer(exchange, queryStatus, queryAnswer);
            return;
        }
        answer(exchange, 404, "{\"errorMessage\": \"Not found\"}");
    }

    private static void answer(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

}
