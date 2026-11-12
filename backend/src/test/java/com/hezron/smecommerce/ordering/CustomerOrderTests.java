package com.hezron.smecommerce.ordering;

import static org.assertj.core.api.Assertions.assertThat;

import com.hezron.smecommerce.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
 * Issue "Checkout and live payment status": the customer's order page (Guide 4.5) and "Try again"
 * after a failed payment (workflow W7). No login anywhere: the private order link is the proof.
 * <p>
 * The fake provider answers by the phone's last digit: 0 pays, 1 is a cancelled prompt (retryable),
 * 3 never answers. In tests it answers after 500 ms.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class CustomerOrderTests {

    private static final long WAIT_AT_MOST_MILLIS = 15_000;

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbc;

    private Shop shopA;
    private Shop shopB;
    private String sukuma;

    @BeforeEach
    void twoShops() throws Exception {
        shopA = signUp("a");
        shopB = signUp("b");
        sukuma = createStockedProduct(shopA, "Sukuma bundle", 5000, 10);
    }

    // --- reading the order ------------------------------------------------------------------------

    @Test
    void theCustomerSeesTheirOrderWithTheLink() throws Exception {
        PlacedOrder order = placeOrder("0712345673", 2);

        MvcTestResult result = view(shopA.slug(), order.orderNumber(), order.accessToken());

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.orderNumber").isEqualTo(order.orderNumber());
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("PENDING_PAYMENT");
        assertThat(result).bodyJson().extractingPath("$.totalMinor").isEqualTo(10000);
        assertThat(result).bodyJson().extractingPath("$.lines[0].productName").isEqualTo("Sukuma bundle");
        assertThat(result).bodyJson().extractingPath("$.lines[0].quantity").isEqualTo(2);
        // Only a hint of the phone: the page shows where the PIN prompt went, nothing more.
        assertThat(result).bodyJson().extractingPath("$.phoneHint").isEqualTo("2547******73");
        assertThat(result.getResponse().getContentAsString()).doesNotContain("254712345673");
        // Nobody's order is kept in a browser or proxy cache.
        assertThat(result.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
    }

    @Test
    void aWrongOrMissingLinkLooksExactlyLikeAMissingOrder() throws Exception {
        PlacedOrder order = placeOrder("0712345673", 1);

        assertOrderNotFound(view(shopA.slug(), order.orderNumber(), "not-the-token"));
        assertOrderNotFound(view(shopA.slug(), order.orderNumber(), null));
        assertOrderNotFound(view(shopA.slug(), "XX-999999", order.accessToken()));
    }

    @Test
    void anotherShopsAddressNeverShowsTheOrder() throws Exception {
        PlacedOrder order = placeOrder("0712345673", 1);

        // The right order number and the right link, but through shop B's address.
        assertOrderNotFound(view(shopB.slug(), order.orderNumber(), order.accessToken()));
    }

    @Test
    void theOrderShowsWhyThePaymentFailed() throws Exception {
        PlacedOrder order = placeOrder("0712345671", 1); // the customer cancels the prompt
        waitForLatestPayment(order.orderId(), "FAILED");

        MvcTestResult result = view(shopA.slug(), order.orderNumber(), order.accessToken());

        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("PENDING_PAYMENT");
        assertThat(result).bodyJson().extractingPath("$.paymentStatus").isEqualTo("FAILED");
        assertThat(result).bodyJson().extractingPath("$.paymentFailureReason")
                .isEqualTo("You cancelled the payment on your phone.");
    }

    // --- trying again ---------------------------------------------------------------------------------

    @Test
    void afterAFailedPaymentTheCustomerCanTryAgain() throws Exception {
        PlacedOrder order = placeOrder("0712345671", 1);
        waitForLatestPayment(order.orderId(), "FAILED");

        MvcTestResult result = tryAgain(shopA.slug(), order.orderNumber(), order.accessToken());

        assertThat(result).hasStatus(HttpStatus.ACCEPTED);
        // A second attempt, for the same amount, and staff can see it in the history.
        assertThat(countPayments(order.orderId())).isEqualTo(2);
        Integer historyRows = jdbc.queryForObject("""
                SELECT count(*) FROM ordering.order_status_history
                WHERE order_id = ?::uuid AND reason = 'Customer tried paying again'
                """, Integer.class, order.orderId());
        assertThat(historyRows).isEqualTo(1);
        // The order is still waiting, with its stock still held.
        assertThat(orderStatus(order.orderId())).isEqualTo("PENDING_PAYMENT");
    }

    @Test
    void noSecondAttemptWhileOneIsStillRunning() throws Exception {
        PlacedOrder order = placeOrder("0712345673", 1); // the phone never answers: still PENDING

        MvcTestResult result = tryAgain(shopA.slug(), order.orderNumber(), order.accessToken());

        assertThat(result).hasStatus(HttpStatus.CONFLICT);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("PAYMENT_IN_PROGRESS");
        assertThat(countPayments(order.orderId())).isEqualTo(1);
    }

    @Test
    void aPaidOrderCannotBePaidAgain() throws Exception {
        PlacedOrder order = placeOrder("0712345670", 1);
        waitForOrderStatus(order.orderId(), "PAID");

        MvcTestResult result = tryAgain(shopA.slug(), order.orderNumber(), order.accessToken());

        assertThat(result).hasStatus(HttpStatus.CONFLICT);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("ORDER_NOT_PAYABLE");
        assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("This order is already paid.");
    }

    @Test
    void afterTheDeadlineThereIsNoNewAttempt() throws Exception {
        PlacedOrder order = placeOrder("0712345671", 1);
        waitForLatestPayment(order.orderId(), "FAILED");
        // The expiry job doesn't exist yet, so the order is still PENDING_PAYMENT, but its time is up.
        jdbc.update("UPDATE ordering.orders SET expires_at = now() - interval '1 minute' WHERE id = ?::uuid",
                order.orderId());

        MvcTestResult result = tryAgain(shopA.slug(), order.orderNumber(), order.accessToken());

        assertThat(result).hasStatus(HttpStatus.CONFLICT);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("ORDER_NOT_PAYABLE");
        assertThat(countPayments(order.orderId())).isEqualTo(1);
    }

    @Test
    void tryingAgainNeedsTheLinkToo() throws Exception {
        PlacedOrder order = placeOrder("0712345671", 1);
        waitForLatestPayment(order.orderId(), "FAILED");

        assertOrderNotFound(tryAgain(shopA.slug(), order.orderNumber(), "not-the-token"));
        assertOrderNotFound(tryAgain(shopB.slug(), order.orderNumber(), order.accessToken()));
        assertThat(countPayments(order.orderId())).isEqualTo(1);
    }

    // --- helpers ----------------------------------------------------------------------------------

    private MvcTestResult view(String slug, String orderNumber, String token) {
        if (token == null) {
            return mvc.get().uri("/api/store/" + slug + "/orders/" + orderNumber).exchange();
        }
        return mvc.get().uri("/api/store/" + slug + "/orders/" + orderNumber)
                .header("X-Order-Token", token)
                .exchange();
    }

    private MvcTestResult tryAgain(String slug, String orderNumber, String token) {
        return mvc.post().uri("/api/store/" + slug + "/orders/" + orderNumber + "/payment-attempts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"orderToken": "%s"}
                        """.formatted(token))
                .exchange();
    }

    private static void assertOrderNotFound(MvcTestResult result) {
        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("ORDER_NOT_FOUND");
    }

    private int countPayments(String orderId) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM payments.payments WHERE order_id = ?::uuid",
                Integer.class, orderId);
        return count;
    }

    private String orderStatus(String orderId) {
        return jdbc.queryForObject("SELECT status FROM ordering.orders WHERE id = ?::uuid", String.class, orderId);
    }

    private void waitForOrderStatus(String orderId, String expected) throws InterruptedException {
        long giveUpAt = System.currentTimeMillis() + WAIT_AT_MOST_MILLIS;
        while (System.currentTimeMillis() < giveUpAt) {
            if (expected.equals(orderStatus(orderId))) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Order never became " + expected);
    }

    private void waitForLatestPayment(String orderId, String expected) throws InterruptedException {
        long giveUpAt = System.currentTimeMillis() + WAIT_AT_MOST_MILLIS;
        while (System.currentTimeMillis() < giveUpAt) {
            String status = jdbc.queryForObject("""
                    SELECT status FROM payments.payments WHERE order_id = ?::uuid
                    ORDER BY created_at DESC LIMIT 1
                    """, String.class, orderId);
            if (expected.equals(status)) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Payment never became " + expected);
    }

    private PlacedOrder placeOrder(String phone, int quantity) throws Exception {
        MvcTestResult result = mvc.post().uri("/api/store/" + shopA.slug() + "/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"customerName": "Amina", "customerPhone": "%s", "paymentMethod": "FAKE",
                         "lines": [{"productId": "%s", "quantity": %d}]}
                        """.formatted(phone, sukuma, quantity))
                .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        String json = result.getResponse().getContentAsString();
        return new PlacedOrder(JsonPath.read(json, "$.orderId"), JsonPath.read(json, "$.orderNumber"),
                JsonPath.read(json, "$.accessToken"));
    }

    private Shop signUp(String label) throws Exception {
        String suffix = label + UUID.randomUUID().toString().substring(0, 6);
        String slug = "cust-" + suffix;
        MvcTestResult result = mvc.post().uri("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"shopName": "Shop %s", "shopSlug": "%s", "ownerName": "Owner",
                         "email": "cust+%s@example.com", "password": "a-long-password"}
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

    private record PlacedOrder(String orderId, String orderNumber, String accessToken) {
    }

}
