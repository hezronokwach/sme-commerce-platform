package com.hezron.smecommerce.ordering;

import static org.assertj.core.api.Assertions.assertThat;

import com.hezron.smecommerce.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
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
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * The admin's order list and order page (Guide 4.5). Two shops are signed up; shop A gets three orders
 * from the storefront, shop B none.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class AdminOrdersTests {

    private static final long WAIT_AT_MOST_MILLIS = 10_000;

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbc;

    private Shop shopA;
    private Shop shopB;
    private String firstOrderOfA;

    @BeforeEach
    void shopAHasThreeOrders() throws Exception {
        shopA = signUp("a");
        shopB = signUp("b");
        String sukuma = createStockedProduct(shopA, "Sukuma bundle", 5000, 20);

        firstOrderOfA = placeOrder(shopA, sukuma, 1, "Amina");
        placeOrder(shopA, sukuma, 2, "Baraka");
        placeOrder(shopA, sukuma, 3, "Chebet");
    }

    @Test
    void listShowsTheShopsOrdersNewestFirst() throws Exception {
        MvcTestResult result = get(shopA, "/api/admin/orders");

        assertThat(result).hasStatus(HttpStatus.OK);
        String json = result.getResponse().getContentAsString();
        List<String> customers = JsonPath.read(json, "$.items[*].customerName");
        assertThat(customers).containsExactly("Chebet", "Baraka", "Amina");
        assertThat(result).bodyJson().extractingPath("$.totalItems").isEqualTo(3);
        assertThat(result).bodyJson().extractingPath("$.items[0].totalMinor").isEqualTo(15000);
        assertThat(result).bodyJson().extractingPath("$.items[0].status").isEqualTo("PENDING_PAYMENT");
    }

    @Test
    void listCanBeFilteredByStatus() throws Exception {
        List<String> waiting = JsonPath.read(get(shopA, "/api/admin/orders?status=PENDING_PAYMENT")
                .getResponse().getContentAsString(), "$.items[*].id");
        List<String> paid = JsonPath.read(get(shopA, "/api/admin/orders?status=PAID")
                .getResponse().getContentAsString(), "$.items[*].id");

        assertThat(waiting).hasSize(3);
        assertThat(paid).isEmpty();
    }

    @Test
    void listComesInPages() throws Exception {
        MvcTestResult firstPage = get(shopA, "/api/admin/orders?page=0&size=2");
        MvcTestResult secondPage = get(shopA, "/api/admin/orders?page=1&size=2");

        List<String> first = JsonPath.read(firstPage.getResponse().getContentAsString(), "$.items[*].customerName");
        List<String> second = JsonPath.read(secondPage.getResponse().getContentAsString(), "$.items[*].customerName");
        assertThat(first).containsExactly("Chebet", "Baraka");
        assertThat(second).containsExactly("Amina");
        assertThat(firstPage).bodyJson().extractingPath("$.totalItems").isEqualTo(3);
    }

    @Test
    void aPageNeverHasMoreThanFifty() {
        MvcTestResult result = get(shopA, "/api/admin/orders?size=500");

        assertThat(result).bodyJson().extractingPath("$.size").isEqualTo(50);
    }

    @Test
    void orderPageShowsTheCustomerTheLinesAndTheHistory() throws Exception {
        MvcTestResult result = get(shopA, "/api/admin/orders/" + firstOrderOfA);

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.customerName").isEqualTo("Amina");
        assertThat(result).bodyJson().extractingPath("$.customerPhone").isEqualTo("254712345678");
        assertThat(result).bodyJson().extractingPath("$.paymentMethod").isEqualTo("MPESA");
        assertThat(result).bodyJson().extractingPath("$.lines[0].productName").isEqualTo("Sukuma bundle");
        assertThat(result).bodyJson().extractingPath("$.lines[0].lineTotalMinor").isEqualTo(5000);

        String json = result.getResponse().getContentAsString();
        List<String> toStatuses = JsonPath.read(json, "$.history[*].toStatus");
        assertThat(toStatuses).containsExactly("CREATED", "PENDING_PAYMENT");
        assertThat(result).bodyJson().extractingPath("$.history[1].fromStatus").isEqualTo("CREATED");
    }

    @Test
    void shopBSeesNoneOfAsOrders() throws Exception {
        MvcTestResult list = get(shopB, "/api/admin/orders");
        List<String> ids = JsonPath.read(list.getResponse().getContentAsString(), "$.items[*].id");
        assertThat(ids).isEmpty();

        MvcTestResult detail = get(shopB, "/api/admin/orders/" + firstOrderOfA);
        // 404, not 403: shop B doesn't even learn that the order exists.
        assertThat(detail).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(detail).bodyJson().extractingPath("$.code").isEqualTo("ORDER_NOT_FOUND");
    }

    @Test
    void noTokenNoOrders() {
        assertThat(mvc.get().uri("/api/admin/orders").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    private MvcTestResult get(Shop shop, String uri) {
        return mvc.get().uri(uri).header(HttpHeaders.AUTHORIZATION, "Bearer " + shop.token()).exchange();
    }

    private String placeOrder(Shop shop, String productId, int quantity, String customerName) throws Exception {
        MvcTestResult result = mvc.post().uri("/api/store/" + shop.slug() + "/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"customerName": "%s", "customerPhone": "0712345678", "paymentMethod": "MPESA",
                         "lines": [{"productId": "%s", "quantity": %d}]}
                        """.formatted(customerName, productId, quantity))
                .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return JsonPath.read(result.getResponse().getContentAsString(), "$.orderId");
    }

    private Shop signUp(String label) throws Exception {
        String suffix = label + UUID.randomUUID().toString().substring(0, 6);
        String slug = "orders-" + suffix;
        MvcTestResult result = mvc.post().uri("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"shopName": "Shop %s", "shopSlug": "%s", "ownerName": "Owner",
                         "email": "orders+%s@example.com", "password": "a-long-password"}
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

        waitForStockRow(productId);
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

    private record Shop(String slug, String token) {
    }

}
