package com.hezron.smecommerce.payments;

import static org.assertj.core.api.Assertions.assertThat;

import com.hezron.smecommerce.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Issue "Buffer + reconciliation and timeouts", Done-when: a payment stuck in pending resolves itself.
 * <p>
 * The reconciliation job runs for real here, with every wait shortened from minutes to milliseconds.
 * The fake provider's digits pick the trouble: 6 pays but its callback is lost, 7's start times out,
 * 3 never answers, 0 pays normally.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class ReconciliationTests {

    private static final long WAIT_AT_MOST_MILLIS = 20_000;
    private static final PretendStripe STRIPE = startStripe();

    @DynamicPropertySource
    static void shortWaits(DynamicPropertyRegistry registry) {
        registry.add("app.payments.reconciliation.every", () -> "200ms");
        registry.add("app.payments.reconciliation.first-check-after-accepted", () -> "300ms");
        registry.add("app.payments.reconciliation.first-check-after-unknown", () -> "300ms");
        registry.add("app.payments.reconciliation.backoff", () -> "200ms");
        registry.add("app.payments.reconciliation.unknown-give-up-after", () -> "1s");
        registry.add("app.payments.reconciliation.timed-out-after", () -> "2s");
        registry.add("app.payments.reconciliation.timed-out-check-every", () -> "1h");
        registry.add("app.payments.card.base-url", STRIPE::url);
        registry.add("app.payments.card.secret-key", () -> "sk_test_pretend");
        registry.add("app.payments.card.publishable-key", () -> "pk_test_pretend");
        registry.add("app.payments.card.webhook-secret", () -> "whsec_test_only");
    }

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbc;

    private Shop shop;
    private String sukuma;

    @BeforeEach
    void aShopWithStock() throws Exception {
        shop = signUp();
        sukuma = createStockedProduct(shop, "Sukuma bundle", 5000, 20);
    }

    @Test
    void aPaidPaymentWhoseCallbackWasLostIsFoundAndTheOrderBecomesPaid() throws Exception {
        String orderId = placeOrder("0712345676", "FAKE"); // digit 6: paid, but no callback ever comes

        waitForOrderStatus(orderId, "PAID");

        assertThat(latestPayment(orderId, "status")).isEqualTo("SUCCEEDED");
        // The answer came from asking, and callback_log says so.
        Integer asked = jdbc.queryForObject("""
                SELECT count(*) FROM payments.callback_log
                WHERE provider_reference = ? AND outcome = 'APPLIED' AND raw_body ->> 'source' = 'status-query'
                """, Integer.class, latestPayment(orderId, "provider_reference"));
        assertThat(asked).isEqualTo(1);
    }

    @Test
    void aStartThatGotNoAnswerAtAllIsGivenUpAndTheCustomerCanTryAgain() throws Exception {
        String orderId = placeOrder("0712345677", "FAKE"); // digit 7: the start request times out

        waitForLatestPayment(orderId, "FAILED");

        assertThat(latestPayment(orderId, "result_code")).isEqualTo("NO_REPLY");
        assertThat(latestPayment(orderId, "failure_reason")).isEqualTo("We couldn't confirm the payment. Please try again.");
        // Not a hard "no": the order still waits, so "Try again" works.
        assertThat(orderStatus(orderId)).isEqualTo("PENDING_PAYMENT");
    }

    @Test
    void aPaymentThatNeverAnswersIsAskedAgainLessOftenThenTimesOutAsChecking() throws Exception {
        String orderId = placeOrder("0712345673", "FAKE"); // digit 3: never answers, ever

        waitForLatestPayment(orderId, "TIMED_OUT");

        Integer attempts = jdbc.queryForObject("SELECT check_attempts FROM payments.payments WHERE order_id = ?::uuid",
                Integer.class, orderId);
        assertThat(attempts).isGreaterThan(1);
        // Still asked about, but hourly now: the truth may still come.
        Boolean nextLater = jdbc.queryForObject("""
                SELECT next_check_at > now() + interval '30 minutes' FROM payments.payments WHERE order_id = ?::uuid
                """, Boolean.class, orderId);
        assertThat(nextLater).isTrue();
        assertThat(orderStatus(orderId)).isEqualTo("PENDING_PAYMENT");
    }

    @Test
    void whenTheCallbackAndTheStatusQueryBothArriveTheResultIsAppliedOnce() throws Exception {
        String orderId = placeOrder("0712345670", "FAKE"); // digit 0: a callback after 500 ms, and a query at ~300 ms

        waitForOrderStatus(orderId, "PAID");
        Thread.sleep(1500); // let the slower of the two arrive too

        Integer paidRows = jdbc.queryForObject(
                "SELECT count(*) FROM ordering.order_status_history WHERE order_id = ?::uuid AND to_status = 'PAID'",
                Integer.class, orderId);
        assertThat(paidRows).isEqualTo(1);
        Integer applied = jdbc.queryForObject(
                "SELECT count(*) FROM payments.callback_log WHERE provider_reference = ? AND outcome = 'APPLIED'",
                Integer.class, latestPayment(orderId, "provider_reference"));
        assertThat(applied).isEqualTo(1);
    }

    @Test
    void aCardPaymentWithNoWebhookIsResolvedByAskingStripe() throws Exception {
        switchMethod("CARD", true);
        String orderId = placeOrder("0712345670", "CARD");
        String intentId = waitForReference(orderId);

        // The customer paid, but Stripe's webhook never reached us.
        STRIPE.setStatus(intentId, "succeeded");

        waitForOrderStatus(orderId, "PAID");
        assertThat(latestPayment(orderId, "receipt_number")).isEqualTo("ch_pretend_" + intentId);
    }

    @Test
    void aCardPaymentStillBeingTypedStaysOpen() throws Exception {
        switchMethod("CARD", true);
        String orderId = placeOrder("0712345670", "CARD");
        waitForReference(orderId);

        Thread.sleep(1200); // several rounds of asking; Stripe keeps saying "requires_payment_method"

        assertThat(latestPayment(orderId, "status")).isEqualTo("PENDING");
        Integer attempts = jdbc.queryForObject("SELECT check_attempts FROM payments.payments WHERE order_id = ?::uuid",
                Integer.class, orderId);
        assertThat(attempts).isGreaterThan(0);
    }

    // --- helpers ----------------------------------------------------------------------------------

    private String latestPayment(String orderId, String column) {
        return jdbc.queryForObject("SELECT " + column + "::text FROM payments.payments WHERE order_id = ?::uuid "
                + "ORDER BY created_at DESC LIMIT 1", String.class, orderId);
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
        throw new AssertionError("Order never became " + expected + "; payment is " + latestPayment(orderId, "status"));
    }

    private void waitForLatestPayment(String orderId, String expected) throws InterruptedException {
        long giveUpAt = System.currentTimeMillis() + WAIT_AT_MOST_MILLIS;
        while (System.currentTimeMillis() < giveUpAt) {
            if (expected.equals(latestPayment(orderId, "status"))) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Payment never became " + expected + "; it is " + latestPayment(orderId, "status"));
    }

    private String waitForReference(String orderId) throws InterruptedException {
        long giveUpAt = System.currentTimeMillis() + WAIT_AT_MOST_MILLIS;
        while (System.currentTimeMillis() < giveUpAt) {
            String reference = latestPayment(orderId, "provider_reference");
            if (reference != null) {
                return reference;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("The payment never reached the provider");
    }

    private String placeOrder(String phone, String method) throws Exception {
        MvcTestResult result = mvc.post().uri("/api/store/" + shop.slug() + "/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"customerName": "Amina", "customerPhone": "%s", "paymentMethod": "%s",
                         "lines": [{"productId": "%s", "quantity": 1}]}
                        """.formatted(phone, method, sukuma))
                .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return JsonPath.read(result.getResponse().getContentAsString(), "$.orderId");
    }

    private void switchMethod(String provider, boolean enabled) {
        MvcTestResult result = mvc.put().uri("/api/admin/settings/payment-methods/" + provider)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + shop.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"enabled": %s}
                        """.formatted(enabled))
                .exchange();
        assertThat(result).hasStatus(HttpStatus.OK);
    }

    private static PretendStripe startStripe() {
        try {
            return new PretendStripe();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private Shop signUp() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String slug = "recon-" + suffix;
        MvcTestResult result = mvc.post().uri("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"shopName": "Shop %s", "shopSlug": "%s", "ownerName": "Owner",
                         "email": "recon+%s@example.com", "password": "a-long-password"}
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

}
