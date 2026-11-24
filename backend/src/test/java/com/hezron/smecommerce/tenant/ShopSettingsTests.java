package com.hezron.smecommerce.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import com.hezron.smecommerce.TestcontainersConfiguration;
import com.hezron.smecommerce.identity.internal.security.TokenClaims;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
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
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * The owner edits the shop's name, tagline, logo and colours in the admin; the storefront shows them.
 * Staff can read the details (for the shop's link) but not change them.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class ShopSettingsTests {

    private static final String SHOP = "/api/admin/shop";

    private static final String NEW_LOOK = """
            {"name": "Kamau Hardware", "tagline": "Tools for every job",
             "logoUrl": "https://res.cloudinary.com/demo/image/upload/logo.png",
             "primaryColor": "#2f4a3b", "accentColor": "#B4532A"}
            """;

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    JwtEncoder jwtEncoder;

    private Shop shopA;
    private Shop shopB;

    @BeforeEach
    void twoShops() throws Exception {
        shopA = signUp("a");
        shopB = signUp("b");
    }

    @Test
    void theAdminSeesItsOwnShopWithItsWebName() {
        MvcTestResult result = mvc.get().uri(SHOP).header(HttpHeaders.AUTHORIZATION, "Bearer " + shopA.token())
                .exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.slug").isEqualTo(shopA.slug());
        assertThat(result).bodyJson().extractingPath("$.primaryColor").isEqualTo("#1F1B17");
    }

    @Test
    void theOwnersChangesShowOnTheStorefrontAndAreAudited() {
        MvcTestResult saved = put(shopA.token(), NEW_LOOK);

        assertThat(saved).hasStatus(HttpStatus.OK);
        // Colours are stored in capitals, whichever way they were typed.
        assertThat(saved).bodyJson().extractingPath("$.primaryColor").isEqualTo("#2F4A3B");

        MvcTestResult profile = mvc.get().uri("/api/store/" + shopA.slug()).exchange();
        assertThat(profile).bodyJson().extractingPath("$.name").isEqualTo("Kamau Hardware");
        assertThat(profile).bodyJson().extractingPath("$.tagline").isEqualTo("Tools for every job");
        assertThat(profile).bodyJson().extractingPath("$.logoUrl")
                .isEqualTo("https://res.cloudinary.com/demo/image/upload/logo.png");

        Integer audited = jdbc.queryForObject("""
                SELECT count(*) FROM audit.audit_log WHERE tenant_id = ? AND action = 'SHOP_SETTINGS_UPDATED'
                """, Integer.class, shopA.tenantId());
        assertThat(audited).isEqualTo(1);
    }

    @Test
    void oneShopsChangesNeverReachAnotherShop() {
        put(shopA.token(), NEW_LOOK);

        MvcTestResult profileB = mvc.get().uri("/api/store/" + shopB.slug()).exchange();
        assertThat(profileB).bodyJson().extractingPath("$.name").isEqualTo("Shop " + shopB.label());
        assertThat(profileB).bodyJson().extractingPath("$.tagline").isNull();
    }

    @Test
    void emptyTaglineAndLogoRemoveThem() {
        put(shopA.token(), NEW_LOOK);

        MvcTestResult cleared = put(shopA.token(), """
                {"name": "Kamau Hardware", "tagline": "", "logoUrl": "",
                 "primaryColor": "#1F1B17", "accentColor": "#B4532A"}
                """);

        assertThat(cleared).hasStatus(HttpStatus.OK);
        assertThat(cleared).bodyJson().extractingPath("$.tagline").isNull();
        assertThat(cleared).bodyJson().extractingPath("$.logoUrl").isNull();
    }

    @Test
    void staffMayReadTheDetailsButNotChangeThem() {
        String staff = staffTokenFor(shopA.tenantId());

        MvcTestResult read = mvc.get().uri(SHOP).header(HttpHeaders.AUTHORIZATION, "Bearer " + staff).exchange();
        assertThat(read).hasStatus(HttpStatus.OK);

        assertThat(put(staff, NEW_LOOK)).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void refusesABadColourAndAPlainHttpLogo() {
        assertThat(put(shopA.token(), NEW_LOOK.replace("#2f4a3b", "green"))).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(put(shopA.token(), NEW_LOOK.replace("https://res", "http://res"))).hasStatus(HttpStatus.BAD_REQUEST);
    }

    // --- helpers ---------------------------------------------------------------------------------

    private MvcTestResult put(String token, String body) {
        return mvc.put().uri(SHOP)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private Shop signUp(String letter) throws Exception {
        String label = letter + UUID.randomUUID().toString().substring(0, 6);
        String slug = "settings-" + label;
        MvcTestResult result = mvc.post().uri("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"shopName": "Shop %s", "shopSlug": "%s", "ownerName": "Owner",
                         "email": "settings+%s@example.com", "password": "a-long-password"}
                        """.formatted(label, slug, label))
                .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        String body = result.getResponse().getContentAsString();
        return new Shop(label, slug, UUID.fromString(JsonPath.read(body, "$.tenantId")),
                JsonPath.read(body, "$.accessToken"));
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

    private record Shop(String label, String slug, UUID tenantId, String token) {
    }

}
