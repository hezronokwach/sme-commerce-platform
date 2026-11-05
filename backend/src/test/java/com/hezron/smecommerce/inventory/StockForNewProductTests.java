package com.hezron.smecommerce.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.hezron.smecommerce.TestcontainersConfiguration;
import com.hezron.smecommerce.inventory.internal.application.StockService;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
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
 * Workflow W3 across two modules: catalog creates a product and announces it; inventory hears it and
 * creates the stock record (Guide 3.2).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class StockForNewProductTests {

    /** The listener runs on another thread, so the test waits for its result, but never forever. */
    private static final long WAIT_AT_MOST_MILLIS = 10_000;

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    StockService stockService;

    @Test
    void newProductGetsAStockRecordWithNothingInStock() throws Exception {
        String[] shop = signUp();
        String productId = createProduct(shop[1], "Sukuma bundle");

        boolean created = waitUntilStockItemExists(productId);

        assertThat(created).isTrue();
        Integer onHand = jdbc.queryForObject(
                "SELECT on_hand FROM inventory.stock_items WHERE product_id = ?::uuid", Integer.class, productId);
        assertThat(onHand).isZero();
        String tenantOfStock = jdbc.queryForObject(
                "SELECT tenant_id::text FROM inventory.stock_items WHERE product_id = ?::uuid", String.class, productId);
        assertThat(tenantOfStock).isEqualTo(shop[0]);
    }

    @Test
    void theEventIsMarkedDoneOnceInventoryHandledIt() throws Exception {
        String[] shop = signUp();
        String productId = createProduct(shop[1], "Mandazi");
        waitUntilStockItemExists(productId);

        // Modulith stored one row per listener in event_publication; a finished one has a completion date.
        boolean completed = waitUntilEventCompleted(productId);
        assertThat(completed).isTrue();
    }

    @Test
    void handlingTheSameProductTwiceChangesNothing() {
        UUID tenantId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        stockService.createStockItemForProduct(tenantId, productId);
        stockService.createStockItemForProduct(tenantId, productId);

        Integer rows = jdbc.queryForObject(
                "SELECT count(*) FROM inventory.stock_items WHERE product_id = ?", Integer.class, productId);
        assertThat(rows).isEqualTo(1);
    }

    /** Checks every 100 ms until the stock row is there, or gives up after 10 seconds. */
    private boolean waitUntilStockItemExists(String productId) throws InterruptedException {
        long giveUpAt = System.currentTimeMillis() + WAIT_AT_MOST_MILLIS;
        while (System.currentTimeMillis() < giveUpAt) {
            Integer rows = jdbc.queryForObject(
                    "SELECT count(*) FROM inventory.stock_items WHERE product_id = ?::uuid", Integer.class, productId);
            if (rows != null && rows == 1) {
                return true;
            }
            Thread.sleep(100);
        }
        return false;
    }

    private boolean waitUntilEventCompleted(String productId) throws InterruptedException {
        long giveUpAt = System.currentTimeMillis() + WAIT_AT_MOST_MILLIS;
        while (System.currentTimeMillis() < giveUpAt) {
            Integer done = jdbc.queryForObject("""
                    SELECT count(*) FROM event_publication
                    WHERE serialized_event LIKE ? AND completion_date IS NOT NULL
                    """, Integer.class, "%" + productId + "%");
            if (done != null && done == 1) {
                return true;
            }
            Thread.sleep(100);
        }
        return false;
    }

    /** Returns {tenantId, accessToken}. */
    private String[] signUp() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        MvcTestResult result = mvc.post().uri("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"shopName": "Shop", "shopSlug": "inv-%s", "ownerName": "Owner",
                         "email": "inv+%s@example.com", "password": "a-long-password"}
                        """.formatted(suffix, suffix))
                .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        String json = result.getResponse().getContentAsString();
        String tenantId = JsonPath.read(json, "$.tenantId");
        String token = JsonPath.read(json, "$.accessToken");
        return new String[] {tenantId, token};
    }

    private String createProduct(String token, String name) throws Exception {
        MvcTestResult result = mvc.post().uri("/api/admin/products")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "%s", "priceMinor": 5000}
                        """.formatted(name))
                .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

}
