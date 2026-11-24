package com.hezron.smecommerce.payments.internal.mpesa;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Map;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The three Daraja calls we need, as plain HTTPS (Guide 5.3, 5.4, 5.7): get an access token, send a PIN
 * prompt (STK push), and ask how a prompt ended (STK query). Called with no database transaction open.
 * <p>
 * Daraja answers errors with a JSON body too (errorCode, errorMessage), so those bodies are returned
 * like any other answer and the caller reads them. Timeouts and dropped connections fly on: the caller
 * records UNKNOWN, never FAILED, because the prompt may have reached the phone.
 */
@Component
@EnableConfigurationProperties(MpesaProperties.class)
public class DarajaClient {

    private static final ZoneId NAIROBI = ZoneId.of("Africa/Nairobi");
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    /** A token is renewed this long before it ends, so it can't run out halfway through a call. */
    private static final long RENEW_SECONDS_EARLY = 60;

    private final MpesaProperties properties;
    private final JsonMapper jsonMapper;
    private final RestClient http;

    private String cachedToken;
    private Instant tokenRenewAt;

    public DarajaClient(MpesaProperties properties, JsonMapper jsonMapper) {
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

    public MpesaProperties properties() {
        return properties;
    }

    /** {@code yyyyMMddHHmmss} in Nairobi time, as Daraja expects. */
    public String timestamp() {
        return TIMESTAMP.format(Instant.now().atZone(NAIROBI));
    }

    /** base64(shortcode + passkey + timestamp): the password of every STK request. */
    public String password(String timestamp) {
        String plain = properties.shortcode() + properties.passkey() + timestamp;
        return Base64.getEncoder().encodeToString(plain.getBytes(StandardCharsets.UTF_8));
    }

    /** {@code POST /mpesa/stkpush/v1/processrequest}: sends the PIN prompt to the customer's phone. */
    public JsonNode stkPush(Map<String, Object> body) {
        return postWithToken("/mpesa/stkpush/v1/processrequest", body);
    }

    /** {@code POST /mpesa/stkpushquery/v1/query}: how an earlier prompt ended. */
    public JsonNode stkQuery(Map<String, Object> body) {
        return postWithToken("/mpesa/stkpushquery/v1/query", body);
    }

    private JsonNode postWithToken(String path, Map<String, Object> body) {
        try {
            return post(path, body);
        } catch (HttpClientErrorException.Unauthorized expired) {
            // The token was cancelled or expired early. A 401 means nothing was done, so one retry is safe.
            forgetToken();
            return post(path, body);
        }
    }

    private JsonNode post(String path, Map<String, Object> body) {
        try {
            String answer = http.post().uri(path)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return jsonMapper.readTree(answer);
        } catch (HttpClientErrorException.Unauthorized unauthorized) {
            throw unauthorized;
        } catch (HttpStatusCodeException error) {
            return jsonMapper.readTree(error.getResponseBodyAsString());
        }
    }

    /** Guide 5.3: one token, reused until a minute before it ends. */
    private synchronized String accessToken() {
        Instant now = Instant.now();
        if (cachedToken != null && now.isBefore(tokenRenewAt)) {
            return cachedToken;
        }
        String credentials = properties.consumerKey() + ":" + properties.consumerSecret();
        String basic = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        String answer = http.get().uri("/oauth/v1/generate?grant_type=client_credentials")
                .header(HttpHeaders.AUTHORIZATION, "Basic " + basic)
                .retrieve()
                .body(String.class);
        JsonNode token = jsonMapper.readTree(answer);
        long expiresIn = token.path("expires_in").asLong(3599);
        cachedToken = token.path("access_token").asString();
        tokenRenewAt = now.plusSeconds(expiresIn - RENEW_SECONDS_EARLY);
        return cachedToken;
    }

    private synchronized void forgetToken() {
        cachedToken = null;
        tokenRenewAt = null;
    }

}
