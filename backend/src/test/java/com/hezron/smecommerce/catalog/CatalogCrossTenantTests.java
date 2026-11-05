package com.hezron.smecommerce.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.hezron.smecommerce.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Shop B can't see or touch shop A's catalog through the API (ADR 0005: cross-tenant tests are
 * mandatory per module). Before each test, shop A gets one category and one product.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class CatalogCrossTenantTests {

    @Autowired
    MockMvcTester mvc;

    private String tokenOfA;
    private String tokenOfB;
    private String categoryOfA;
    private String productOfA;

    @BeforeEach
    void shopAHasACategoryAndAProduct() throws Exception {
        tokenOfA = signUpAndGetToken("a");
        tokenOfB = signUpAndGetToken("b");

        MvcTestResult category = send(tokenOfA, "POST", "/api/admin/categories", """
                {"name": "Vegetables"}
                """);
        assertThat(category).hasStatus(HttpStatus.CREATED);
        categoryOfA = JsonPath.read(category.getResponse().getContentAsString(), "$.id");

        MvcTestResult product = send(tokenOfA, "POST", "/api/admin/products", """
                {"name": "Sukuma", "priceMinor": 5000, "categoryId": "%s"}
                """.formatted(categoryOfA));
        assertThat(product).hasStatus(HttpStatus.CREATED);
        productOfA = JsonPath.read(product.getResponse().getContentAsString(), "$.id");
    }

    @Test
    void shopBListsNoneOfAsProductsOrCategories() throws Exception {
        List<String> productIds = idsIn(send(tokenOfB, "GET", "/api/admin/products", null));
        List<String> categoryIds = idsIn(send(tokenOfB, "GET", "/api/admin/categories", null));

        assertThat(productIds).doesNotContain(productOfA);
        assertThat(categoryIds).doesNotContain(categoryOfA);
    }

    @Test
    void shopBCannotReadAsProduct() {
        MvcTestResult result = send(tokenOfB, "GET", "/api/admin/products/" + productOfA, null);

        // 404, not 403: shop B doesn't even learn that the id exists.
        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("PRODUCT_NOT_FOUND");
    }

    @Test
    void shopBCannotEditAsProduct() {
        MvcTestResult result = send(tokenOfB, "PUT", "/api/admin/products/" + productOfA, """
                {"version": 0, "name": "Hacked", "priceMinor": 1, "status": "ACTIVE"}
                """);

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        // And A's product is unchanged.
        MvcTestResult asSeenByA = send(tokenOfA, "GET", "/api/admin/products/" + productOfA, null);
        assertThat(asSeenByA).bodyJson().extractingPath("$.name").isEqualTo("Sukuma");
    }

    @Test
    void shopBCannotArchiveAsProduct() {
        assertThat(send(tokenOfB, "DELETE", "/api/admin/products/" + productOfA, null))
                .hasStatus(HttpStatus.NOT_FOUND);

        MvcTestResult asSeenByA = send(tokenOfA, "GET", "/api/admin/products/" + productOfA, null);
        assertThat(asSeenByA).bodyJson().extractingPath("$.status").isEqualTo("ACTIVE");
    }

    @Test
    void shopBCannotEditOrDeleteAsCategory() {
        MvcTestResult edit = send(tokenOfB, "PUT", "/api/admin/categories/" + categoryOfA, """
                {"name": "Hacked"}
                """);
        MvcTestResult delete = send(tokenOfB, "DELETE", "/api/admin/categories/" + categoryOfA, null);

        assertThat(edit).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(delete).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void shopBCannotPutAProductInAsCategory() {
        MvcTestResult result = send(tokenOfB, "POST", "/api/admin/products", """
                {"name": "Sneaky", "priceMinor": 100, "categoryId": "%s"}
                """.formatted(categoryOfA));

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("CATEGORY_NOT_FOUND");
    }

    private static List<String> idsIn(MvcTestResult result) throws Exception {
        assertThat(result).hasStatus(HttpStatus.OK);
        return JsonPath.read(result.getResponse().getContentAsString(), "$[*].id");
    }

    /** One request as the owner holding {@code token}. {@code json} is null for GET and DELETE. */
    private MvcTestResult send(String token, String method, String uri, String json) {
        String authorization = "Bearer " + token;
        if (method.equals("GET")) {
            return mvc.get().uri(uri).header(HttpHeaders.AUTHORIZATION, authorization).exchange();
        }
        if (method.equals("DELETE")) {
            return mvc.delete().uri(uri).header(HttpHeaders.AUTHORIZATION, authorization).exchange();
        }
        if (method.equals("PUT")) {
            return mvc.put().uri(uri).header(HttpHeaders.AUTHORIZATION, authorization)
                    .contentType(MediaType.APPLICATION_JSON).content(json).exchange();
        }
        return mvc.post().uri(uri).header(HttpHeaders.AUTHORIZATION, authorization)
                .contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    private String signUpAndGetToken(String label) throws Exception {
        String suffix = label + "-" + UUID.randomUUID().toString().substring(0, 8);
        String body = """
                {"shopName": "Shop %s", "shopSlug": "x-%s", "ownerName": "Owner",
                 "email": "x+%s@example.com", "password": "a-long-password"}
                """.formatted(suffix, suffix, suffix);
        MvcTestResult result = mvc.post().uri("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON).content(body).exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

}
