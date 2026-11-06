package com.hezron.smecommerce.ordering;

import static org.assertj.core.api.Assertions.assertThat;

import com.hezron.smecommerce.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
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
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Placing an order through the real API (Guide 4.1, workflow W6). Each test signs up its own shop
 * "Mama Mboga ..." and stocks it through the admin API, then checks out as a guest on the storefront.
 */
@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class PlaceOrderTests {

    /** Inventory creates stock rows from catalog's event on another thread, so wait for them, but not forever. */
    private static final long WAIT_AT_MOST_MILLIS = 10_000;

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ApplicationEvents events;

    private String slug;
    private String tenantId;
    private String ownerToken;
    private String sukuma;

    @BeforeEach
    void shopWithTenSukumaInStock() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        slug = "mama-mboga-" + suffix;
        MvcTestResult signUp = mvc.post().uri("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"shopName": "Mama Mboga", "shopSlug": "%s", "ownerName": "Wanjiru",
                         "email": "order+%s@example.com", "password": "a-long-password"}
                        """.formatted(slug, suffix))
                .exchange();
        assertThat(signUp).hasStatus(HttpStatus.CREATED);
        String json = signUp.getResponse().getContentAsString();
        ownerToken = JsonPath.read(json, "$.accessToken");
        tenantId = JsonPath.read(json, "$.tenantId");

        sukuma = createStockedProduct("Sukuma bundle", 5000, 10);
    }

    @Test
    void placedOrderWaitsForPaymentWithItsStockHeld() throws Exception {
        MvcTestResult result = placeOrder(sukuma, 2, "0712 345 678");

        // The issue's "Done when".
        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("PENDING_PAYMENT");
        assertThat(result).bodyJson().extractingPath("$.totalMinor").isEqualTo(10000);
        assertThat(result).bodyJson().extractingPath("$.currency").isEqualTo("KES");
        assertThat(result).bodyJson().extractingPath("$.orderNumber").asString().matches("^MM[A-Z0-9]*-000001$");

        String orderId = JsonPath.read(result.getResponse().getContentAsString(), "$.orderId");
        assertThat(reservedOf(sukuma)).isEqualTo(2);
        String hold = jdbc.queryForObject(
                "SELECT status || ' ' || quantity FROM inventory.reservations WHERE order_id = ?::uuid",
                String.class, orderId);
        assertThat(hold).isEqualTo("HELD 2");

        List<String> statusHistory = jdbc.queryForList("""
                SELECT coalesce(from_status, '-') || '>' || to_status FROM ordering.order_status_history
                WHERE order_id = ?::uuid ORDER BY created_at
                """, String.class, orderId);
        assertThat(statusHistory).containsExactly("->CREATED", "CREATED>PENDING_PAYMENT");

        assertThat(events.stream(OrderPlacedEvent.class).count()).isEqualTo(1);
    }

    @Test
    void theLineKeepsTheNameAndPriceOfTheMomentOfPurchase() throws Exception {
        MvcTestResult result = placeOrder(sukuma, 2, "0712345678");
        String orderId = JsonPath.read(result.getResponse().getContentAsString(), "$.orderId");

        String line = jdbc.queryForObject("""
                SELECT product_name || ' ' || unit_price_minor || ' x' || quantity || ' = ' || line_total_minor
                FROM ordering.order_lines WHERE order_id = ?::uuid
                """, String.class, orderId);
        assertThat(line).isEqualTo("Sukuma bundle 5000 x2 = 10000");
    }

    @Test
    void aPriceSentByTheBrowserIsIgnored() {
        MvcTestResult result = mvc.post().uri("/api/store/" + slug + "/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"customerName": "Mallory", "customerPhone": "0712345678", "paymentMethod": "MPESA",
                         "totalMinor": 1,
                         "lines": [{"productId": "%s", "quantity": 2, "unitPriceMinor": 1, "priceMinor": 1}]}
                        """.formatted(sukuma))
                .exchange();

        assertThat(result).hasStatus(HttpStatus.CREATED);
        // 2 x the server's price of 5000, not 2 x 1.
        assertThat(result).bodyJson().extractingPath("$.totalMinor").isEqualTo(10000);
    }

    @Test
    void notEnoughStockSavesNothingAtAll() throws Exception {
        MvcTestResult result = placeOrder(sukuma, 11, "0712345678");

        assertThat(result).hasStatus(HttpStatus.CONFLICT);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INSUFFICIENT_STOCK");
        assertThat(result).bodyJson().extractingPath("$.available").isEqualTo(10);

        // Everything in the transaction was rolled back: no order, no customer, no hold...
        assertThat(countFor("ordering.orders")).isZero();
        assertThat(countFor("identity.customers")).isZero();
        assertThat(reservedOf(sukuma)).isZero();
        // ...and the order number was given back: the next order is still number 1.
        MvcTestResult next = placeOrder(sukuma, 1, "0712345678");
        assertThat(next).bodyJson().extractingPath("$.orderNumber").asString().endsWith("-000001");
    }

    @Test
    void aDraftProductCantBeOrdered() throws Exception {
        String draft = createProductAndReturnId("Coming soon", 100, "DRAFT");

        MvcTestResult result = placeOrder(draft, 1, "0712345678");

        assertThat(result).hasStatus(HttpStatus.CONFLICT);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("PRODUCT_UNAVAILABLE");
        assertThat(result).bodyJson().extractingPath("$.productId").isEqualTo(draft);
    }

    @Test
    void anotherShopsProductCantBeOrderedHere() throws Exception {
        String foreignProduct = UUID.randomUUID().toString();

        MvcTestResult result = placeOrder(foreignProduct, 1, "0712345678");

        assertThat(result).hasStatus(HttpStatus.CONFLICT);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("PRODUCT_UNAVAILABLE");
    }

    @Test
    void anUnknownShopIsNotFound() {
        MvcTestResult result = mvc.post().uri("/api/store/no-such-shop-" + UUID.randomUUID().toString().substring(0, 6) + "/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(orderJson(sukuma, 1, "0712345678"))
                .exchange();

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("SHOP_NOT_FOUND");
    }

    @Test
    void aNumberThatIsntAKenyanMobileIsRefused() {
        MvcTestResult result = placeOrder(sukuma, 1, "020 123 4567");

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("PHONE_INVALID");
    }

    @Test
    void theSameProductOnTwoLinesIsRefused() {
        MvcTestResult result = mvc.post().uri("/api/store/" + slug + "/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"customerName": "Amina", "customerPhone": "0712345678", "paymentMethod": "MPESA",
                         "lines": [{"productId": "%s", "quantity": 1}, {"productId": "%s", "quantity": 1}]}
                        """.formatted(sukuma, sukuma))
                .exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("ORDER_INVALID");
    }

    @Test
    void anEmptyCartIsAValidationError() {
        MvcTestResult result = mvc.post().uri("/api/store/" + slug + "/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"customerName": "Amina", "customerPhone": "0712345678", "paymentMethod": "MPESA", "lines": []}
                        """)
                .exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void twoOrdersFromOnePhoneAreOneCustomerStoredInTheStandardForm() {
        placeOrder(sukuma, 1, "0712 345 678");
        placeOrder(sukuma, 1, "+254712345678");

        List<String> customers = jdbc.queryForList(
                "SELECT phone FROM identity.customers WHERE tenant_id = ?::uuid", String.class, tenantId);
        assertThat(customers).containsExactly("254712345678");
    }

    @Test
    void onlyAHashOfTheOrderLinkTokenIsStored() throws Exception {
        MvcTestResult result = placeOrder(sukuma, 1, "0712345678");
        String json = result.getResponse().getContentAsString();
        String token = JsonPath.read(json, "$.accessToken");
        String orderId = JsonPath.read(json, "$.orderId");

        String storedHash = jdbc.queryForObject(
                "SELECT access_token_hash FROM ordering.orders WHERE id = ?::uuid", String.class, orderId);
        assertThat(storedHash).isNotEqualTo(token);
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
        assertThat(storedHash).isEqualTo(HexFormat.of().formatHex(digest));
    }

    private MvcTestResult placeOrder(String productId, int quantity, String phone) {
        return mvc.post().uri("/api/store/" + slug + "/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(orderJson(productId, quantity, phone))
                .exchange();
    }

    private static String orderJson(String productId, int quantity, String phone) {
        return """
                {"customerName": "Amina", "customerPhone": "%s", "paymentMethod": "MPESA",
                 "lines": [{"productId": "%s", "quantity": %d}]}
                """.formatted(phone, productId, quantity);
    }

    /** Creates an ACTIVE product through the admin API and restocks it once its stock row exists. */
    private String createStockedProduct(String name, long priceMinor, int stock) throws Exception {
        String productId = createProductAndReturnId(name, priceMinor, "ACTIVE");
        waitForStockRow(productId);
        MvcTestResult restock = mvc.post().uri("/api/admin/stock/" + productId + "/adjustments")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"change": %d, "reason": "RESTOCK"}
                        """.formatted(stock))
                .exchange();
        assertThat(restock).hasStatus(HttpStatus.OK);
        return productId;
    }

    private String createProductAndReturnId(String name, long priceMinor, String status) throws Exception {
        MvcTestResult result = mvc.post().uri("/api/admin/products")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "%s", "priceMinor": %d, "status": "%s"}
                        """.formatted(name, priceMinor, status))
                .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private void waitForStockRow(String productId) throws InterruptedException {
        long giveUpAt = System.currentTimeMillis() + WAIT_AT_MOST_MILLIS;
        while (System.currentTimeMillis() < giveUpAt) {
            Integer rows = jdbc.queryForObject(
                    "SELECT count(*) FROM inventory.stock_items WHERE product_id = ?::uuid", Integer.class, productId);
            if (rows != null && rows == 1) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Inventory never created the stock row for " + productId);
    }

    private int reservedOf(String productId) {
        Integer reserved = jdbc.queryForObject(
                "SELECT reserved FROM inventory.stock_items WHERE product_id = ?::uuid", Integer.class, productId);
        return reserved;
    }

    private int countFor(String table) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM " + table + " WHERE tenant_id = ?::uuid", Integer.class, tenantId);
        return count;
    }

}
