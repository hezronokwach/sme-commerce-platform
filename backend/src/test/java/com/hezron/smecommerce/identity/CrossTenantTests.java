package com.hezron.smecommerce.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.hezron.smecommerce.TestcontainersConfiguration;
import com.hezron.smecommerce.identity.internal.security.TokenClaims;
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
 * The issue's "Done when": shop B can't read shop A's data through the normal API.
 * <p>
 * Two real shops are signed up before each test. Shop A also gets a second (STAFF) member, so A has
 * two people and B has one: a missing {@code tenant_id} filter would show up as wrong counts.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class CrossTenantTests {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    JwtEncoder jwtEncoder;

    private Shop shopA;
    private Shop shopB;
    private UUID staffOfA;

    @BeforeEach
    void twoShops() throws Exception {
        shopA = signUp("a");
        shopB = signUp("b");
        staffOfA = addStaffMember(shopA.tenantId());
    }

    @Test
    void eachOwnerListsOnlyTheirOwnStaff() throws Exception {
        assertThat(staffIds(shopA)).containsExactlyInAnyOrder(shopA.ownerId(), staffOfA);
        assertThat(staffIds(shopB)).containsExactly(shopB.ownerId());
    }

    @Test
    void ownerOfBCannotFetchAPersonOfA() {
        MvcTestResult result = getAs(shopB, "/api/admin/staff/" + shopA.ownerId());

        // 404, not 403: shop B doesn't even learn that this id exists somewhere.
        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("STAFF_NOT_FOUND");
    }

    @Test
    void ownerCanFetchTheirOwnPeople() {
        MvcTestResult result = getAs(shopA, "/api/admin/staff/" + staffOfA);

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.role").isEqualTo("STAFF");
        // The password hash never leaves the server.
        assertThat(result).bodyJson().doesNotHavePath("$.passwordHash");
    }

    @Test
    void staffMemberCannotListStaff() {
        String staffToken = staffTokenFor(staffOfA, shopA.tenantId());

        MvcTestResult result = mvc.get().uri("/api/admin/staff")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + staffToken).exchange();

        // Logged in (so not 401) but the endpoint is for owners only.
        assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void noTokenMeansNoStaffList() {
        assertThat(mvc.get().uri("/api/admin/staff").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    private List<UUID> staffIds(Shop shop) throws Exception {
        MvcTestResult result = getAs(shop, "/api/admin/staff");
        assertThat(result).hasStatus(HttpStatus.OK);
        List<String> ids = JsonPath.read(result.getResponse().getContentAsString(), "$[*].id");
        return ids.stream().map(UUID::fromString).toList();
    }

    private MvcTestResult getAs(Shop shop, String uri) {
        return mvc.get().uri(uri).header(HttpHeaders.AUTHORIZATION, "Bearer " + shop.accessToken()).exchange();
    }

    /** Signs up a real shop through the API and keeps what the response returns. */
    private Shop signUp(String label) throws Exception {
        String suffix = label + "-" + UUID.randomUUID().toString().substring(0, 8);
        MvcTestResult result = mvc.post().uri("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"shopName": "Shop %s", "shopSlug": "shop-%s", "ownerName": "Owner %s",
                         "email": "owner+%s@example.com", "password": "a-long-password"}
                        """.formatted(suffix, suffix, suffix, suffix))
                .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        String body = result.getResponse().getContentAsString();
        return new Shop(UUID.fromString(JsonPath.read(body, "$.tenantId")),
                UUID.fromString(JsonPath.read(body, "$.userId")),
                JsonPath.read(body, "$.accessToken"));
    }

    /** Staff invites don't exist yet, so the second person is inserted straight into the table. */
    private UUID addStaffMember(UUID tenantId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO identity.staff_users (id, tenant_id, email, full_name, password_hash, role, status)
                VALUES (?, ?, ?, 'Staff Member', '{noop}unused', 'STAFF', 'ACTIVE')
                """, id, tenantId, "staff+" + id + "@example.com");
        return id;
    }

    private String staffTokenFor(UUID userId, UUID tenantId) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("sme-commerce")
                .subject(userId.toString())
                .claim(TokenClaims.TENANT_ID, tenantId.toString())
                .claim(TokenClaims.ROLE, "STAFF")
                .claim(TokenClaims.SUBJECT_TYPE, "STAFF")
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofMinutes(15)))
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    private record Shop(UUID tenantId, UUID ownerId, String accessToken) {
    }

}
