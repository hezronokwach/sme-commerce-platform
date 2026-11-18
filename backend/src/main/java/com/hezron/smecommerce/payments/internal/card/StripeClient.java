package com.hezron.smecommerce.payments.internal.card;

import java.net.http.HttpClient;
import java.util.Locale;
import java.util.UUID;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The two Stripe calls we need, as plain HTTPS (no Stripe SDK, ADR 0013): create a payment intent, and
 * read one. Stripe takes form-encoded bodies and answers JSON. Auth is the secret key as a Bearer token.
 * <p>
 * Called with NO database transaction open, like every provider call.
 */
@Component
@EnableConfigurationProperties(CardProperties.class)
public class StripeClient {

    private final CardProperties properties;
    private final JsonMapper jsonMapper;
    private final RestClient http;

    public StripeClient(CardProperties properties, JsonMapper jsonMapper) {
        this.properties = properties;
        this.jsonMapper = jsonMapper;

        HttpClient client = HttpClient.newBuilder().connectTimeout(properties.timeout()).build();
        JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(client);
        requests.setReadTimeout(properties.timeout());
        this.http = RestClient.builder().baseUrl(properties.baseUrl()).requestFactory(requests).build();
    }

    public boolean isConfigured() {
        return properties.isConfigured();
    }

    public String publishableKey() {
        return properties.publishableKey();
    }

    /**
     * {@code POST /v1/payment_intents}. The payment's id is the Idempotency-Key: if this is sent twice
     * (a retried event), Stripe returns the same intent instead of making a second one.
     *
     * @return the intent's id, e.g. pi_3N...
     * @throws StripeRefusedException if Stripe said no (4xx), with its message
     * @throws RuntimeException       on a timeout or a connection problem (the caller treats it as UNKNOWN)
     */
    public String createIntent(UUID paymentId, long amountMinor, String currency, String orderNumber) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        // Stripe counts KES in cents too: 5000 means KES 50.00, the same minor units we store.
        form.add("amount", String.valueOf(amountMinor));
        form.add("currency", currency.toLowerCase(Locale.ROOT));
        form.add("automatic_payment_methods[enabled]", "true");
        form.add("description", "Order " + orderNumber);
        form.add("metadata[payment_id]", paymentId.toString());
        form.add("metadata[order_number]", orderNumber);

        try {
            String body = http.post().uri("/v1/payment_intents")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.secretKey())
                    .header("Idempotency-Key", paymentId.toString())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(String.class);
            return jsonMapper.readTree(body).path("id").asString();
        } catch (HttpClientErrorException refused) {
            throw new StripeRefusedException(errorMessage(refused.getResponseBodyAsString()));
        }
    }

    /** {@code GET /v1/payment_intents/{id}}: its status, client secret, amount received and last error. */
    public Intent readIntent(String intentId) {
        String body = http.get().uri("/v1/payment_intents/{id}", intentId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.secretKey())
                .retrieve()
                .body(String.class);
        JsonNode intent = jsonMapper.readTree(body);
        return new Intent(intent.path("id").asString(), intent.path("status").asString(),
                intent.path("client_secret").asString(), intent.path("amount_received").asLong(),
                intent.path("latest_charge").asString(""),
                intent.path("last_payment_error").path("message").asString(""));
    }

    /**
     * What we read from an intent. The client secret lets the customer's browser confirm this one payment;
     * it is handed to that browser only, never stored or logged.
     */
    public record Intent(String id, String status, String clientSecret, long amountReceived, String latestCharge,
            String lastErrorMessage) {

        @Override
        public String toString() {
            return "Intent[id=" + id + ", status=" + status + "]";
        }
    }

    private String errorMessage(String errorBody) {
        try {
            String message = jsonMapper.readTree(errorBody).path("error").path("message").asString("");
            if (!message.isBlank()) {
                return message;
            }
        } catch (RuntimeException notJson) {
            // fall through
        }
        return "The card payment could not be started.";
    }

}
