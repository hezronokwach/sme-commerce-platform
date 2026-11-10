package com.hezron.smecommerce.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.hezron.smecommerce.TestcontainersConfiguration;
import com.hezron.smecommerce.notification.internal.application.InvalidStreamTicketException;
import com.hezron.smecommerce.notification.internal.application.StreamTicketService;
import com.hezron.smecommerce.notification.internal.application.StreamTicketService.WatchedOrder;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
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
 * Who may get a ticket to watch an order live, and that a ticket works once (Guide 6.3 and 8.8,
 * "stream ticket single use"). Shop A has one order; shop B is a stranger to it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class StreamTicketTests {

    private static final long WAIT_AT_MOST_MILLIS = 10_000;

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    StreamTicketService tickets;

    private Shop shopA;
    private Shop shopB;
    private PlacedOrder order;

    @BeforeEach
    void shopAHasAnOrder() throws Exception {
        shopA = signUp("a");
        shopB = signUp("b");
        String sukuma = createStockedProduct(shopA, "Sukuma bundle", 5000, 5);
        // Digit 3: the fake payment never answers, so the order just waits.
        order = placeOrder(shopA, sukuma, "0712345673");
    }

    @Test
    void theCustomerGetsATicketWithTheirOrderLink() throws Exception {
        MvcTestResult result = customerTicket(shopA.slug(), order.orderNumber(), order.accessToken());

        assertThat(result).hasStatus(HttpStatus.OK);
        String ticket = JsonPath.read(result.getResponse().getContentAsString(), "$.ticket");
        assertThat(result).bodyJson().extractingPath("$.streamUrl")
                .isEqualTo("/api/store/order-stream?ticket=" + ticket);
        assertThat(result).bodyJson().extractingPath("$.expiresInSeconds").isEqualTo(60);
        // Only the hash is stored, never the ticket itself.
        Integer plainCopies = jdbc.queryForObject(
                "SELECT count(*) FROM notification.stream_tickets WHERE ticket_hash = ?", Integer.class, ticket);
        assertThat(plainCopies).isZero();
    }

    @Test
    void aWrongOrderLinkGetsNoTicket() {
        MvcTestResult result = customerTicket(shopA.slug(), order.orderNumber(), "not-the-token");

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("ORDER_NOT_FOUND");
    }

    @Test
    void theRightLinkAtTheWrongShopGetsNoTicket() {
        MvcTestResult result = customerTicket(shopB.slug(), order.orderNumber(), order.accessToken());

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void theShopsStaffGetATicketButAnotherShopsStaffDoNot() {
        assertThat(staffTicket(shopA, order.orderId())).hasStatus(HttpStatus.OK);

        // 404, not 403: shop B doesn't even learn that the order exists.
        assertThat(staffTicket(shopB, order.orderId())).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void staffNeedToBeLoggedIn() {
        MvcTestResult result = mvc.post().uri("/api/admin/orders/" + order.orderId() + "/stream-ticket").exchange();

        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void aTicketWorksOnce() throws Exception {
        String ticket = ticketFor(order);

        WatchedOrder watched = tickets.redeem(ticket);
        assertThat(watched.orderId().toString()).isEqualTo(order.orderId());
        assertThat(watched.tenantId().value().toString()).isEqualTo(shopA.tenantId());

        assertRefused(ticket);
    }

    @Test
    void anExpiredTicketIsRefused() throws Exception {
        String ticket = ticketFor(order);
        jdbc.update("UPDATE notification.stream_tickets SET expires_at = now() - interval '1 second' "
                + "WHERE order_id = ?::uuid", order.orderId());

        assertRefused(ticket);
    }

    @Test
    void aMadeUpTicketIsRefused() {
        assertRefused("made-up-ticket");
        assertRefused("");
    }

    @Test
    void twoTabsUsingTheSameTicketAtOnceOnlyOneGetsIn() throws Exception {
        String ticket = ticketFor(order);
        AtomicInteger admitted = new AtomicInteger();
        AtomicInteger refused = new AtomicInteger();
        CountDownLatch go = new CountDownLatch(1);

        Thread[] tabs = new Thread[8];
        for (int i = 0; i < tabs.length; i++) {
            tabs[i] = new Thread(new Redeem(ticket, go, admitted, refused));
            tabs[i].start();
        }
        go.countDown(); // all at once
        for (Thread tab : tabs) {
            tab.join();
        }

        assertThat(admitted.get()).isEqualTo(1);
        assertThat(refused.get()).isEqualTo(tabs.length - 1);
    }

    // --- helpers ---------------------------------------------------------------------------------

    /** One "tab": waits for the start signal, then tries the ticket. */
    private class Redeem implements Runnable {

        private final String ticket;
        private final CountDownLatch go;
        private final AtomicInteger admitted;
        private final AtomicInteger refused;

        Redeem(String ticket, CountDownLatch go, AtomicInteger admitted, AtomicInteger refused) {
            this.ticket = ticket;
            this.go = go;
            this.admitted = admitted;
            this.refused = refused;
        }

        @Override
        public void run() {
            try {
                go.await();
                tickets.redeem(ticket);
                admitted.incrementAndGet();
            } catch (InvalidStreamTicketException e) {
                refused.incrementAndGet();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void assertRefused(String ticket) {
        try {
            tickets.redeem(ticket);
            fail("The ticket was accepted");
        } catch (InvalidStreamTicketException expected) {
            // refused, as it should be
        }
    }

    private String ticketFor(PlacedOrder placed) throws Exception {
        MvcTestResult result = customerTicket(shopA.slug(), placed.orderNumber(), placed.accessToken());
        assertThat(result).hasStatus(HttpStatus.OK);
        return JsonPath.read(result.getResponse().getContentAsString(), "$.ticket");
    }

    private MvcTestResult customerTicket(String slug, String orderNumber, String orderToken) {
        return mvc.post().uri("/api/store/" + slug + "/orders/" + orderNumber + "/stream-ticket")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"orderToken": "%s"}
                        """.formatted(orderToken))
                .exchange();
    }

    private MvcTestResult staffTicket(Shop shop, String orderId) {
        return mvc.post().uri("/api/admin/orders/" + orderId + "/stream-ticket")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + shop.token())
                .exchange();
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
        String json = result.getResponse().getContentAsString();
        return new PlacedOrder(JsonPath.read(json, "$.orderId"), JsonPath.read(json, "$.orderNumber"),
                JsonPath.read(json, "$.accessToken"));
    }

    private Shop signUp(String label) throws Exception {
        String suffix = label + UUID.randomUUID().toString().substring(0, 6);
        String slug = "watch-" + suffix;
        MvcTestResult result = mvc.post().uri("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"shopName": "Shop %s", "shopSlug": "%s", "ownerName": "Owner",
                         "email": "watch+%s@example.com", "password": "a-long-password"}
                        """.formatted(suffix, slug, suffix))
                .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        String json = result.getResponse().getContentAsString();
        return new Shop(slug, JsonPath.read(json, "$.accessToken"), JsonPath.read(json, "$.tenantId"));
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

    private record Shop(String slug, String token, String tenantId) {
    }

    private record PlacedOrder(String orderId, String orderNumber, String accessToken) {
    }

}
