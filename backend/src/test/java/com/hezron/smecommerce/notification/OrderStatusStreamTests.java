package com.hezron.smecommerce.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.hezron.smecommerce.TestcontainersConfiguration;
import com.hezron.smecommerce.notification.internal.application.OrderStreams;
import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Issue "Notification module + SSE order status", Done-when: status changes arrive with no polling.
 * <p>
 * A real web server on a free port (RANDOM_PORT), because a stream only makes sense over a real open
 * connection. The stream is read with Java's own HttpClient, line by line, on a background thread,
 * just like a browser's EventSource would. Setting up shops and orders uses MockMvc, as elsewhere.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class OrderStatusStreamTests {

    private static final long WAIT_AT_MOST_MILLIS = 15_000;

    @LocalServerPort
    int port;

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    OrderStreams streams;

    private final HttpClient http = HttpClient.newHttpClient();

    private Shop shop;
    private String sukuma;

    @BeforeEach
    void aShopWithStock() throws Exception {
        shop = signUp("live");
        sukuma = createStockedProduct(shop, "Sukuma bundle", 5000, 10);
    }

    @Test
    void theCustomerSeesWaitingThenPaidWithoutAsking() throws Exception {
        PlacedOrder order = placeOrder("0712345670"); // fake payment succeeds after 500 ms

        StreamReader reader = openStream(customerTicketUrl(order));

        reader.waitForLineContaining("\"status\":\"PENDING_PAYMENT\"");
        reader.waitForLineContaining("Payment received. Thank you!");
        reader.waitForLineContaining("\"paymentStatus\":\"SUCCEEDED\"");
        reader.waitForLineContaining("\"last\":true");
        // After the last message the server closes the stream.
        reader.waitUntilClosed();
    }

    @Test
    void aCancelledPromptIsShownAndTheStreamStaysOpen() throws Exception {
        PlacedOrder order = placeOrder("0712345671"); // the customer "cancels" the PIN prompt

        StreamReader reader = openStream(customerTicketUrl(order));

        reader.waitForLineContaining("Payment failed: You cancelled the payment on your phone. You can try again.");
        // The page reads what happened from the data, not from the words.
        reader.waitForLineContaining("\"paymentStatus\":\"FAILED\"");
        // Still open: the customer may pay again before the time limit.
        assertThat(reader.isClosed()).isFalse();
        assertThat(streams.countFor(UUID.fromString(order.orderId()))).isEqualTo(1);
        reader.stop();
    }

    @Test
    void afterTryAgainThePageSaysWaitingAgainThenHowTheNewAttemptEnded() throws Exception {
        PlacedOrder order = placeOrder("0712345671"); // every attempt with this phone is cancelled
        StreamReader reader = openStream(customerTicketUrl(order));
        int firstFailure = reader.waitForLineContaining("\"paymentStatus\":\"FAILED\"", 0);

        MvcTestResult again = mvc.post().uri("/api/store/" + shop.slug() + "/orders/" + order.orderNumber()
                + "/payment-attempts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"orderToken": "%s"}
                        """.formatted(order.accessToken()))
                .exchange();
        assertThat(again).hasStatus(HttpStatus.ACCEPTED);

        // In this order, on the same open stream: waiting for the new PIN prompt, then its failure.
        int waitingAgain = reader.waitForLineContaining("\"paymentStatus\":\"PENDING\"", firstFailure + 1);
        reader.waitForLineContaining("\"paymentStatus\":\"FAILED\"", waitingAgain + 1);
        assertThat(reader.lines().get(waitingAgain))
                .contains("Waiting for payment. Check your phone and enter your PIN.");
        reader.stop();
    }

    @Test
    void aPageThatConnectsLateGetsTheCurrentStatusAtOnce() throws Exception {
        PlacedOrder order = placeOrder("0712345670");
        waitForOrderStatus(order.orderId(), "PAID");

        StreamReader reader = openStream(customerTicketUrl(order));

        // Nothing will happen any more, but the first message still tells the truth, then it closes.
        reader.waitForLineContaining("Payment received. Thank you!");
        reader.waitUntilClosed();
    }

    @Test
    void staffCanWatchTheirShopsOrderToo() throws Exception {
        PlacedOrder order = placeOrder("0712345673"); // never answers: the order just waits

        MvcTestResult ticket = mvc.post().uri("/api/admin/orders/" + order.orderId() + "/stream-ticket")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + shop.token())
                .exchange();
        assertThat(ticket).hasStatus(HttpStatus.OK);
        StreamReader reader = openStream(JsonPath.read(ticket.getResponse().getContentAsString(), "$.streamUrl"));

        reader.waitForLineContaining("Waiting for payment. Check your phone and enter your PIN.");
        reader.stop();
    }

    @Test
    void aTicketCannotOpenASecondStream() throws Exception {
        PlacedOrder order = placeOrder("0712345673");
        String url = customerTicketUrl(order);
        StreamReader first = openStream(url);
        first.waitForLineContaining("\"status\":\"PENDING_PAYMENT\"");

        HttpResponse<String> second = http.send(streamRequest(url), HttpResponse.BodyHandlers.ofString());

        assertThat(second.statusCode()).isEqualTo(401);
        first.stop();
    }

    @Test
    void aMadeUpTicketGets401() throws Exception {
        HttpResponse<String> response = http.send(streamRequest("/api/store/order-stream?ticket=made-up"),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(401);
    }

    // --- reading a stream ------------------------------------------------------------------------

    /** Opens the stream and starts reading it in the background, like EventSource does. */
    private StreamReader openStream(String streamUrl) throws Exception {
        HttpResponse<Stream<String>> response = http.send(streamRequest(streamUrl), HttpResponse.BodyHandlers.ofLines());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).startsWith("text/event-stream");
        StreamReader reader = new StreamReader(response.body());
        Thread thread = new Thread(reader);
        thread.setDaemon(true);
        thread.start();
        return reader;
    }

    private HttpRequest streamRequest(String path) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Accept", "text/event-stream")
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
    }

    /** Collects every line the server writes, until the server closes the stream (or stop()). */
    private static class StreamReader implements Runnable {

        private final Stream<String> body;
        private final List<String> lines = new CopyOnWriteArrayList<>();
        private volatile boolean closed = false;

        StreamReader(Stream<String> body) {
            this.body = body;
        }

        @Override
        public void run() {
            try {
                Iterator<String> iterator = body.iterator();
                while (iterator.hasNext()) {
                    lines.add(iterator.next());
                }
            } catch (RuntimeException connectionGone) {
                // stop() closed it, or the connection dropped: either way, no more lines.
            } finally {
                closed = true;
            }
        }

        List<String> lines() {
            return lines;
        }

        boolean isClosed() {
            return closed;
        }

        void stop() {
            body.close();
        }

        void waitForLineContaining(String text) throws InterruptedException {
            waitForLineContaining(text, 0);
        }

        /** Waits for a line with the text at position fromIndex or later, and returns its position. */
        int waitForLineContaining(String text, int fromIndex) throws InterruptedException {
            long giveUpAt = System.currentTimeMillis() + WAIT_AT_MOST_MILLIS;
            while (System.currentTimeMillis() < giveUpAt) {
                for (int i = fromIndex; i < lines.size(); i++) {
                    if (lines.get(i).contains(text)) {
                        return i;
                    }
                }
                Thread.sleep(50);
            }
            throw new AssertionError("The stream never said (from line " + fromIndex + "): " + text
                    + "\nIt said: " + lines);
        }

        void waitUntilClosed() throws InterruptedException {
            long giveUpAt = System.currentTimeMillis() + WAIT_AT_MOST_MILLIS;
            while (System.currentTimeMillis() < giveUpAt) {
                if (closed) {
                    return;
                }
                Thread.sleep(50);
            }
            throw new AssertionError("The server never closed the stream. It said: " + lines);
        }
    }

    // --- setting up ------------------------------------------------------------------------------

    /** Asks for a customer ticket with the order link and returns the stream address. */
    private String customerTicketUrl(PlacedOrder order) throws Exception {
        MvcTestResult result = mvc.post().uri("/api/store/" + shop.slug() + "/orders/" + order.orderNumber()
                + "/stream-ticket")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"orderToken": "%s"}
                        """.formatted(order.accessToken()))
                .exchange();
        assertThat(result).hasStatus(HttpStatus.OK);
        return JsonPath.read(result.getResponse().getContentAsString(), "$.streamUrl");
    }

    private void waitForOrderStatus(String orderId, String expected) throws InterruptedException {
        long giveUpAt = System.currentTimeMillis() + WAIT_AT_MOST_MILLIS;
        while (System.currentTimeMillis() < giveUpAt) {
            String status = jdbc.queryForObject("SELECT status FROM ordering.orders WHERE id = ?::uuid",
                    String.class, orderId);
            if (expected.equals(status)) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Order never became " + expected);
    }

    private PlacedOrder placeOrder(String phone) throws Exception {
        MvcTestResult result = mvc.post().uri("/api/store/" + shop.slug() + "/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"customerName": "Amina", "customerPhone": "%s", "paymentMethod": "FAKE",
                         "lines": [{"productId": "%s", "quantity": 1}]}
                        """.formatted(phone, sukuma))
                .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        String json = result.getResponse().getContentAsString();
        return new PlacedOrder(JsonPath.read(json, "$.orderId"), JsonPath.read(json, "$.orderNumber"),
                JsonPath.read(json, "$.accessToken"));
    }

    private Shop signUp(String label) throws Exception {
        String suffix = label + UUID.randomUUID().toString().substring(0, 6);
        String slug = "stream-" + suffix;
        MvcTestResult result = mvc.post().uri("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"shopName": "Shop %s", "shopSlug": "%s", "ownerName": "Owner",
                         "email": "stream+%s@example.com", "password": "a-long-password"}
                        """.formatted(suffix, slug, suffix))
                .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return new Shop(slug, JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken"));
    }

    private String createStockedProduct(Shop shop, String name, long priceMinor, int stock) throws Exception {
        MvcTestResult created = mvc.post().uri("/api/admin/products")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + shop.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "%s", "priceMinor": %d}
                        """.formatted(name, priceMinor))
                .exchange();
        assertThat(created).hasStatus(HttpStatus.CREATED);
        String productId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        long giveUpAt = System.currentTimeMillis() + WAIT_AT_MOST_MILLIS;
        while (System.currentTimeMillis() < giveUpAt) {
            Integer rows = jdbc.queryForObject(
                    "SELECT count(*) FROM inventory.stock_items WHERE product_id = ?::uuid", Integer.class, productId);
            if (rows != null && rows == 1) {
                break;
            }
            Thread.sleep(100);
        }
        MvcTestResult restock = mvc.post().uri("/api/admin/stock/" + productId + "/adjustments")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + shop.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"change": %d, "reason": "RESTOCK"}
                        """.formatted(stock))
                .exchange();
        assertThat(restock).hasStatus(HttpStatus.OK);
        return productId;
    }

    private record Shop(String slug, String token) {
    }

    private record PlacedOrder(String orderId, String orderNumber, String accessToken) {
    }

}
