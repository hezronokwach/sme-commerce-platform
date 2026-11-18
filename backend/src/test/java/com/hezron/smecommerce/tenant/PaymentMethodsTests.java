package com.hezron.smecommerce.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import com.hezron.smecommerce.TestcontainersConfiguration;
import com.hezron.smecommerce.identity.internal.security.TokenClaims;
import com.hezron.smecommerce.shared.TenantId;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.Instant;
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
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Issue "Second payment provider": each shop chooses which ways of paying it offers. The owner switches
 * them; the storefront profile lists only the shop's own; payments asks TenantApi before every payment.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class PaymentMethodsTests {

    private static final String SETTINGS = "/api/admin/settings/payment-methods";

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    JwtEncoder jwtEncoder;

    @Autowired
    TenantApi tenantApi;

    private Shop shopA;
    private Shop shopB;

    @BeforeEach
    void twoShops() throws Exception {
        shopA = signUp("a");
        shopB = signUp("b");
    }

    @Test
    void aNewShopOffersOnlyTheTestMpesaAtFirst() {
        MvcTestResult result = mvc.get().uri(SETTINGS).header(HttpHeaders.AUTHORIZATION, "Bearer " + shopA.token())
                .exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$[?(@.provider == 'FAKE')].enabled").asArray()
                .containsExactly(true);
        assertThat(result).bodyJson().extractingPath("$[?(@.provider == 'CARD')].enabled").asArray()
                .containsExactly(false);
        // M-Pesa is listed, but can't be used until it is connected.
        assertThat(result).bodyJson().extractingPath("$[?(@.provider == 'MPESA')].available").asArray()
                .containsExactly(false);
        assertThat(profileMethods(shopA)).containsExactly("FAKE");
    }

    @Test
    void theOwnerSwitchesTheShopFromTestMpesaToCard() {
        assertThat(set(shopA.token(), "CARD", true)).hasStatus(HttpStatus.OK);
        assertThat(set(shopA.token(), "FAKE", false)).hasStatus(HttpStatus.OK);

        assertThat(profileMethods(shopA)).containsExactly("CARD");
        TenantId tenantA = TenantId.of(shopA.tenantId());
        assertThat(tenantApi.isPaymentMethodEnabled(tenantA, "CARD")).isTrue();
        assertThat(tenantApi.isPaymentMethodEnabled(tenantA, "FAKE")).isFalse();
    }

    @Test
    void theLastWayToPayCannotBeTurnedOff() {
        MvcTestResult result = set(shopA.token(), "FAKE", false);

        assertThat(result).hasStatus(HttpStatus.CONFLICT);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("LAST_PAYMENT_METHOD");
        assertThat(profileMethods(shopA)).containsExactly("FAKE");
    }

    @Test
    void mpesaCannotBeTurnedOnYet() {
        MvcTestResult result = set(shopA.token(), "MPESA", true);

        assertThat(result).hasStatus(HttpStatus.CONFLICT);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("PAYMENT_METHOD_UNAVAILABLE");
    }

    @Test
    void anUnknownMethodIsRefused() {
        MvcTestResult result = set(shopA.token(), "BITCOIN", true);

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("PAYMENT_METHOD_UNKNOWN");
    }

    @Test
    void staffCannotChangeHowTheShopIsPaid() {
        String staffToken = staffTokenFor(shopA.tenantId());

        assertThat(set(staffToken, "CARD", true)).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(mvc.get().uri(SETTINGS).header(HttpHeaders.AUTHORIZATION, "Bearer " + staffToken).exchange())
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(profileMethods(shopA)).containsExactly("FAKE");
    }

    @Test
    void oneShopsSwitchNeverChangesAnother() {
        set(shopA.token(), "CARD", true);
        set(shopA.token(), "FAKE", false);

        assertThat(profileMethods(shopB)).containsExactly("FAKE");
        assertThat(tenantApi.isPaymentMethodEnabled(TenantId.of(shopB.tenantId()), "CARD")).isFalse();
    }

    @Test
    void theDefaultRowIsWhatTheMigrationAlsoGivesExistingShops() {
        Integer rows = jdbc.queryForObject("""
                SELECT count(*) FROM tenant.tenant_payment_providers
                WHERE tenant_id = ?::uuid AND provider = 'FAKE' AND enabled
                """, Integer.class, shopA.tenantId().toString());

        assertThat(rows).isEqualTo(1);
    }

    // --- helpers ----------------------------------------------------------------------------------

    private MvcTestResult set(String token, String provider, boolean enabled) {
        return mvc.put().uri(SETTINGS + "/" + provider)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"enabled": %s}
                        """.formatted(enabled))
                .exchange();
    }

    private List<String> profileMethods(Shop shop) {
        MvcTestResult profile = mvc.get().uri("/api/store/" + shop.slug()).exchange();
        assertThat(profile).hasStatus(HttpStatus.OK);
        try {
            return JsonPath.read(profile.getResponse().getContentAsString(), "$.paymentMethods");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private Shop signUp(String label) throws Exception {
        String suffix = label + UUID.randomUUID().toString().substring(0, 6);
        String slug = "pay-" + suffix;
        MvcTestResult result = mvc.post().uri("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"shopName": "Shop %s", "shopSlug": "%s", "ownerName": "Owner",
                         "email": "pay+%s@example.com", "password": "a-long-password"}
                        """.formatted(suffix, slug, suffix))
                .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        String body = result.getResponse().getContentAsString();
        return new Shop(slug, UUID.fromString(JsonPath.read(body, "$.tenantId")), JsonPath.read(body, "$.accessToken"));
    }

    /** Staff invites don't exist yet, so the staff member is inserted straight into the table. */
    private String staffTokenFor(UUID tenantId) {
        UUID staffId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO identity.staff_users (id, tenant_id, email, full_name, password_hash, role, status)
                VALUES (?, ?, ?, 'Staff Member', '{noop}unused', 'STAFF', 'ACTIVE')
                """, staffId, tenantId, "staff+" + staffId + "@example.com");
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("sme-commerce")
                .subject(staffId.toString())
                .claim(TokenClaims.TENANT_ID, tenantId.toString())
                .claim(TokenClaims.ROLE, "STAFF")
                .claim(TokenClaims.SUBJECT_TYPE, "STAFF")
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofMinutes(15)))
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    private record Shop(String slug, UUID tenantId, String token) {
    }

}
