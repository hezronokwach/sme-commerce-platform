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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * The category endpoints through the real HTTP stack. Each test signs up its own shop, so tests don't
 * share data.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class CategoryEndpointsTests {

    private static final String CATEGORIES = "/api/admin/categories";

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbc;

    private String ownerToken;
    private String tenantId;

    @BeforeEach
    void signUpAShop() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String body = """
                {"shopName": "Shop", "shopSlug": "cat-%s", "ownerName": "Owner",
                 "email": "cat+%s@example.com", "password": "a-long-password"}
                """.formatted(suffix, suffix);
        MvcTestResult result = mvc.post().uri("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON).content(body).exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);

        String json = result.getResponse().getContentAsString();
        ownerToken = JsonPath.read(json, "$.accessToken");
        tenantId = JsonPath.read(json, "$.tenantId");
    }

    @Test
    void createBuildsTheSlugFromTheName() {
        MvcTestResult result = post(CATEGORIES, """
                {"name": "Fresh Vegetables"}
                """);

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).bodyJson().extractingPath("$.slug").isEqualTo("fresh-vegetables");
        assertThat(result).bodyJson().extractingPath("$.sortOrder").isEqualTo(0);
    }

    @Test
    void listIsInMenuOrder() throws Exception {
        post(CATEGORIES, """
                {"name": "Drinks", "sortOrder": 2}
                """);
        post(CATEGORIES, """
                {"name": "Bakery", "sortOrder": 1}
                """);

        MvcTestResult result = get(CATEGORIES);

        assertThat(result).hasStatus(HttpStatus.OK);
        List<String> names = JsonPath.read(result.getResponse().getContentAsString(), "$[*].name");
        assertThat(names).containsExactly("Bakery", "Drinks");
    }

    @Test
    void updateChangesNameSlugAndOrder() throws Exception {
        String id = createCategory("Snacks");

        MvcTestResult result = put(CATEGORIES + "/" + id, """
                {"name": "Snacks & Sweets", "slug": "snacks", "sortOrder": 5}
                """);

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.name").isEqualTo("Snacks & Sweets");
        assertThat(result).bodyJson().extractingPath("$.slug").isEqualTo("snacks");
        assertThat(result).bodyJson().extractingPath("$.sortOrder").isEqualTo(5);
    }

    @Test
    void sameSlugTwiceInOneShopIsRefused() {
        post(CATEGORIES, """
                {"name": "Fruit"}
                """);

        MvcTestResult second = post(CATEGORIES, """
                {"name": "Fruit"}
                """);

        assertThat(second).hasStatus(HttpStatus.CONFLICT);
        assertThat(second).bodyJson().extractingPath("$.code").isEqualTo("CATEGORY_SLUG_TAKEN");
    }

    @Test
    void nameWithoutLettersGivesInvalidSlug() {
        MvcTestResult result = post(CATEGORIES, """
                {"name": "!!!"}
                """);

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("SLUG_INVALID");
    }

    @Test
    void missingNameIsAValidationError() {
        MvcTestResult result = post(CATEGORIES, """
                {"sortOrder": 1}
                """);

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
        assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("name");
    }

    @Test
    void emptyCategoryCanBeDeleted() throws Exception {
        String id = createCategory("Seasonal");

        assertThat(delete(CATEGORIES + "/" + id)).hasStatus(HttpStatus.NO_CONTENT);

        List<String> ids = JsonPath.read(get(CATEGORIES).getResponse().getContentAsString(), "$[*].id");
        assertThat(ids).doesNotContain(id);
    }

    @Test
    void categoryWithProductsCannotBeDeleted() throws Exception {
        String id = createCategory("Vegetables");
        // Products have no endpoint yet, so one is inserted straight into the table.
        jdbc.update("""
                INSERT INTO catalog.products (id, tenant_id, category_id, name, slug, price_minor, currency)
                VALUES (?, ?::uuid, ?::uuid, 'Sukuma', 'sukuma', 5000, 'KES')
                """, UUID.randomUUID(), tenantId, id);

        MvcTestResult result = delete(CATEGORIES + "/" + id);

        assertThat(result).hasStatus(HttpStatus.CONFLICT);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("CATEGORY_IN_USE");
    }

    @Test
    void unknownCategoryIsNotFound() {
        MvcTestResult result = delete(CATEGORIES + "/" + UUID.randomUUID());

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("CATEGORY_NOT_FOUND");
    }

    private String createCategory(String name) throws Exception {
        MvcTestResult result = post(CATEGORIES, """
                {"name": "%s"}
                """.formatted(name));
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private MvcTestResult get(String uri) {
        return mvc.get().uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                .exchange();
    }

    private MvcTestResult post(String uri, String json) {
        return mvc.post().uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON).content(json)
                .exchange();
    }

    private MvcTestResult put(String uri, String json) {
        return mvc.put().uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON).content(json)
                .exchange();
    }

    private MvcTestResult delete(String uri) {
        return mvc.delete().uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                .exchange();
    }

}
