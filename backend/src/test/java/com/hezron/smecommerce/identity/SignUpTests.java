package com.hezron.smecommerce.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.hezron.smecommerce.TestcontainersConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Sign-up through the real HTTP stack: security chain, validation, controller, service and Postgres.
 * Every test uses its own email and slug, so tests don't depend on each other's data.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class SignUpTests {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void createsOneShopAndOneActiveOwner() {
        String suffix = uniqueSuffix();

        MvcTestResult result = signUp("Mama Mboga", "mama-" + suffix, "wanjiru+" + suffix + "@example.com",
                "a-long-password");

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).bodyJson().extractingPath("$.tenantId").isNotNull();

        UUID tenantId = jdbc.queryForObject("SELECT id FROM tenant.tenants WHERE slug = ?", UUID.class, "mama-" + suffix);
        assertThat(jdbc.queryForList("SELECT role || '/' || status FROM identity.staff_users WHERE tenant_id = ?",
                String.class, tenantId)).containsExactly("OWNER/ACTIVE");
    }

    @Test
    void storesEmailLowercaseAndOnlyAPasswordHash() {
        String suffix = uniqueSuffix();
        String email = "Owner+" + suffix + "@Example.COM";

        assertThat(signUp("Kamau Hardware", "kamau-" + suffix, email, "a-long-password"))
                .hasStatus(HttpStatus.CREATED);

        String stored = jdbc.queryForObject("SELECT email || ' ' || password_hash FROM identity.staff_users WHERE email = ?",
                String.class, email.toLowerCase());
        assertThat(stored).startsWith(email.toLowerCase() + " {bcrypt}").doesNotContain("a-long-password");
    }

    @Test
    void rejectsSecondAccountWithSameEmailAndLeavesNoShopBehind() {
        String suffix = uniqueSuffix();
        String email = "dup+" + suffix + "@example.com";
        signUp("First Shop", "first-" + suffix, email, "a-long-password");

        // Same email in a different case, new slug: the slug is free, so only the email check can stop it.
        MvcTestResult second = signUp("Second Shop", "second-" + suffix, email.toUpperCase(), "a-long-password");

        assertThat(second).hasStatus(HttpStatus.CONFLICT);
        assertThat(second).bodyJson().extractingPath("$.code").isEqualTo("EMAIL_TAKEN");
        assertThat(countShops("second-" + suffix)).isZero();
    }

    @Test
    void rejectsTakenSlugAndCreatesNoUser() {
        String suffix = uniqueSuffix();
        signUp("Original", "taken-" + suffix, "a+" + suffix + "@example.com", "a-long-password");

        MvcTestResult second = signUp("Copycat", "taken-" + suffix, "b+" + suffix + "@example.com", "a-long-password");

        assertThat(second).hasStatus(HttpStatus.CONFLICT);
        assertThat(second).bodyJson().extractingPath("$.code").isEqualTo("SLUG_TAKEN");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM identity.staff_users WHERE email = ?",
                Integer.class, "b+" + suffix + "@example.com")).isZero();
    }

    @Test
    void rejectsShortPasswordWithFieldError() {
        String suffix = uniqueSuffix();

        MvcTestResult result = signUp("Shop", "short-" + suffix, "c+" + suffix + "@example.com", "short");

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(countShops("short-" + suffix)).isZero();
    }

    @Test
    void rejectsReservedSlug() {
        MvcTestResult result = signUp("Admin", "admin", "d+" + uniqueSuffix() + "@example.com", "a-long-password");

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("SLUG_NOT_ALLOWED");
    }

    private MvcTestResult signUp(String shopName, String slug, String email, String password) {
        String body = """
                {"shopName": "%s", "shopSlug": "%s", "ownerName": "Test Owner", "email": "%s", "password": "%s"}
                """.formatted(shopName, slug, email, password);
        return mvc.post().uri("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private int countShops(String slug) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM tenant.tenants WHERE slug = ?", Integer.class, slug);
        return count == null ? 0 : count;
    }

    private static String uniqueSuffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

}
