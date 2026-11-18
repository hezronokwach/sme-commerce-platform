package com.hezron.smecommerce.payments;

import static org.assertj.core.api.Assertions.assertThat;

import com.hezron.smecommerce.TestcontainersConfiguration;
import com.hezron.smecommerce.payments.internal.card.StripeSignature;
import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
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
 * Issue "Second payment provider (card)": card payments through Stripe, tested against a pretend Stripe
 * and webhooks signed here with a test-only secret. No real Stripe account is needed.
 * <p>
 * The shop has CARD turned on and FAKE off: the same ordering code, a different provider.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class CardPaymentTests {

    private static final long WAIT_AT_MOST_MILLIS = 15_000;
    private static final String WEBHOOK_SECRET = "whsec_test_only_not_a_real_secret";

    private static final PretendStripe STRIPE = startStripe();

    @DynamicPropertySource
    static void pointAtPretendStripe(DynamicPropertyRegistry registry) {
        registry.add("app.payments.card.base-url", STRIPE::url);
        registry.add("app.payments.card.secret-key", () -> "sk_test_pretend");
        registry.add("app.payments.card.publishable-key", () -> "pk_test_pretend");
        registry.add("app.payments.card.webhook-secret", () -> WEBHOOK_SECRET);
    }

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbc;

    private Shop shop;
    private String sukuma;

    @BeforeEach
    void aShopThatTakesCardsOnly() throws Exception {
        shop = signUp();
        sukuma = createStockedProduct(shop, "Sukuma bundle", 5000, 10);
        switchMethod(shop, "CARD", true);
        switchMethod(shop, "FAKE", false);
    }

    @Test
    void aCardOrderCreatesAPaymentIntentForTheRightAmountOnce() throws Exception {
        PlacedOrder order = placeOrder(2);

        waitForPendingReference(order.orderId());
        String paymentId = latestPaymentId(order.orderId());
        PretendStripe.SentRequest sent = creationFor(paymentId);
        assertThat(sent.form()).containsEntry("amount", "10000").containsEntry("currency", "kes")
                .containsEntry("metadata[payment_id]", paymentId)
                .containsEntry("metadata[order_number]", order.orderNumber());
        assertThat(sent.authorization()).isEqualTo("Bearer sk_test_pretend");
        // The same payment can never create a second intent at Stripe.
        assertThat(sent.idempotencyKey()).isEqualTo(paymentId);
    }

    @Test
    void theOrderPageGetsWhatTheBrowserNeedsForTheCardForm() throws Exception {
        PlacedOrder order = placeOrder(1);
        String intentId = waitForPendingReference(order.orderId());

        MvcTestResult view = viewOrder(order);

        assertThat(view).hasStatus(HttpStatus.OK);
        assertThat(view).bodyJson().extractingPath("$.paymentNextAction.kind").isEqualTo("CARD");
        assertThat(view).bodyJson().extractingPath("$.paymentNextAction.clientSecret")
                .isEqualTo(intentId + "_secret_pretend");
        assertThat(view).bodyJson().extractingPath("$.paymentNextAction.publishableKey").isEqualTo("pk_test_pretend");
        // The secret key never leaves the server.
        assertThat(view.getResponse().getContentAsString()).doesNotContain("sk_test");
    }

    @Test
    void aSignedSucceededWebhookMarksTheOrderPaidAndASecondCopyChangesNothing() throws Exception {
        PlacedOrder order = placeOrder(1);
        String intentId = waitForPendingReference(order.orderId());
        String body = event("payment_intent.succeeded", intentId, 5000, null);

        MvcTestResult first = sendWebhook(body, signed(body, Instant.now()));
        assertThat(first).hasStatus(HttpStatus.OK);
        assertThat(first).bodyJson().extractingPath("$.outcome").isEqualTo("APPLIED");
        waitForOrderStatus(order.orderId(), "PAID");

        MvcTestResult second = sendWebhook(body, signed(body, Instant.now()));
        assertThat(second).bodyJson().extractingPath("$.outcome").isEqualTo("DUPLICATE");
        Integer paidRows = jdbc.queryForObject(
                "SELECT count(*) FROM ordering.order_status_history WHERE order_id = ?::uuid AND to_status = 'PAID'",
                Integer.class, order.orderId());
        assertThat(paidRows).isEqualTo(1);
        // Nothing left for the browser to do.
        assertThat(viewOrder(order)).bodyJson().extractingPath("$.paymentNextAction").isNull();
    }

    @Test
    void aWebhookThatIsNotSignedByStripeChangesNothing() throws Exception {
        PlacedOrder order = placeOrder(1);
        String intentId = waitForPendingReference(order.orderId());
        String body = event("payment_intent.succeeded", intentId, 5000, null);
        long now = Instant.now().getEpochSecond();

        // Signed with another secret.
        String wrongSecret = "t=" + now + ",v1=" + StripeSignature.sign(String.valueOf(now),
                body.getBytes(StandardCharsets.UTF_8), "whsec_someone_else");
        assertThat(sendWebhook(body, wrongSecret)).hasStatus(HttpStatus.BAD_REQUEST);
        // A real signature, but recorded 10 minutes ago and replayed.
        assertThat(sendWebhook(body, signed(body, Instant.now().minusSeconds(600)))).hasStatus(HttpStatus.BAD_REQUEST);
        // A real signature, but the body was changed on the way.
        String changed = event("payment_intent.succeeded", intentId, 1, null);
        assertThat(sendWebhook(changed, signed(body, Instant.now()))).hasStatus(HttpStatus.BAD_REQUEST);
        // No signature at all.
        assertThat(sendWebhook(body, null)).hasStatus(HttpStatus.BAD_REQUEST);

        assertThat(orderStatus(order.orderId())).isEqualTo("PENDING_PAYMENT");
        assertThat(latestPaymentStatus(order.orderId())).isEqualTo("PENDING");
    }

    @Test
    void aSuccessForAnotherAmountIsNeverMarkedPaid() throws Exception {
        PlacedOrder order = placeOrder(1);
        String intentId = waitForPendingReference(order.orderId());
        String body = event("payment_intent.succeeded", intentId, 100, null);

        MvcTestResult result = sendWebhook(body, signed(body, Instant.now()));

        assertThat(result).bodyJson().extractingPath("$.outcome").isEqualTo("AMOUNT_MISMATCH");
        assertThat(orderStatus(order.orderId())).isEqualTo("PENDING_PAYMENT");
    }

    @Test
    void aDeclinedCardCanBeTriedAgain() throws Exception {
        PlacedOrder order = placeOrder(1);
        String intentId = waitForPendingReference(order.orderId());
        String body = event("payment_intent.payment_failed", intentId, 0, "Your card was declined.");

        sendWebhook(body, signed(body, Instant.now()));

        waitForLatestPayment(order.orderId(), "FAILED");
        assertThat(orderStatus(order.orderId())).isEqualTo("PENDING_PAYMENT");
        assertThat(viewOrder(order)).bodyJson().extractingPath("$.paymentFailureReason")
                .isEqualTo("Your card was declined.");

        // Try again: a new attempt, so a new payment intent at Stripe.
        MvcTestResult again = mvc.post().uri("/api/store/" + shop.slug() + "/orders/" + order.orderNumber()
                + "/payment-attempts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"orderToken": "%s"}
                        """.formatted(order.accessToken()))
                .exchange();
        assertThat(again).hasStatus(HttpStatus.ACCEPTED);
        String secondIntent = waitForPendingReference(order.orderId());
        assertThat(secondIntent).isNotEqualTo(intentId);
    }

    @Test
    void whenStripeRefusesTheCustomerHearsWhyAndCanTryAgain() throws Exception {
        STRIPE.refuseNextWith("Amount must be at least 50 cents");
        PlacedOrder order = placeOrder(1);

        waitForLatestPayment(order.orderId(), "FAILED");

        assertThat(viewOrder(order)).bodyJson().extractingPath("$.paymentFailureReason")
                .isEqualTo("Amount must be at least 50 cents");
        assertThat(orderStatus(order.orderId())).isEqualTo("PENDING_PAYMENT");
    }

    @Test
    void anEventWeDoNotUseIsAnsweredButChangesNothing() throws Exception {
        PlacedOrder order = placeOrder(1);
        String intentId = waitForPendingReference(order.orderId());
        String body = event("payment_intent.created", intentId, 0, null);

        MvcTestResult result = sendWebhook(body, signed(body, Instant.now()));

        // 200, or Stripe would keep sending it again.
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(latestPaymentStatus(order.orderId())).isEqualTo("PENDING");
    }

    // --- webhooks -----------------------------------------------------------------------------------

    private static String event(String type, String intentId, long amountReceived, String errorMessage) {
        String error = "null";
        if (errorMessage != null) {
            error = "{\"code\": \"card_declined\", \"message\": \"" + errorMessage + "\"}";
        }
        return """
                {"id": "evt_%s", "type": "%s", "data": {"object": {"id": "%s", "object": "payment_intent",
                 "amount_received": %d, "latest_charge": "ch_test_1", "last_payment_error": %s}}}
                """.formatted(UUID.randomUUID(), type, intentId, amountReceived, error);
    }

    /** The Stripe-Signature header Stripe would send, made with our test-only webhook secret. */
    private static String signed(String body, Instant at) {
        String timestamp = String.valueOf(at.getEpochSecond());
        return "t=" + timestamp + ",v1="
                + StripeSignature.sign(timestamp, body.getBytes(StandardCharsets.UTF_8), WEBHOOK_SECRET);
    }

    private MvcTestResult sendWebhook(String body, String signature) {
        if (signature == null) {
            return mvc.post().uri("/api/webhooks/card").contentType(MediaType.APPLICATION_JSON).content(body).exchange();
        }
        return mvc.post().uri("/api/webhooks/card")
                .header("Stripe-Signature", signature)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    // --- reading the database -----------------------------------------------------------------------

    /** The request that created the intent for this payment (each test's payments are its own). */
    private PretendStripe.SentRequest creationFor(String paymentId) {
        for (PretendStripe.SentRequest request : STRIPE.requests()) {
            if (request.method().equals("POST") && paymentId.equals(request.form().get("metadata[payment_id]"))) {
                return request;
            }
        }
        throw new AssertionError("No intent was created for payment " + paymentId);
    }

    private String waitForPendingReference(String orderId) throws InterruptedException {
        long giveUpAt = System.currentTimeMillis() + WAIT_AT_MOST_MILLIS;
        while (System.currentTimeMillis() < giveUpAt) {
            java.util.List<String> references = jdbc.queryForList("""
                    SELECT provider_reference FROM payments.payments
                    WHERE order_id = ?::uuid AND status = 'PENDING' AND provider_reference IS NOT NULL
                    ORDER BY created_at DESC LIMIT 1
                    """, String.class, orderId);
            if (!references.isEmpty()) {
                return references.get(0);
            }
            Thread.sleep(100);
        }
        throw new AssertionError("The card payment never reached Stripe");
    }

    private String latestPaymentId(String orderId) {
        return jdbc.queryForObject("SELECT id::text FROM payments.payments WHERE order_id = ?::uuid "
                + "ORDER BY created_at DESC LIMIT 1", String.class, orderId);
    }

    private String latestPaymentStatus(String orderId) {
        return jdbc.queryForObject("SELECT status FROM payments.payments WHERE order_id = ?::uuid "
                + "ORDER BY created_at DESC LIMIT 1", String.class, orderId);
    }

    private void waitForLatestPayment(String orderId, String expected) throws InterruptedException {
        long giveUpAt = System.currentTimeMillis() + WAIT_AT_MOST_MILLIS;
        while (System.currentTimeMillis() < giveUpAt) {
            if (expected.equals(latestPaymentStatus(orderId))) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Payment never became " + expected);
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
        throw new AssertionError("Order never became " + expected);
    }

    // --- setting up ---------------------------------------------------------------------------------

    private static PretendStripe startStripe() {
        try {
            return new PretendStripe();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private MvcTestResult viewOrder(PlacedOrder order) {
        return mvc.get().uri("/api/store/" + shop.slug() + "/orders/" + order.orderNumber())
                .header("X-Order-Token", order.accessToken())
                .exchange();
    }

    private PlacedOrder placeOrder(int quantity) throws Exception {
        MvcTestResult result = mvc.post().uri("/api/store/" + shop.slug() + "/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"customerName": "Amina", "customerPhone": "0712345670", "paymentMethod": "CARD",
                         "lines": [{"productId": "%s", "quantity": %d}]}
                        """.formatted(sukuma, quantity))
                .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        String json = result.getResponse().getContentAsString();
        return new PlacedOrder(JsonPath.read(json, "$.orderId"), JsonPath.read(json, "$.orderNumber"),
                JsonPath.read(json, "$.accessToken"));
    }

    private void switchMethod(Shop shop, String provider, boolean enabled) {
        MvcTestResult result = mvc.put().uri("/api/admin/settings/payment-methods/" + provider)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + shop.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"enabled": %s}
                        """.formatted(enabled))
                .exchange();
        assertThat(result).hasStatus(HttpStatus.OK);
    }

    private Shop signUp() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String slug = "card-" + suffix;
        MvcTestResult result = mvc.post().uri("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"shopName": "Shop %s", "shopSlug": "%s", "ownerName": "Owner",
                         "email": "card+%s@example.com", "password": "a-long-password"}
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

        // Inventory makes the stock row when it hears about the new product (an event, a moment later).
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

    private record PlacedOrder(String orderId, String orderNumber, String accessToken) {
    }

}
