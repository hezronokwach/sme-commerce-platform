package com.hezron.smecommerce;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Issue "Hardening and observability". Done when: one order can be traced through the logs by its
 * correlation id. Each test uses its own IP address, so the rate limits don't carry over between tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = { "app.rate-limit.enabled=true", "app.ordering.orders-per-phone-per-minute=2" })
class HardeningTests {

    private static final long WAIT_AT_MOST_MILLIS = 20_000;
    private static int nextAddress = 1;

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MeterRegistry metrics;

    private String ip;

    @BeforeEach
    void aFreshAddress() {
        ip = "10.1.0." + nextAddress;
        nextAddress = nextAddress + 1;
    }

    @Test
    void oneOrderCanBeTracedThroughTheLogsByItsCorrelationId(CapturedOutput logs) throws Exception {
        Shop shop = signUp();
        String product = stockedProduct(shop);
        String traceId = "trace-" + UUID.randomUUID();

        MvcTestResult placed = placeOrder(shop, product, "0712345670", traceId);
        assertThat(placed).hasStatus(HttpStatus.CREATED);
        String orderId = JsonPath.read(placed.getResponse().getContentAsString(), "$.orderId");
        String orderNumber = JsonPath.read(placed.getResponse().getContentAsString(), "$.orderNumber");
        waitForOrderStatus(orderId, "PAID");

        // The request's thread, the payment's thread, the callback's thread and the listener's thread.
        List<String> traced = new ArrayList<>();
        for (String line : logs.getOut().split("\\R")) {
            if (line.contains(traceId)) {
                traced.add(line);
            }
        }
        assertThat(traced).anyMatch(line -> line.contains("Order " + orderNumber + " status null -> CREATED"));
        assertThat(traced).anyMatch(line -> line.contains("Fake payment"));
        assertThat(traced).anyMatch(line -> line.contains("Order " + orderNumber + " status PENDING_PAYMENT -> PAID"));
        // And the customer's phone is never in the logs.
        assertThat(logs.getOut()).doesNotContain("254712345670");
    }

    @Test
    void theSixthLoginInAMinuteIsBlockedWithTheUsualErrorShape() {
        for (int i = 0; i < 5; i++) {
            assertThat(login()).hasStatus(HttpStatus.UNAUTHORIZED);
        }

        MvcTestResult sixth = login();

        assertThat(sixth).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(sixth).bodyJson().extractingPath("$.code").isEqualTo("TOO_MANY_REQUESTS");
        assertThat(sixth.getResponse().getHeader("X-Correlation-Id")).isNotBlank();
    }

