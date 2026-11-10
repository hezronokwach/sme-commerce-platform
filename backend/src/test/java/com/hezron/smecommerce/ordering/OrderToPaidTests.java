package com.hezron.smecommerce.ordering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.hezron.smecommerce.TestcontainersConfiguration;
import com.hezron.smecommerce.payments.PaymentInProgressException;
import com.hezron.smecommerce.payments.PaymentProviderName;
import com.hezron.smecommerce.payments.PaymentSucceededEvent;
import com.hezron.smecommerce.payments.PaymentsApi;
import com.hezron.smecommerce.payments.RequestPaymentCommand;
import com.hezron.smecommerce.payments.internal.application.PaymentCallbackService;
import com.hezron.smecommerce.payments.internal.domain.CallbackOutcome;
import com.hezron.smecommerce.shared.TenantId;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
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
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Issue "Payments with a fake provider first", Done-when: the whole order-to-paid flow works end to
 * end with zero real money. Each test places a real order over HTTP with paymentMethod FAKE; the
 * phone's last digit picks how the fake payment ends (FakeOutcome). The fake calls back after 500 ms
 * in tests (application-test.properties), so each test waits for the outcome instead of sleeping.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class OrderToPaidTests {

    private static final long WAIT_AT_MOST_MILLIS = 15_000;
    private static final int STOCK = 10;

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PaymentsApi paymentsApi;

    @Autowired
    PaymentCallbackService callbacks;

    @Autowired
    ApplicationEventPublisher events;

    @Autowired
    TransactionTemplate transactions;

    private Shop shop;
    private String sukuma;

    @BeforeEach
    void aShopWithTenSukumaBundles() throws Exception {
        shop = signUp("pay");
        sukuma = createStockedProduct(shop, "Sukuma bundle", 5000, STOCK);
    }

    @Test
    void aSuccessfulPaymentMarksTheOrderPaidAndTheStockSold() throws Exception {
        String orderId = placeOrder(shop, sukuma, 2, "0712345670", "FAKE");

        waitForOrderStatus(orderId, "PAID");

        assertThat(paymentStatus(orderId)).isEqualTo("SUCCEEDED");
        // Confirmed: the 2 left the shelf for good, and nothing is held any more.
        waitForStock(sukuma, STOCK - 2, 0);
        assertThat(historyRowsTo(orderId, "PAID")).isEqualTo(1);
    }

    @Test
    void aCancelledPromptKeepsTheOrderWaitingAndTheStockHeld() throws Exception {
        String orderId = placeOrder(shop, sukuma, 2, "0712345671", "FAKE");

        waitForPaymentStatus(orderId, "FAILED");

        // Retryable: the customer may try again before the time limit.
        assertThat(orderStatus(orderId)).isEqualTo("PENDING_PAYMENT");
        assertThat(stock(sukuma)).isEqualTo(new Stock(STOCK, 2));
        assertThat(failureReason(orderId)).isEqualTo("You cancelled the payment on your phone.");
    }

    @Test
    void aHardDeclineCancelsTheOrderAndGivesTheStockBack() throws Exception {
        String orderId = placeOrder(shop, sukuma, 2, "0712345674", "FAKE");

        waitForOrderStatus(orderId, "CANCELLED");

        assertThat(paymentStatus(orderId)).isEqualTo("FAILED");
        waitForStock(sukuma, STOCK, 0);
    }

    @Test
    void aProviderThatNeverAnswersLeavesThePaymentPending() throws Exception {
        String orderId = placeOrder(shop, sukuma, 1, "0712345673", "FAKE");

        waitForPaymentStatus(orderId, "PENDING");
        Thread.sleep(1_000); // longer than the fake's delay: no callback is coming

        assertThat(paymentStatus(orderId)).isEqualTo("PENDING");
        assertThat(orderStatus(orderId)).isEqualTo("PENDING_PAYMENT");
    }

    @Test
    void theSameCallbackTwiceIsAppliedOnce() throws Exception {
        String orderId = placeOrder(shop, sukuma, 1, "0712345670", "FAKE");
        waitForOrderStatus(orderId, "PAID");

        // Replay exactly what the provider sent.
        String reference = providerReference(orderId);
        String rawBody = jdbc.queryForObject(
                "SELECT raw_body::text FROM payments.callback_log WHERE provider_reference = ?", String.class, reference);
        CallbackOutcome second = callbacks.receive(PaymentProviderName.FAKE, rawBody);

        assertThat(second).isEqualTo(CallbackOutcome.DUPLICATE);
        Thread.sleep(500); // give any (wrong) second listener run the chance to happen
        assertThat(historyRowsTo(orderId, "PAID")).isEqualTo(1);
        waitForStock(sukuma, STOCK - 1, 0);
    }

    @Test
    void aSuccessForTheWrongAmountNeverMarksTheOrderPaid() throws Exception {
        // Digit 3: the fake never calls back, so this test sends the callbacks itself.
        String orderId = placeOrder(shop, sukuma, 1, "0712345673", "FAKE");
        waitForPaymentStatus(orderId, "PENDING");
        String reference = providerReference(orderId);

        CallbackOutcome tooLittle = callbacks.receive(PaymentProviderName.FAKE, successBody(reference, 100));

        assertThat(tooLittle).isEqualTo(CallbackOutcome.AMOUNT_MISMATCH);
        assertThat(paymentStatus(orderId)).isEqualTo("PENDING");

        // The right amount does count.
        CallbackOutcome right = callbacks.receive(PaymentProviderName.FAKE, successBody(reference, 5000));
        assertThat(right).isEqualTo(CallbackOutcome.APPLIED);
        waitForOrderStatus(orderId, "PAID");
    }

    @Test
    void aCallbackForAReferenceNobodyKnowsChangesNothing() {
        CallbackOutcome outcome = callbacks.receive(PaymentProviderName.FAKE, successBody("FAKE-0-nobody", 5000));

        assertThat(outcome).isEqualTo(CallbackOutcome.UNKNOWN_REFERENCE);
    }

    @Test
    void anOrderNeverHasTwoLivePayments() throws Exception {
        String orderId = placeOrder(shop, sukuma, 1, "0712345673", "FAKE");
        waitForPaymentStatus(orderId, "PENDING");

        RequestPaymentCommand again = new RequestPaymentCommand(TenantId.of(UUID.fromString(shop.tenantId())),
                UUID.fromString(orderId), "X-1", 5000, "KES", PaymentProviderName.FAKE, "254712345673");

        try {
            paymentsApi.requestPayment(again);
            fail("A second live payment was started");
        } catch (PaymentInProgressException expected) {
            // refused, as it should be
        }
    }

    @Test
    void anMpesaOrderWaitsWithAClearReasonUntilMpesaIsConnected() throws Exception {
        String orderId = placeOrder(shop, sukuma, 1, "0712345670", "MPESA");

        waitForPaymentStatus(orderId, "FAILED");

        assertThat(failureReason(orderId)).contains("not connected yet");
        assertThat(orderStatus(orderId)).isEqualTo("PENDING_PAYMENT");
    }

    @Test
    void aPaymentEventNamingAnotherShopLeavesTheOrderAlone() throws Exception {
        String orderId = placeOrder(shop, sukuma, 1, "0712345673", "FAKE");
        Shop otherShop = signUp("other");

        // Ordering looks the order up by id AND shop, so the wrong shop finds nothing.
        publishInTransaction(new PaymentSucceededEvent(TenantId.of(UUID.fromString(otherShop.tenantId())),
                UUID.fromString(orderId), UUID.randomUUID(), "FK-WRONG", 5000));
        Thread.sleep(1_000);

        assertThat(orderStatus(orderId)).isEqualTo("PENDING_PAYMENT");
    }

    @Test
    void everyStepOfThePaymentFlowIsRecordedInTheEventRegistry() throws Exception {
        String orderId = placeOrder(shop, sukuma, 1, "0712345670", "FAKE");
        waitForOrderStatus(orderId, "PAID");
        String paymentId = newestPaymentColumn(orderId, "id");

        // "Ask the provider" uses @TransactionalEventListener, not @ApplicationModuleListener (see
        // PaymentRequestedListener); it is recorded all the same, so a crash can't lose it either.
        waitForRegistryRow("PaymentRequestedEvent", paymentId, "COMPLETED");
        waitForRegistryRow("PaymentSucceededEvent", paymentId, "COMPLETED");
    }

    @Test
    void aPaymentSucceededEventDeliveredTwiceIsHandledOnce() throws Exception {
        String orderId = placeOrder(shop, sukuma, 2, "0712345670", "FAKE");
        waitForOrderStatus(orderId, "PAID");
        waitForStock(sukuma, STOCK - 2, 0);

        // At-least-once delivery: a retry or restart can deliver the same event again.
        String paymentId = newestPaymentColumn(orderId, "id");
        publishInTransaction(new PaymentSucceededEvent(TenantId.of(UUID.fromString(shop.tenantId())),
                UUID.fromString(orderId), UUID.fromString(paymentId), "FK-AGAIN", 10000));
        Thread.sleep(1_000);

        // Ordering checks the order first: already PAID, so nothing happens a second time.
        assertThat(historyRowsTo(orderId, "PAID")).isEqualTo(1);
        assertThat(stock(sukuma)).isEqualTo(new Stock(STOCK - 2, 0));
    }

    // --- helpers ---------------------------------------------------------------------------------

    /** Waits until the registry row of one event (found by an id inside its JSON) has this status. */
    private void waitForRegistryRow(String eventType, String idInEvent, String expected) throws InterruptedException {
        long giveUpAt = System.currentTimeMillis() + WAIT_AT_MOST_MILLIS;
        List<String> statuses = List.of();
        while (System.currentTimeMillis() < giveUpAt) {
            statuses = jdbc.queryForList(
                    "SELECT status FROM event_publication WHERE event_type LIKE ? AND serialized_event LIKE ?",
                    String.class, "%" + eventType, "%" + idInEvent + "%");
            if (statuses.size() == 1 && expected.equals(statuses.get(0))) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError(eventType + " for " + idInEvent + " never became " + expected + "; rows: " + statuses);
    }

    /** A JSON callback in the fake provider's format, saying "success". */
    private static String successBody(String reference, long amountMinor) {
        return """
                {"reference": "%s", "resultCode": "0", "receiptNumber": "FK-TEST", "amountMinor": %d,
                 "resultDescription": "ok"}
                """.formatted(reference, amountMinor);
    }

    /** Module listeners only hear events after a transaction commits, so publish inside one. */
    private void publishInTransaction(Object event) {
        transactions.execute(new TransactionCallbackWithoutResult() {
            @Override
            protected void doInTransactionWithoutResult(TransactionStatus status) {
                events.publishEvent(event);
            }
        });
    }

    private String orderStatus(String orderId) {
        return jdbc.queryForObject("SELECT status FROM ordering.orders WHERE id = ?::uuid", String.class, orderId);
    }

    /** The newest attempt's status, or null if payments hasn't saved one. */
    private String paymentStatus(String orderId) {
        return newestPaymentColumn(orderId, "status");
    }

    private String failureReason(String orderId) {
        return newestPaymentColumn(orderId, "failure_reason");
    }

    private String providerReference(String orderId) {
        return newestPaymentColumn(orderId, "provider_reference");
    }

    private String newestPaymentColumn(String orderId, String column) {
        List<String> values =jdbc.queryForList("SELECT " + column
                + " FROM payments.payments WHERE order_id = ?::uuid ORDER BY created_at DESC LIMIT 1", String.class, orderId);
        if (values.isEmpty()) {
            return null;
        }
        return values.get(0);
    }

    private int historyRowsTo(String orderId, String status) {
        Integer rows = jdbc.queryForObject(
                "SELECT count(*) FROM ordering.order_status_history WHERE order_id = ?::uuid AND to_status = ?",
                Integer.class, orderId, status);
        return rows;
    }

    private Stock stock(String productId) {
        Integer onHand = jdbc.queryForObject(
                "SELECT on_hand FROM inventory.stock_items WHERE product_id = ?::uuid", Integer.class, productId);
        Integer reserved = jdbc.queryForObject(
                "SELECT reserved FROM inventory.stock_items WHERE product_id = ?::uuid", Integer.class, productId);
        return new Stock(onHand, reserved);
    }

    private void waitForOrderStatus(String orderId, String expected) throws InterruptedException {
        long giveUpAt = System.currentTimeMillis() + WAIT_AT_MOST_MILLIS;
        while (System.currentTimeMillis() < giveUpAt) {
            if (expected.equals(orderStatus(orderId))) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Order never became " + expected + "; it is " + orderStatus(orderId)
                + ", payment " + paymentStatus(orderId));
    }

    private void waitForPaymentStatus(String orderId, String expected) throws InterruptedException {
        long giveUpAt = System.currentTimeMillis() + WAIT_AT_MOST_MILLIS;
        while (System.currentTimeMillis() < giveUpAt) {
            if (expected.equals(paymentStatus(orderId))) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Payment never became " + expected + "; it is " + paymentStatus(orderId));
    }

    /** Confirming and releasing stock happen in ordering's listener, a moment after the order changes. */
    private void waitForStock(String productId, int onHand, int reserved) throws InterruptedException {
        Stock expected = new Stock(onHand, reserved);
        long giveUpAt = System.currentTimeMillis() + WAIT_AT_MOST_MILLIS;
        while (System.currentTimeMillis() < giveUpAt) {
            if (expected.equals(stock(productId))) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Stock never became " + expected + "; it is " + stock(productId));
    }

    private String placeOrder(Shop shop, String productId, int quantity, String phone, String method)
            throws Exception {
        MvcTestResult result = mvc.post().uri("/api/store/" + shop.slug() + "/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"customerName": "Amina", "customerPhone": "%s", "paymentMethod": "%s",
                         "lines": [{"productId": "%s", "quantity": %d}]}
                        """.formatted(phone, method, productId, quantity))
                .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return JsonPath.read(result.getResponse().getContentAsString(), "$.orderId");
    }

    private Shop signUp(String label) throws Exception {
        String suffix = label + UUID.randomUUID().toString().substring(0, 6);
        String slug = "paid-" + suffix;
        MvcTestResult result = mvc.post().uri("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"shopName": "Shop %s", "shopSlug": "%s", "ownerName": "Owner",
                         "email": "paid+%s@example.com", "password": "a-long-password"}
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

    private record Stock(int onHand, int reserved) {
    }

}
