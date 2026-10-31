package com.hezron.smecommerce.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.hezron.smecommerce.TestcontainersConfiguration;
import com.hezron.smecommerce.identity.internal.security.TokenClaims;
import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * The issue's "Done when": the protected endpoint rejects no token, a bad token and an expired token,
 * and accepts a good one. Plus the chain's edges: the webhook chain and paths nobody declared.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class JwtSecurityTests {

    private static final String ME = "/api/admin/me";

    @Autowired
    MockMvcTester mvc;

    /** The app's real encoder, so these tokens differ from good ones only in what each test changes. */
    @Autowired
    JwtEncoder jwtEncoder;

    @Test
    void validTokenFromSignUpIsAccepted() throws Exception {
        MvcTestResult signUp = signUp();
        String body = signUp.getResponse().getContentAsString();

        MvcTestResult me = getMe(JsonPath.read(body, "$.accessToken"));

        assertThat(me).hasStatus(HttpStatus.OK);
        assertThat(me).bodyJson().extractingPath("$.userId").isEqualTo(JsonPath.read(body, "$.userId"));
        assertThat(me).bodyJson().extractingPath("$.tenantId").isEqualTo(JsonPath.read(body, "$.tenantId"));
        assertThat(me).bodyJson().extractingPath("$.role").isEqualTo("OWNER");
    }

    @Test
    void noTokenIsRejected() {
        MvcTestResult result = mvc.get().uri(ME).exchange();

        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
        // RFC 6750: tells the client a bearer token is expected.
        assertThat(result).headers().containsHeader(HttpHeaders.WWW_AUTHENTICATE);
    }

    @Test
    void garbageTokenIsRejected() {
        assertThat(getMe("not.a.jwt")).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        SecretKeySpec otherKey = new SecretKeySpec(
                "a-different-secret-that-is-long-enough-0123456789".getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        JwtEncoder forger = new NimbusJwtEncoder(new ImmutableSecret<>(otherKey));

        assertThat(getMe(sign(forger, claims(Instant.now(), "OWNER", "sme-commerce"))))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void expiredTokenIsRejected() {
        // Issued 20 minutes ago with the real 15-minute lifetime: expired 5 minutes ago,
        // well past the decoder's 60-second clock-skew allowance.
        Instant issuedAt = Instant.now().minus(Duration.ofMinutes(20));

        assertThat(getMe(sign(jwtEncoder, claims(issuedAt, "OWNER", "sme-commerce"))))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void tokenFromAnotherIssuerIsRejected() {
        assertThat(getMe(sign(jwtEncoder, claims(Instant.now(), "OWNER", "someone-else"))))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void validTokenWithoutAStaffRoleIsForbidden() {
        // Correctly signed, so authentication passes (not 401), but the admin API is for staff only.
        assertThat(getMe(sign(jwtEncoder, claims(Instant.now(), "CUSTOMER", "sme-commerce"))))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void webhookPathIsNotBlockedByTheJwtChain() {
        // No webhook controller exists yet, so 404 shows the request got past security.
        MvcTestResult result = mvc.post().uri("/api/webhooks/anything").exchange();

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void undeclaredPathIsDenied() {
        assertThat(mvc.get().uri("/api/something-nobody-declared").exchange())
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    private MvcTestResult getMe(String token) {
        return mvc.get().uri(ME).header(HttpHeaders.AUTHORIZATION, "Bearer " + token).exchange();
    }

    private static JwtClaimsSet claims(Instant issuedAt, String role, String issuer) {
        return JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(UUID.randomUUID().toString())
                .claim(TokenClaims.TENANT_ID, UUID.randomUUID().toString())
                .claim(TokenClaims.ROLE, role)
                .claim(TokenClaims.SUBJECT_TYPE, "STAFF")
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(Duration.ofMinutes(15)))
                .build();
    }

    private static String sign(JwtEncoder encoder, JwtClaimsSet claims) {
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private MvcTestResult signUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        MvcTestResult result = mvc.post().uri("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"shopName": "Shop", "shopSlug": "jwt-%s", "ownerName": "Owner",
                         "email": "jwt+%s@example.com", "password": "a-long-password"}
                        """.formatted(suffix, suffix))
                .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return result;
    }

}
