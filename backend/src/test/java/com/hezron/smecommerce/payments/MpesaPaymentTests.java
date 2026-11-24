package com.hezron.smecommerce.payments;

import static org.assertj.core.api.Assertions.assertThat;

import com.hezron.smecommerce.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * M-Pesa through Daraja (issues "M-Pesa part 1 and 2", "Buffer + payment tests"), against a pretend
 * Daraja. The callbacks are Safaricom's documented bodies (test resources mpesa/*.json).
 * <p>
 * Done-when (#14): success, cancel, wrong PIN and duplicate callbacks all leave the payment in the
 * correct final state. Done-when (#15): duplicate, out-of-order and unknown-id callbacks are tested.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class MpesaPaymentTests {

    private static final long WAIT_AT_MOST_MILLIS = 20_000;
    private static final String SECRET = "test-callback-secret-0123456789";
    private static final PretendDaraja DARAJA = startDaraja();

    @DynamicPropertySource
    static void pointAtPretendDaraja(DynamicPropertyRegistry registry) {
        registry.add("app.payments.mpesa.base-url", DARAJA::url);
        registry.add("app.payments.mpesa.consumer-key", () -> "test-key");
        registry.add("app.payments.mpesa.consumer-secret", () -> "test-secret");
        registry.add("app.payments.mpesa.shortcode", () -> "174379");
        registry.add("app.payments.mpesa.passkey", () -> "test-passkey");
        registry.add("app.payments.mpesa.callback-base-url", () -> "https://shop.example");
        registry.add("app.payments.mpesa.callback-secret", () -> SECRET);
        // Reconciliation asks quickly here, so the "no callback" tests don't wait minutes.
        registry.add("app.payments.reconciliation.every", () -> "200ms");
        registry.add("app.payments.reconciliation.first-check-after-accepted", () -> "300ms");
        registry.add("app.payments.reconciliation.backoff", () -> "200ms");
    }

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbc;

    private Shop shop;
    private String sukuma;

    @BeforeEach
    void aShopThatTakesMpesa() throws Exception {
        shop = signUp();
        sukuma = createStockedProduct(shop, 5000, 20);
        switchMethod("MPESA", true);
        // Most tests answer by callback; a "still processing" query keeps reconciliation out of the way.
        DARAJA.answerQueriesWith(500, "{\"errorCode\": \"500.001.1001\", \"errorMessage\": \"The transaction is being processed\"}");
    }

    @Test
    void anOrderSendsThePromptWithEverythingDarajaNeeds() throws Exception {
        String orderId = placeOrder(2);

        String reference = waitForReference(orderId);
        PretendDaraja.SentRequest push = pushFor(reference);
        assertThat(push.authorization()).isEqualTo("Bearer " + PretendDaraja.TOKEN);
        String body = push.body();
        String timestamp = JsonPath.read(body, "$.Timestamp");
        assertThat(timestamp).matches("\\d{14}");
        String expectedPassword = Base64.getEncoder()
                .encodeToString(("174379" + "test-passkey" + timestamp).getBytes(StandardCharsets.UTF_8));
        assertThat(JsonPath.<String>read(body, "$.Password")).isEqualTo(expectedPassword);
        assertThat(JsonPath.<String>read(body, "$.BusinessShortCode")).isEqualTo("174379");
        assertThat(JsonPath.<Integer>read(body, "$.Amount")).isEqualTo(100); // KES 100.00, whole shillings
        assertThat(JsonPath.<String>read(body, "$.PhoneNumber")).isEqualTo("254712345670");
        assertThat(JsonPath.<String>read(body, "$.PartyA")).isEqualTo("254712345670");
        assertThat(JsonPath.<String>read(body, "$.CallBackURL"))
                .isEqualTo("https://shop.example/api/webhooks/mpesa/" + SECRET);
        assertThat(JsonPath.<String>read(body, "$.AccountReference")).hasSizeLessThanOrEqualTo(12);
        assertThat(latestPayment(orderId, "status")).isEqualTo("PENDING");
    }

    @Test
    void theAccessTokenIsReusedAndRenewedAfterA401() throws Exception {
        int tokensBefore = DARAJA.tokenRequests();
        waitForReference(placeOrder(1));
        waitForReference(placeOrder(1));
        int afterTwoOrders = DARAJA.tokenRequests();

        DARAJA.rejectNextTokenUse();
        String third = placeOrder(1);

        // The 401 drops the token, a new one is fetched, and the prompt is sent again.
        waitForReference(third);
        assertThat(afterTwoOrders - tokensBefore).isLessThanOrEqualTo(1);
        assertThat(DARAJA.tokenRequests()).isEqualTo(afterTwoOrders + 1);
    }

    @Test
    void aSuccessCallbackMarksTheOrderPaid() throws Exception {
        String orderId = placeOrder(1);
        String reference = waitForReference(orderId);

        MvcTestResult result = callback(successBody(reference, "50.00"));

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.ResultCode").isEqualTo(0);
        waitForOrderStatus(orderId, "PAID");
        assertThat(latestPayment(orderId, "receipt_number")).isEqualTo("NLJ7RT61SV");
    }

    @Test
    void aCancelledPromptCanBeTriedAgain() throws Exception {
        String orderId = placeOrder(1);
        String reference = waitForReference(orderId);

        callback(body("mpesa/callback-cancelled.json", reference, null));

        waitForLatestPayment(orderId, "FAILED");
        assertThat(latestPayment(orderId, "result_code")).isEqualTo("1032");
        assertThat(orderStatus(orderId)).isEqualTo("PENDING_PAYMENT");
    }

    @Test
    void aWrongPinCanBeTriedAgain() throws Exception {
        String orderId = placeOrder(1);
        String reference = waitForReference(orderId);

        callback(body("mpesa/callback-wrong-pin.json", reference, null));

        waitForLatestPayment(orderId, "FAILED");
        assertThat(latestPayment(orderId, "result_code")).isEqualTo("2001");
        assertThat(orderStatus(orderId)).isEqualTo("PENDING_PAYMENT");
    }

    @Test
    void aDuplicateSuccessChangesNothing() throws Exception {
        String orderId = placeOrder(1);
        String reference = waitForReference(orderId);

        callback(successBody(reference, "50.00"));
        callback(successBody(reference, "50.00"));

        waitForOrderStatus(orderId, "PAID");
        assertThat(outcomes(reference)).containsExactlyInAnyOrder("APPLIED", "DUPLICATE");
        Integer paidRows = jdbc.queryForObject(
                "SELECT count(*) FROM ordering.order_status_history WHERE order_id = ?::uuid AND to_status = 'PAID'",
                Integer.class, orderId);
        assertThat(paidRows).isEqualTo(1);
    }

    @Test
    void aFailureArrivingAfterTheSuccessCannotUndoIt() throws Exception {
        String orderId = placeOrder(1);
        String reference = waitForReference(orderId);

        callback(successBody(reference, "50.00"));
        callback(body("mpesa/callback-cancelled.json", reference, null));

        waitForOrderStatus(orderId, "PAID");
        assertThat(latestPayment(orderId, "status")).isEqualTo("SUCCEEDED");
        assertThat(outcomes(reference)).containsExactlyInAnyOrder("APPLIED", "DUPLICATE");
    }

    @Test
    void aCallbackForAnUnknownPromptIsLoggedAndIgnored() throws Exception {
        MvcTestResult result = callback(successBody("ws_CO_nobody_knows", "50.00"));

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(outcomes("ws_CO_nobody_knows")).containsExactly("UNKNOWN_REFERENCE");
    }

    @Test
    void aSuccessForAnotherAmountIsNeverMarkedPaid() throws Exception {
        String orderId = placeOrder(1);
        String reference = waitForReference(orderId);

        callback(successBody(reference, "1.00"));

        assertThat(outcomes(reference)).containsExactly("AMOUNT_MISMATCH");
        assertThat(latestPayment(orderId, "status")).isEqualTo("PENDING");
    }

    @Test
    void aCallbackToTheWrongAddressIsNotFoundAndChangesNothing() throws Exception {
        String orderId = placeOrder(1);
        String reference = waitForReference(orderId);

        MvcTestResult result = mvc.post().uri("/api/webhooks/mpesa/a-guess")
                .contentType(MediaType.APPLICATION_JSON)
                .content(successBody(reference, "50.00"))
                .exchange();

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(outcomes(reference)).isEmpty();
        assertThat(latestPayment(orderId, "status")).isEqualTo("PENDING");
    }

    @Test
    void whenTheCustomerPaidButNoCallbackCameTheStatusQueryFindsIt() throws Exception {
        String orderId = placeOrder(1);
        waitForReference(orderId);

        DARAJA.answerQueriesWith(200, """
                {"ResponseCode": "0", "ResponseDescription": "The service request has been accepted successsfully",
                 "MerchantRequestID": "29115-34620561-1", "CheckoutRequestID": "x",
                 "ResultCode": "0", "ResultDesc": "The service request is processed successfully."}
                """);

        waitForOrderStatus(orderId, "PAID");
    }

    @Test
    void anOrderWithCentsIsRefusedBecauseMpesaTakesWholeShillings() throws Exception {
        String odd = createStockedProduct(shop, 5050, 5);

        MvcTestResult placed = mvc.post().uri("/api/store/" + shop.slug() + "/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(orderBody(odd, 1))
                .exchange();
        String orderId = JsonPath.read(placed.getResponse().getContentAsString(), "$.orderId");

        waitForLatestPayment(orderId, "FAILED");
        assertThat(latestPayment(orderId, "failure_reason")).isEqualTo("M-Pesa only takes whole shillings.");
    }

    // --- helpers ----------------------------------------------------------------------------------

    private MvcTestResult callback(String body) {
        return mvc.post().uri("/api/webhooks/mpesa/" + SECRET)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private static String successBody(String reference, String amount) throws IOException {
        return body("mpesa/callback-success.json", reference, amount);
    }

    private static String body(String resource, String reference, String amount) throws IOException {
        String text;
        try (InputStream in = MpesaPaymentTests.class.getResourceAsStream("/" + resource)) {
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        text = text.replace("CHECKOUT_ID", reference);
        if (amount != null) {
            text = text.replace("AMOUNT", amount);
        }
        return text;
    }

    private List<String> outcomes(String reference) {
        return jdbc.queryForList("SELECT outcome FROM payments.callback_log WHERE provider_reference = ?",
                String.class, reference);
    }

    private PretendDaraja.SentRequest pushFor(String reference) {
        PretendDaraja.SentRequest push = DARAJA.pushFor(reference);
        if (push == null) {
            throw new AssertionError("No prompt was sent for " + reference);
        }
        return push;
    }

    private String latestPayment(String orderId, String column) {
        return jdbc.queryForObject("SELECT " + column + "::text FROM payments.payments WHERE order_id = ?::uuid "
                + "ORDER BY created_at DESC LIMIT 1", String.class, orderId);
    }

    private String orderStatus(String orderId) {
        return jdbc.queryForObject("SELECT status FROM ordering.orders WHERE id = ?::uuid", String.class, orderId);
    }

    private String waitForReference(String orderId) throws InterruptedException {
        long giveUpAt = System.currentTimeMillis() + WAIT_AT_MOST_MILLIS;
        while (System.currentTimeMillis() < giveUpAt) {
            String reference = latestPayment(orderId, "provider_reference");
            if (reference != null) {
                return reference;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("The prompt was never sent; payment is " + latestPayment(orderId, "status"));
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

    private String placeOrder(int quantity) throws Exception {
        MvcTestResult result = mvc.post().uri("/api/store/" + shop.slug() + "/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(orderBody(sukuma, quantity))
                .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return JsonPath.read(result.getResponse().getContentAsString(), "$.orderId");
    }

    private static String orderBody(String productId, int quantity) {
        return """
                {"customerName": "Amina", "customerPhone": "0712345670", "paymentMethod": "MPESA",
                 "lines": [{"productId": "%s", "quantity": %d}]}
                """.formatted(productId, quantity);
    }

    private void switchMethod(String provider, boolean enabled) {
        MvcTestResult result = mvc.put().uri("/api/admin/settings/payment-methods/" + provider)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + shop.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\": " + enabled + "}")
                .exchange();
        assertThat(result).hasStatus(HttpStatus.OK);
    }

    private static PretendDaraja startDaraja() {
        try {
            return new PretendDaraja();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private Shop signUp() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String slug = "mpesa-" + suffix;
        MvcTestResult result = mvc.post().uri("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"shopName": "Shop %s", "shopSlug": "%s", "ownerName": "Owner",
                         "email": "mpesa+%s@example.com", "password": "a-long-password"}
                        """.formatted(suffix, slug, suffix))
                .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return new Shop(slug, JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken"));
    }

    private String createStockedProduct(Shop shop, long priceMinor, int stock) throws Exception {
        MvcTestResult created = mvc.post().uri("/api/admin/products")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + shop.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Item " + priceMinor + "\", \"priceMinor\": " + priceMinor + "}")
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
        MvcTestResult restock = mvc.post().uri("/api/admin/stock/" + productId + "/adjustments")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + shop.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"change\": " + stock + ", \"reason\": \"RESTOCK\"}")
                .exchange();
        assertThat(restock).hasStatus(HttpStatus.OK);
        return productId;
    }

    private record Shop(String slug, String token) {
    }

}