    @Test
    void oneAddressDoesNotUseUpAnothersLimit() {
        for (int i = 0; i < 6; i++) {
            login();
        }
        ip = "10.2.0." + nextAddress;
        nextAddress = nextAddress + 1;

        assertThat(login()).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void onePhoneCannotBeFloodedWithOrders() throws Exception {
        Shop shop = signUp();
        String product = stockedProduct(shop);

        assertThat(placeOrder(shop, product, "0712345673", "a")).hasStatus(HttpStatus.CREATED);
        assertThat(placeOrder(shop, product, "0712345673", "b")).hasStatus(HttpStatus.CREATED);
        MvcTestResult third = placeOrder(shop, product, "0712345673", "c");

        assertThat(third).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(third).bodyJson().extractingPath("$.code").isEqualTo("TOO_MANY_ORDERS");
        assertThat(placeOrder(shop, product, "0722345673", "d")).hasStatus(HttpStatus.CREATED);
    }

    @Test
    void onlyOurOwnFrontendsMayCallTheApiFromABrowser() {
        MvcTestResult ours = preflight("http://localhost:5173");
        assertThat(ours).hasStatus(HttpStatus.OK);
        assertThat(ours.getResponse().getHeader("Access-Control-Allow-Origin")).isEqualTo("http://localhost:5173");

        MvcTestResult other = preflight("https://evil.example");
        assertThat(other).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(other.getResponse().getHeader("Access-Control-Allow-Origin")).isNull();
    }

    @Test
    void metricsAreOnlyServedToTheServerItself() {
        MvcTestResult fromOutside = mvc.get().uri("/actuator/metrics").with(this::fromCaller).exchange();
        assertThat(fromOutside.getResponse().getStatus()).isIn(401, 403);

        MvcTestResult health = mvc.get().uri("/actuator/health").with(this::fromCaller).exchange();
        assertThat(health).hasStatus(HttpStatus.OK);
    }

    @Test
    void aPaidOrderIsCountedInTheMetrics() throws Exception {
        Shop shop = signUp();
        String product = stockedProduct(shop);
        double before = metrics.counter("sme.payments.results", "provider", "FAKE", "outcome", "SUCCEEDED").count();

        MvcTestResult placed = placeOrder(shop, product, "0733345670", "metric");
        waitForOrderStatus(JsonPath.read(placed.getResponse().getContentAsString(), "$.orderId"), "PAID");

        double after = metrics.counter("sme.payments.results", "provider", "FAKE", "outcome", "SUCCEEDED").count();
        assertThat(after).isGreaterThan(before);
    }

    @Test
    void aPriceChangeIsAuditedWithWhoAndWhat() throws Exception {
        Shop shop = signUp();
        String product = stockedProduct(shop);
        Integer version = jdbc.queryForObject("SELECT version FROM catalog.products WHERE id = ?::uuid",
                Integer.class, product);

        MvcTestResult updated = mvc.put().uri("/api/admin/products/" + product).with(this::fromCaller)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + shop.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"version": %d, "name": "Sukuma", "priceMinor": 6000, "status": "ACTIVE"}
                        """.formatted(version))
                .exchange();
        assertThat(updated).hasStatus(HttpStatus.OK);

        String row = jdbc.queryForObject("""
                SELECT actor_type || ' ' || (details ->> 'oldPriceMinor') || ' ' || (details ->> 'newPriceMinor')
                       || ' ' || ip_address
                FROM audit.audit_log
                WHERE tenant_id = ?::uuid AND action = 'PRODUCT_PRICE_CHANGED' AND entity_id = ?::uuid
                """, String.class, shop.tenantId(), product);
        assertThat(row).isEqualTo("STAFF 5000 6000 " + ip);
    }

    // --- helpers ----------------------------------------------------------------------------------

    private org.springframework.mock.web.MockHttpServletRequest fromCaller(
            org.springframework.mock.web.MockHttpServletRequest request) {
        request.setRemoteAddr(ip);
        return request;
    }

    private MvcTestResult login() {
        return mvc.post().uri("/api/auth/login").with(this::fromCaller)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "nobody@example.com", "password": "a-wrong-password"}
                        """)
                .exchange();
    }

    private MvcTestResult preflight(String origin) {
        return mvc.options().uri("/api/store/any/products")
                .header(HttpHeaders.ORIGIN, origin)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .exchange();
    }

    private MvcTestResult placeOrder(Shop shop, String product, String phone, String correlationId) {
        return mvc.post().uri("/api/store/" + shop.slug() + "/orders").with(this::fromCaller)
                .header("X-Correlation-Id", correlationId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"customerName": "Amina", "customerPhone": "%s", "paymentMethod": "FAKE",
                         "lines": [{"productId": "%s", "quantity": 1}]}
                        """.formatted(phone, product))
                .exchange();
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
        throw new AssertionError("The order never became " + expected);
    }

    private Shop signUp() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String slug = "hard-" + suffix;
        MvcTestResult result = mvc.post().uri("/api/auth/signup").with(this::fromCaller)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"shopName": "Shop %s", "shopSlug": "%s", "ownerName": "Owner",
                         "email": "hard+%s@example.com", "password": "a-long-password"}
                        """.formatted(suffix, slug, suffix))
                .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        String tenantId = jdbc.queryForObject("SELECT id::text FROM tenant.tenants WHERE slug = ?", String.class, slug);
        return new Shop(slug, JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken"), tenantId);
    }

    private String stockedProduct(Shop shop) throws Exception {
        MvcTestResult created = mvc.post().uri("/api/admin/products").with(this::fromCaller)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + shop.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "Sukuma", "priceMinor": 5000}
                        """)
                .exchange();
        assertThat(created).hasStatus(HttpStatus.CREATED);
        String productId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        long giveUpAt = System.currentTimeMillis() + WAIT_AT_MOST_MILLIS;
        while (System.currentTimeMillis() < giveUpAt) {
            Integer rows = jdbc.queryForObject("SELECT count(*) FROM inventory.stock_items WHERE product_id = ?::uuid",
                    Integer.class, productId);
            if (rows != null && rows == 1) {
                break;
            }
            Thread.sleep(50);
        }
        MvcTestResult restock = mvc.post().uri("/api/admin/stock/" + productId + "/adjustments").with(this::fromCaller)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + shop.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"change": 20, "reason": "RESTOCK"}
                        """)
                .exchange();
        assertThat(restock).hasStatus(HttpStatus.OK);
        return productId;
    }

    private record Shop(String slug, String token, String tenantId) {
    }

}
