package com.hezron.smecommerce.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.hezron.smecommerce.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
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
 * Issue "Admin live order feed", Done-when: a new order appears on the admin screen without refresh.
 * The admin's feed is read here like the browser's EventSource would: a real port, line by line.
 * <p>
 * Shop A and shop B each have a product. The fake provider pays a phone ending in 0 after 500 ms and
 * never answers one ending in 3.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class ShopFeedTests {

    private static final long WAIT_AT_MOST_MILLIS = 15_000;

    @LocalServerPort
    int port;

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbc;

    private final HttpClient http = HttpClient.newHttpClient();

    private Shop shopA;
    private Shop shopB;
    private String productOfA;
    private String productOfB;

    @BeforeEach
    void twoShopsWithStock() throws Exception {
        shopA = signUp("a");
        shopB = signUp("b");
        productOfA = createStockedProduct(shopA, "Sukuma bundle", 5000, 10);
        productOfB = createStockedProduct(shopB, "Nails", 200, 10);
    }

    @Test
    void aNewOrderAppearsOnTheShopsFeedThenItsPayment() throws Exception {
        StreamReader feed = openFeed(feedUrl(shopA));
        feed.waitForLineContaining("\"type\":\"connected\"");

        PlacedOrder order = placeOrder(shopA, productOfA, "0712345670");

        int placed = feed.waitForLineContaining("\"orderNumber\":\"" + order.orderNumber() + "\"", 0);
        assertThat(feed.lines().get(placed)).contains("\"type\":\"order-placed\"");
        int paid = feed.waitForLineContaining("\"status\":\"PAID\"", placed + 1);
        assertThat(feed.lines().get(paid)).contains("\"type\":\"order-status\"")
                .contains(order.orderNumber());
        // A feed doesn't end after a paid order: more orders will come.
        assertThat(feed.isClosed()).isFalse();
        feed.stop();
    }

    @Test
    void anotherShopsOrderNeverAppearsOnMyFeed() throws Exception {
        StreamReader feedOfA = openFeed(feedUrl(shopA));
        feedOfA.waitForLineContaining("\"type\":\"connected\"");

        PlacedOrder orderOfB = placeOrder(shopB, productOfB, "0712345673");
        // Then one of A's own, so we know A's feed has heard everything up to here.
        PlacedOrder orderOfA = placeOrder(shopA, productOfA, "0712345673");
        feedOfA.waitForLineContaining(orderOfA.orderNumber());

        for (String line : feedOfA.lines()) {
            assertThat(line).doesNotContain(orderOfB.orderNumber());
        }
        feedOfA.stop();
    }

    @Test
    void aFeedTicketWorksOnce() throws Exception {
        String url = feedUrl(shopA);
        StreamReader first = openFeed(url);
        first.waitForLineContaining("\"type\":\"connected\"");

        HttpResponse<String> second = http.send(streamRequest(url), HttpResponse.BodyHandlers.ofString());

        assertThat(second.statusCode()).isEqualTo(401);
        first.stop();
    }

    @Test
    void theFeedNeedsATicketNotJustTheAddress() throws Exception {
        HttpResponse<String> madeUp = http.send(streamRequest("/api/admin/order-feed?ticket=made-up"),
                HttpResponse.BodyHandlers.ofString());

        assertThat(madeUp.statusCode()).isEqualTo(401);
    }

    // --- reading a stream ------------------------------------------------------------------------

    private StreamReader openFeed(String feedUrl) throws Exception {
        HttpResponse<Stream<String>> response = http.send(streamRequest(feedUrl), HttpResponse.BodyHandlers.ofLines());
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
            throw new AssertionError("The feed never said (from line " + fromIndex + "): " + text
                    + "\nIt said: " + lines);
        }
    }

    // --- setting up ------------------------------------------------------------------------------

    /** Asks for a SHOP ticket as the shop's owner and returns the feed address. */
    private String feedUrl(Shop shop) throws Exception {
        MvcTestResult result = mvc.post().uri("/api/admin/order-feed/ticket")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + shop.token())
                .exchange();
        assertThat(result).hasStatus(HttpStatus.OK);
        return JsonPath.read(result.getResponse().getContentAsString(), "$.streamUrl");
    }

    private PlacedOrder placeOrder(Shop shop, String productId, String phone) throws Exception {
        MvcTestResult result = mvc.post().uri("/api/store/" + shop.slug() + "/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"customerName": "Amina", "customerPhone": "%s", "paymentMethod": "FAKE",
                         "lines": [{"productId": "%s", "quantity": 1}]}
                        """.formatted(phone, productId))
                .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return new PlacedOrder(JsonPath.read(result.getResponse().getContentAsString(), "$.orderNumber"));
    }

    private Shop signUp(String label) throws Exception {
        String suffix = label + UUID.randomUUID().toString().substring(0, 6);
        String slug = "feed-" + suffix;
        MvcTestResult result = mvc.post().uri("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"shopName": "Shop %s", "shopSlug": "%s", "ownerName": "Owner",
                         "email": "feed+%s@example.com", "password": "a-long-password"}
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

        // Inventory makes the stock row when it hears about the new product (an event, a moment later).
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

    private record PlacedOrder(String orderNumber) {
    }

}
