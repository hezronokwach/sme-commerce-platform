package com.hezron.smecommerce.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.hezron.smecommerce.TestcontainersConfiguration;
import com.hezron.smecommerce.inventory.internal.application.StockService;
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
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Restocking from the admin (Guide 3.3). Each test signs up its own shop and gives it one product's
 * stock record directly through StockService, so it doesn't have to wait for the product event.
 */
@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class StockEndpointsTests {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    StockService stockService;

    @Autowired
    ApplicationEvents events;

    private String token;
    private String userId;
    private UUID productId;

    @BeforeEach
    void shopWithOneEmptyStockItem() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        MvcTestResult result = mvc.post().uri("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"shopName": "Shop", "shopSlug": "stock-%s", "ownerName": "Owner",
                         "email": "stock+%s@example.com", "password": "a-long-password"}
                        """.formatted(suffix, suffix))
                .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        String json = result.getResponse().getContentAsString();
        token = JsonPath.read(json, "$.accessToken");
        userId = JsonPath.read(json, "$.userId");
        UUID tenantId = UUID.fromString(JsonPath.read(json, "$.tenantId"));

        productId = UUID.randomUUID();
        stockService.createStockItemForProduct(tenantId, productId);
    }

    @Test
    void newStockStartsEmpty() {
        MvcTestResult result = get("/api/admin/stock/" + productId);

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.onHand").isEqualTo(0);
        assertThat(result).bodyJson().extractingPath("$.available").isEqualTo(0);
        assertThat(result).bodyJson().extractingPath("$.lowStockThreshold").isEqualTo(5);
    }

    @Test
    void restockAddsToOnHandAndWritesTheHistoryWithWhoDidIt() {
        MvcTestResult result = adjust(40, "RESTOCK", "Delivery from Limuru");

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.onHand").isEqualTo(40);
        assertThat(result).bodyJson().extractingPath("$.available").isEqualTo(40);

        String history = jdbc.queryForObject("""
                SELECT change || ' ' || reason || ' ' || reference || ' ' || actor_id
                FROM inventory.stock_movements WHERE product_id = ?
                """, String.class, productId);
        assertThat(history).isEqualTo("40 RESTOCK Delivery from Limuru " + userId);
    }

    @Test
    void onHandAlwaysEqualsTheSumOfTheHistory() {
        adjust(40, "RESTOCK", null);
        adjust(-3, "ADJUSTMENT", "Counted the shelf");
        adjust(2, "RETURN", null);

        Integer onHand = jdbc.queryForObject(
                "SELECT on_hand FROM inventory.stock_items WHERE product_id = ?", Integer.class, productId);
        Integer sumOfHistory = jdbc.queryForObject(
                "SELECT sum(change) FROM inventory.stock_movements WHERE product_id = ?", Integer.class, productId);
        assertThat(onHand).isEqualTo(39);
        assertThat(sumOfHistory).isEqualTo(onHand);
    }

    @Test
    void cannotRemoveWhatUnpaidOrdersAreHolding() {
        adjust(10, "RESTOCK", null);
        // Reservations come in the next step; set 8 held directly.
        jdbc.update("UPDATE inventory.stock_items SET reserved = 8 WHERE product_id = ?", productId);

        MvcTestResult result = adjust(-3, "ADJUSTMENT", null);

        assertThat(result).hasStatus(HttpStatus.CONFLICT);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("WOULD_BREAK_RESERVATIONS");
        // Nothing changed: still 10 on hand, and no history row for the refused change.
        assertThat(get("/api/admin/stock/" + productId)).bodyJson().extractingPath("$.onHand").isEqualTo(10);
        Integer rows = jdbc.queryForObject(
                "SELECT count(*) FROM inventory.stock_movements WHERE product_id = ?", Integer.class, productId);
        assertThat(rows).isEqualTo(1);
    }

    @Test
    void cannotGoBelowZero() {
        MvcTestResult result = adjust(-1, "ADJUSTMENT", null);

        assertThat(result).hasStatus(HttpStatus.CONFLICT);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("WOULD_BREAK_RESERVATIONS");
    }

    @Test
    void salesCannotBeRecordedByHand() {
        MvcTestResult result = adjust(-1, "SALE", null);

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("STOCK_REASON_INVALID");
    }

    @Test
    void aChangeOfZeroIsRefused() {
        MvcTestResult result = adjust(0, "ADJUSTMENT", null);

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("STOCK_CHANGE_ZERO");
    }

    @Test
    void droppingToTheWarningLevelAnnouncesStockLow() {
        adjust(20, "RESTOCK", null);
        assertThat(events.stream(StockLowEvent.class).count()).isZero();

        adjust(-15, "ADJUSTMENT", null);

        // 5 left, and the warning level is 5.
        assertThat(events.stream(StockLowEvent.class).count()).isEqualTo(1);
    }

    @Test
    void unknownProductIsNotFound() {
        MvcTestResult result = get("/api/admin/stock/" + UUID.randomUUID());

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("STOCK_ITEM_NOT_FOUND");
    }

    private MvcTestResult adjust(int change, String reason, String note) {
        String noteJson = "null";
        if (note != null) {
            noteJson = "\"" + note + "\"";
        }
        String body = """
                {"change": %d, "reason": "%s", "note": %s}
                """.formatted(change, reason, noteJson);
        return mvc.post().uri("/api/admin/stock/" + productId + "/adjustments")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body)
                .exchange();
    }

    private MvcTestResult get(String uri) {
        return mvc.get().uri(uri).header(HttpHeaders.AUTHORIZATION, "Bearer " + token).exchange();
    }

}
