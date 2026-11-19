package com.hezron.smecommerce.payments;

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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * A provider that stops answering (ADR 0014). Its own Spring context, because the circuit breaker's
 * state would otherwise leak into other test classes: here 2 failures open it, for 1 second.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class ProviderDownTests {

    private static final long WAIT_AT_MOST_MILLIS = 20_000;

    @DynamicPropertySource
    static void aQuickBreaker(DynamicPropertyRegistry registry) {
        registry.add("app.payments.provider-calls.failures-to-open", () -> "2");
        registry.add("app.payments.provider-calls.open-for", () -> "1s");
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
    void whileTheProviderIsDownCheckoutGetsAClearRetryableAnswerThenRecovers() throws Exception {
        // Digit 7: the start request times out. Twice in a row opens the breaker.
        String first = placeOrder("0712345677");
        waitForLatestPayment(first, "UNKNOWN");
        String second = placeOrder("0712345677");
        waitForLatestPayment(second, "UNKNOWN");

        // The provider isn't even asked: an honest, immediate "try again in a minute", not "checking".
        String third = placeOrder("0712345670");
        waitForLatestPayment(third, "FAILED");
        assertThat(latestPayment(third, "failure_reason"))
                .isEqualTo("Payments are having trouble right now. Please try again in a minute.");
        assertThat(latestPayment(third, "provider_reference")).isNull();
        // Retryable: the order keeps waiting, with its stock held.
        Thread.sleep(300);
        assertThat(orderStatus(third)).isEqualTo("PENDING_PAYMENT");

        // After the wait one trial call goes through; it works, so payments flow again.
        Thread.sleep(1100);
        String fourth = placeOrder("0712345670");
        waitForOrderStatus(fourth, "PAID");
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

    private String placeOrder(String phone) throws Exception {
        MvcTestResult result = mvc.post().uri("/api/store/" + shop.slug() + "/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"customerName": "Amina", "customerPhone": "%s", "paymentMethod": "FAKE",
                         "lines": [{"productId": "%s", "quantity": 1}]}
                        """.formatted(phone, sukuma))
                .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return JsonPath.read(result.getResponse().getContentAsString(), "$.orderId");
    }

    private Shop signUp() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String slug = "down-" + suffix;
        MvcTestResult result = mvc.post().uri("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"shopName": "Shop %s", "shopSlug": "%s", "ownerName": "Owner",
                         "email": "down+%s@example.com", "password": "a-long-password"}
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
