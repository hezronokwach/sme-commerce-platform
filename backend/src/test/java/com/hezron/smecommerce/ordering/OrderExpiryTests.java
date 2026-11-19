package com.hezron.smecommerce.ordering;

import static org.assertj.core.api.Assertions.assertThat;

import com.hezron.smecommerce.TestcontainersConfiguration;
import com.hezron.smecommerce.payments.PaymentSucceededEvent;
import com.hezron.smecommerce.shared.TenantId;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
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
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Guide 3.5: unpaid orders past their deadline expire and give their stock back, but never while a
 * payment is still under way. The expiry job runs for real, every 200 ms; each test moves an order's
 * deadline into the past with SQL instead of waiting 15 minutes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class OrderExpiryTests {

    private static final long WAIT_AT_MOST_MILLIS = 20_000;

    @DynamicPropertySource
    static void expireOften(DynamicPropertyRegistry registry) {
        registry.add("app.ordering.expiry.every", () -> "200ms");
    }

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ApplicationEventPublisher events;

    @Autowired
    TransactionTemplate transactions;

    private Shop shop;
    private String sukuma;

    @BeforeEach
    void aShopWithStock() throws Exception {
        shop = signUp();
        sukuma = createStockedProduct(shop, "Sukuma bundle", 5000, 20);
    }

    @Test
    void anUnpaidOrderPastItsDeadlineExpiresAndItsStockGoesBackOnSale() throws Exception {
        String orderId = placeOrder(shop, sukuma, "0712345671", 3); // digit 1: the customer cancels the prompt
        waitForLatestPayment(orderId, "FAILED");
        assertThat(reserved(sukuma)).isEqualTo(3);

        moveDeadlineIntoThePast(orderId);

        waitForOrderStatus(orderId, "EXPIRED");
        assertThat(reserved(sukuma)).isEqualTo(0);
        assertThat(onHand(sukuma)).isEqualTo(20);
        String reason = jdbc.queryForObject("""
                SELECT reason FROM ordering.order_status_history WHERE order_id = ?::uuid AND to_status = 'EXPIRED'
                """, String.class, orderId);
        assertThat(reason).isEqualTo("Not paid within the time limit");
    }

    @Test
    void anOrderWhosePaymentIsStillUnderWayWaitsForReconciliation() throws Exception {
        String orderId = placeOrder(shop, sukuma, "0712345673", 1); // digit 3: never answers
        waitForLatestPayment(orderId, "PENDING");

        moveDeadlineIntoThePast(orderId);
        Thread.sleep(1000); // five rounds of the job

        // The customer may have paid at the last moment: the stock stays held.
        assertThat(orderStatus(orderId)).isEqualTo("PENDING_PAYMENT");
        assertThat(reserved(sukuma)).isEqualTo(1);

        // Reconciliation gives up waiting ("checking" for the customer): now the order may expire.
        jdbc.update("UPDATE payments.payments SET status = 'TIMED_OUT' WHERE order_id = ?::uuid", orderId);

        waitForOrderStatus(orderId, "EXPIRED");
        assertThat(reserved(sukuma)).isEqualTo(0);
    }

    @Test
    void moneyThatArrivesAfterTheExpiryStillMarksTheOrderPaid() throws Exception {
        String orderId = placeOrder(shop, sukuma, "0712345671", 1);
        waitForLatestPayment(orderId, "FAILED");
        moveDeadlineIntoThePast(orderId);
        waitForOrderStatus(orderId, "EXPIRED");

        // A success that comes late (a callback, or reconciliation finding it).
        transactions.executeWithoutResult(status -> events.publishEvent(new PaymentSucceededEvent(
                TenantId.of(UUID.fromString(shop.tenantId())), UUID.fromString(orderId), UUID.randomUUID(),
                "LATE123", 5000)));

        waitForOrderStatus(orderId, "PAID");
        String reason = jdbc.queryForObject("""
                SELECT reason FROM ordering.order_status_history WHERE order_id = ?::uuid AND to_status = 'PAID'
                """, String.class, orderId);
        assertThat(reason).contains("LATE123").contains("check the stock by hand");
    }

    @Test
    void runningTheJobAgainChangesNothingMore() throws Exception {
        String orderId = placeOrder(shop, sukuma, "0712345671", 2);
        waitForLatestPayment(orderId, "FAILED");
        moveDeadlineIntoThePast(orderId);
        waitForOrderStatus(orderId, "EXPIRED");

        Thread.sleep(1000); // five more rounds

        Integer expiredRows = jdbc.queryForObject("""
                SELECT count(*) FROM ordering.order_status_history WHERE order_id = ?::uuid AND to_status = 'EXPIRED'
                """, Integer.class, orderId);
        assertThat(expiredRows).isEqualTo(1);
        assertThat(reserved(sukuma)).isEqualTo(0);
        assertThat(onHand(sukuma)).isEqualTo(20);
    }

    @Test
    void eachShopsOrdersExpireWithTheirOwnShop() throws Exception {
        Shop otherShop = signUp();
        String otherSukuma = createStockedProduct(otherShop, "Sukuma bundle", 5000, 10);
        String ours = placeOrder(shop, sukuma, "0712345671", 1);
        String theirs = placeOrder(otherShop, otherSukuma, "0712345671", 4);
        waitForLatestPayment(ours, "FAILED");
        waitForLatestPayment(theirs, "FAILED");

        moveDeadlineIntoThePast(ours);
        moveDeadlineIntoThePast(theirs);

        waitForOrderStatus(ours, "EXPIRED");
        waitForOrderStatus(theirs, "EXPIRED");
        assertThat(reserved(sukuma)).isEqualTo(0);
        assertThat(reserved(otherSukuma)).isEqualTo(0);
        // The history rows belong to each order's own shop.
        assertThat(historyTenant(ours)).isEqualTo(shop.tenantId());
        assertThat(historyTenant(theirs)).isEqualTo(otherShop.tenantId());
    }

    // --- helpers ----------------------------------------------------------------------------------

    private void moveDeadlineIntoThePast(String orderId) {
        jdbc.update("UPDATE ordering.orders SET expires_at = now() - interval '1 minute' WHERE id = ?::uuid", orderId);
    }

    private String historyTenant(String orderId) {
        return jdbc.queryForObject("""
                SELECT tenant_id::text FROM ordering.order_status_history
                WHERE order_id = ?::uuid AND to_status = 'EXPIRED'
                """, String.class, orderId);
    }

    private Integer reserved(String productId) {
        return jdbc.queryForObject("SELECT reserved FROM inventory.stock_items WHERE product_id = ?::uuid",
                Integer.class, productId);
    }

    private Integer onHand(String productId) {
        return jdbc.queryForObject("SELECT on_hand FROM inventory.stock_items WHERE product_id = ?::uuid",
                Integer.class, productId);
    }

    private String orderStatus(String orderId) {
        return jdbc.queryForObject("SELECT status FROM ordering.orders WHERE id = ?::uuid", String.class, orderId);
    }

    private String latestPaymentStatus(String orderId) {
        return jdbc.queryForObject("SELECT status FROM payments.payments WHERE order_id = ?::uuid "
                + "ORDER BY created_at DESC LIMIT 1", String.class, orderId);
    }

    private void waitForOrderStatus(String orderId, String expected) throws InterruptedException {
        long giveUpAt = System.currentTimeMillis() + WAIT_AT_MOST_MILLIS;
        while (System.currentTimeMillis() < giveUpAt) {
            if (expected.equals(orderStatus(orderId))) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Order never became " + expected + "; it is " + orderStatus(orderId));
    }

    private void waitForLatestPayment(String orderId, String expected) throws InterruptedException {
        long giveUpAt = System.currentTimeMillis() + WAIT_AT_MOST_MILLIS;
        while (System.currentTimeMillis() < giveUpAt) {
            if (expected.equals(latestPaymentStatus(orderId))) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Payment never became " + expected + "; it is " + latestPaymentStatus(orderId));
    }

    private String placeOrder(Shop shop, String productId, String phone, int quantity) throws Exception {
        MvcTestResult result = mvc.post().uri("/api/store/" + shop.slug() + "/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"customerName": "Amina", "customerPhone": "%s", "paymentMethod": "FAKE",
                         "lines": [{"productId": "%s", "quantity": %d}]}
                        """.formatted(phone, productId, quantity))
                .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return JsonPath.read(result.getResponse().getContentAsString(), "$.orderId");
    }

    private Shop signUp() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String slug = "expiry-" + suffix;
        MvcTestResult result = mvc.post().uri("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"shopName": "Shop %s", "shopSlug": "%s", "ownerName": "Owner",
                         "email": "expiry+%s@example.com", "password": "a-long-password"}
                        """.formatted(suffix, slug, suffix))
                .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        String tenantId = jdbc.queryForObject("SELECT id::text FROM tenant.tenants WHERE slug = ?", String.class, slug);
        return new Shop(slug, JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken"), tenantId);
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

    private record Shop(String slug, String token, String tenantId) {
    }

}
