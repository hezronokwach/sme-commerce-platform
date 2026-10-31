package com.hezron.smecommerce.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.hezron.smecommerce.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
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
 * Login, refresh and logout through the real HTTP stack (Guide 2.2). Every test signs up its own owner,
 * so tests don't share accounts or tokens.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class LoginAndRefreshTests {

    private static final String PASSWORD = "a-long-password";
    private static final String COOKIE = "refresh_token";

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void signUpLogsTheOwnerIn() {
        String suffix = uniqueSuffix();
        MvcTestResult result = mvc.post().uri("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"shopName": "Shop", "shopSlug": "s-%s", "ownerName": "Owner",
                         "email": "o+%s@example.com", "password": "%s"}
                        """.formatted(suffix, suffix, PASSWORD))
                .exchange();

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).bodyJson().extractingPath("$.accessToken").isNotNull();
        assertRefreshCookieIsLockedDown(result);
    }

    @Test
    void loginReturnsAccessTokenAndRefreshCookie() {
        String email = signUpOwner();

        MvcTestResult result = login(email, PASSWORD);

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.accessToken").isNotNull();
        assertThat(result).bodyJson().extractingPath("$.expiresIn").isEqualTo(900);
        assertRefreshCookieIsLockedDown(result);
    }

    @Test
    void wrongPasswordAndUnknownEmailGiveTheSameAnswer() throws Exception {
        String email = signUpOwner();

        MvcTestResult wrongPassword = login(email, "not-the-password");
        MvcTestResult unknownEmail = login("nobody+" + uniqueSuffix() + "@example.com", "not-the-password");

        assertThat(wrongPassword).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(unknownEmail).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(wrongPassword.getResponse().getContentAsString())
                .isEqualTo(unknownEmail.getResponse().getContentAsString());
    }

    @Test
    void fiveWrongPasswordsLockTheAccountEvenForTheRightPassword() {
        String email = signUpOwner();
        for (int attempt = 1; attempt <= 5; attempt++) {
            assertThat(login(email, "wrong-" + attempt)).hasStatus(HttpStatus.UNAUTHORIZED);
        }

        MvcTestResult result = login(email, PASSWORD);

        assertThat(result).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("TOO_MANY_ATTEMPTS");
    }

    @Test
    void successfulLoginResetsTheFailedCount() {
        String email = signUpOwner();
        login(email, "wrong-1");
        login(email, "wrong-2");

        assertThat(login(email, PASSWORD)).hasStatus(HttpStatus.OK);

        assertThat(jdbc.queryForObject(
                "SELECT failed_logins FROM identity.staff_users WHERE email = ?", Integer.class, email)).isZero();
    }

    @Test
    void refreshGivesANewPairAndTheOldTokenStopsWorking() {
        String first = refreshCookieOf(login(signUpOwner(), PASSWORD));

        MvcTestResult refreshed = refresh(first);

        assertThat(refreshed).hasStatus(HttpStatus.OK);
        assertThat(refreshed).bodyJson().extractingPath("$.accessToken").isNotNull();
        assertThat(refreshCookieOf(refreshed)).isNotEqualTo(first);
        assertThat(refresh(first)).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void reusingAUsedTokenRevokesTheWholeFamily() {
        String first = refreshCookieOf(login(signUpOwner(), PASSWORD));
        String second = refreshCookieOf(refresh(first));

        // Someone replays the first token (as a thief would) ...
        assertThat(refresh(first)).hasStatus(HttpStatus.UNAUTHORIZED);

        // ... so the newer token, held by the real user or the thief, is dead too.
        assertThat(refresh(second)).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void anotherLoginSessionSurvivesAReuseInADifferentFamily() {
        String email = signUpOwner();
        String laptop = refreshCookieOf(login(email, PASSWORD));
        String phone = refreshCookieOf(login(email, PASSWORD));

        refresh(laptop);
        refresh(laptop); // reuse: kills the laptop family only

        assertThat(refresh(phone)).hasStatus(HttpStatus.OK);
    }

    @Test
    void logoutRevokesTheSessionAndClearsTheCookie() {
        String token = refreshCookieOf(login(signUpOwner(), PASSWORD));

        MvcTestResult result = mvc.post().uri("/api/auth/logout").cookie(new Cookie(COOKIE, token)).exchange();

        assertThat(result).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(result.getResponse().getCookie(COOKIE)).isNotNull()
                .satisfies(cookie -> assertThat(cookie.getMaxAge()).isZero());
        assertThat(refresh(token)).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void refreshWithoutCookieOrWithGarbageIsUnauthorized() {
        assertThat(mvc.post().uri("/api/auth/refresh").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);

        MvcTestResult garbage = refresh("not-a-real-token");
        assertThat(garbage).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(garbage).bodyJson().extractingPath("$.code").isEqualTo("INVALID_REFRESH_TOKEN");
    }

    @Test
    void storesOnlyTheHashOfTheRefreshToken() {
        String token = refreshCookieOf(login(signUpOwner(), PASSWORD));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM identity.refresh_tokens WHERE token_hash = ?",
                Integer.class, token)).isZero();
    }

    private static void assertRefreshCookieIsLockedDown(MvcTestResult result) {
        Cookie cookie = result.getResponse().getCookie(COOKIE);
        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSecure()).isTrue();
        assertThat(cookie.getPath()).isEqualTo("/api/auth");
        assertThat(cookie.getAttribute("SameSite")).isEqualTo("Strict");
    }

    /** Signs up a new owner and returns their email. */
    private String signUpOwner() {
        String suffix = uniqueSuffix();
        String email = "owner+" + suffix + "@example.com";
        assertThat(mvc.post().uri("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"shopName": "Shop", "shopSlug": "shop-%s", "ownerName": "Owner",
                         "email": "%s", "password": "%s"}
                        """.formatted(suffix, email, PASSWORD))
                .exchange()).hasStatus(HttpStatus.CREATED);
        return email;
    }

    private MvcTestResult login(String email, String password) {
        return mvc.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s"}
                        """.formatted(email, password))
                .exchange();
    }

    private MvcTestResult refresh(String token) {
        return mvc.post().uri("/api/auth/refresh").cookie(new Cookie(COOKIE, token)).exchange();
    }

    private static String refreshCookieOf(MvcTestResult result) {
        Cookie cookie = result.getResponse().getCookie(COOKIE);
        assertThat(cookie).isNotNull();
        return cookie.getValue();
    }

    private static String uniqueSuffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

}
