package com.hezron.smecommerce;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Guide 1.4: errors look the same everywhere, whether our code, Spring MVC or Spring Security
 * produced them.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class ErrorShapeTests {

    /**
     * The fields every error body has. ({@code type} is left out of the JSON while it's the default
     * {@code about:blank}, so it isn't listed.)
     */
    private static final Set<String> SHAPE = Set.of(
            "title", "status", "detail", "instance", "code", "correlationId", "timestamp");

    @Autowired
    MockMvcTester mvc;

    @Test
    void badInputAndUnknownIdHaveTheSameShape() throws Exception {
        MvcTestResult badInput = signUp("""
                {"shopName": "", "shopSlug": "x", "ownerName": "O", "email": "not-an-email", "password": "short"}
                """);
        MvcTestResult unknownId = mvc.get().uri("/api/admin/staff/" + UUID.randomUUID())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + newOwnerToken()).exchange();

        assertThat(badInput).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(unknownId).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(fieldsOf(badInput)).containsAll(SHAPE);
        assertThat(fieldsOf(unknownId)).containsAll(SHAPE);
        assertThat(badInput).bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
        assertThat(unknownId).bodyJson().extractingPath("$.code").isEqualTo("STAFF_NOT_FOUND");
    }

    @Test
    void validationErrorsListEachBadField() throws Exception {
        MvcTestResult result = signUp("""
                {"shopName": "Shop", "shopSlug": "fine-slug", "ownerName": "O", "email": "o@example.com", "password": "short"}
                """);

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("password");
        assertThat(result).bodyJson().extractingPath("$.errors[0].message").isNotNull();
    }

    @Test
    void springsOwnErrorsGetTheShapeToo() throws Exception {
        // Broken JSON never reaches our code: Spring rejects it while reading the body.
        MvcTestResult result = signUp("{ this is not json");

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(fieldsOf(result)).containsAll(SHAPE);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("BAD_REQUEST");
    }

    @Test
    void missingTokenGetsTheShapeAndKeepsTheBearerHeader() throws Exception {
        MvcTestResult result = mvc.get().uri("/api/admin/staff").exchange();

        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(result).headers().containsHeader(HttpHeaders.WWW_AUTHENTICATE);
        assertThat(fieldsOf(result)).containsAll(SHAPE);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("UNAUTHENTICATED");
    }

    @Test
    void correlationIdFromTheCallerIsEchoedBack() {
        MvcTestResult result = mvc.get().uri("/api/admin/staff").header(CorrelationIdFilter.HEADER, "order-flow-42")
                .exchange();

        assertThat(result).headers().hasValue(CorrelationIdFilter.HEADER, "order-flow-42");
        assertThat(result).bodyJson().extractingPath("$.correlationId").isEqualTo("order-flow-42");
    }

    @Test
    void unsafeCorrelationIdIsReplaced() {
        // A newline could forge a fake log line, so ids that aren't plain are swapped for our own.
        MvcTestResult result = mvc.get().uri("/api/admin/staff")
                .header(CorrelationIdFilter.HEADER, "abc\nERROR fake log line").exchange();

        assertThat(result).bodyJson().extractingPath("$.correlationId").asString()
                .doesNotContain("fake").isNotBlank();
    }

    private MvcTestResult signUp(String json) {
        return mvc.post().uri("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    private String newOwnerToken() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        MvcTestResult result = signUp("""
                {"shopName": "Shop", "shopSlug": "err-%s", "ownerName": "Owner",
                 "email": "err+%s@example.com", "password": "a-long-password"}
                """.formatted(suffix, suffix));
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    private static Set<String> fieldsOf(MvcTestResult result) throws Exception {
        Map<String, Object> body = JsonPath.read(result.getResponse().getContentAsString(), "$");
        return body.keySet();
    }

}
