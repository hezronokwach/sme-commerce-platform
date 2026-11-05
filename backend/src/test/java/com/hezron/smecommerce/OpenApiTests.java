package com.hezron.smecommerce;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * The API description is served without a token, and is saved to docs/api/openapi.json for the
 * frontends. Like ModularityTests and docs/modules: every build refreshes the file, and it is committed
 * with the code that changed it, so a diff shows exactly how the API changed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class OpenApiTests {

    /** Relative to backend/, where Maven runs the tests. */
    private static final Path API_DESCRIPTION_FILE = Path.of("..", "docs", "api", "openapi.json");

    @Autowired
    MockMvcTester mvc;

    @Test
    void apiDescriptionListsTheEndpointsWithReadableNames() throws Exception {
        String json = fetchApiDescription();

        assertThat(json).contains("/api/admin/products");
        assertThat(json).contains("/api/admin/categories");
        assertThat(json).contains("\"productList\"");
        assertThat(json).contains("\"authLogin\"");
        assertThat(json).contains("bearerAuth");
    }

    @Test
    void writesTheApiDescriptionForTheFrontends() throws Exception {
        String json = fetchApiDescription();

        Files.createDirectories(API_DESCRIPTION_FILE.getParent());
        Files.writeString(API_DESCRIPTION_FILE, json + "\n", StandardCharsets.UTF_8);

        assertThat(API_DESCRIPTION_FILE).exists();
    }

    @Test
    void swaggerUiIsReachable() {
        // /swagger-ui.html redirects to the UI page itself.
        MvcTestResult result = mvc.get().uri("/swagger-ui.html").exchange();

        assertThat(result).hasStatus3xxRedirection();
    }

    private String fetchApiDescription() throws Exception {
        MvcTestResult result = mvc.get().uri("/v3/api-docs").exchange();
        assertThat(result).hasStatus(HttpStatus.OK);
        return result.getResponse().getContentAsString();
    }

}
