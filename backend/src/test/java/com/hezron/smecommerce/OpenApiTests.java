package com.hezron.smecommerce;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** The API description is served without a token and lists the catalog endpoints. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class OpenApiTests {

    @Autowired
    MockMvcTester mvc;

    @Test
    void apiDescriptionListsTheProductEndpointsAndTheBearerScheme() throws Exception {
        MvcTestResult result = mvc.get().uri("/v3/api-docs").exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        String json = result.getResponse().getContentAsString();
        assertThat(json).contains("/api/admin/products");
        assertThat(json).contains("/api/admin/categories");
        assertThat(json).contains("bearerAuth");
    }

    @Test
    void swaggerUiIsReachable() {
        // /swagger-ui.html redirects to the UI page itself.
        MvcTestResult result = mvc.get().uri("/swagger-ui.html").exchange();

        assertThat(result).hasStatus3xxRedirection();
    }

}
