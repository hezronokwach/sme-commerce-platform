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
 * Issue "React storefront scaffold": a customer browses a shop's products with no login (workflow W5).
 * <p>
 * Before each test, shop A has a category with one ACTIVE product, plus one DRAFT and one ARCHIVED
 * product. Shop B has one ACTIVE product of its own.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class StorefrontCatalogTests {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbc;

    private Shop shopA;
    private Shop shopB;
    private String vegetablesOfA;
    private String sukumaOfA;
    private String draftOfA;
    private String archivedOfA;
    private String nailsOfB;

    @BeforeEach
    void twoShopsWithProducts() throws Exception {
        shopA = signUp("a");
        shopB = signUp("b");

        MvcTestResult category = send(shopA, "POST", "/api/admin/categories", """
                {"name": "Vegetables"}
                """);
        assertThat(category).hasStatus(HttpStatus.CREATED);
        vegetablesOfA = JsonPath.read(category.getResponse().getContentAsString(), "$.id");

        sukumaOfA = createProduct(shopA, """
                {"name": "Sukuma bundle", "slug": "sukuma-bundle", "priceMinor": 5000, "categoryId": "%s"}
                """.formatted(vegetablesOfA));
        createProduct(shopA, """
                {"name": "Tomatoes", "priceMinor": 3000}
                """);
        draftOfA = createProduct(shopA, """
                {"name": "Secret recipe", "slug": "secret-recipe", "priceMinor": 100, "status": "DRAFT"}
                """);
        archivedOfA = createProduct(shopA, """
                {"name": "Old stock", "slug": "old-stock", "priceMinor": 100}
                """);
        assertThat(send(shopA, "DELETE", "/api/admin/products/" + archivedOfA, null))
                .hasStatus(HttpStatus.NO_CONTENT);

        nailsOfB = createProduct(shopB, """
                {"name": "Nails", "priceMinor": 200}
                """);
    }

    @Test
    void aCustomerSeesOnlyTheProductsOnSale() throws Exception {
        MvcTestResult result = mvc.get().uri("/api/store/" + shopA.slug() + "/products").exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        List<String> ids = idsIn(result);
        assertThat(ids).contains(sukumaOfA);
        assertThat(ids).hasSize(2);
        assertThat(ids).doesNotContain(draftOfA, archivedOfA);
    }

    @Test
    void aShopsListNeverShowsAnotherShopsProducts() throws Exception {
        List<String> idsOfA = idsIn(mvc.get().uri("/api/store/" + shopA.slug() + "/products").exchange());
        List<String> idsOfB = idsIn(mvc.get().uri("/api/store/" + shopB.slug() + "/products").exchange());

        assertThat(idsOfA).doesNotContain(nailsOfB);
        assertThat(idsOfB).containsExactly(nailsOfB);
    }

    @Test
    void theListCanBeNarrowedToOneCategory() throws Exception {
        MvcTestResult result = mvc.get().uri("/api/store/" + shopA.slug() + "/products?categoryId=" + vegetablesOfA)
                .exchange();

        assertThat(idsIn(result)).containsExactly(sukumaOfA);
    }

    @Test
    void aProductPageIsFoundByItsWebName() {
        MvcTestResult result = mvc.get().uri("/api/store/" + shopA.slug() + "/products/sukuma-bundle").exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.id").isEqualTo(sukumaOfA);
        assertThat(result).bodyJson().extractingPath("$.priceMinor").isEqualTo(5000);
        assertThat(result).bodyJson().extractingPath("$.currency").isEqualTo("KES");
        // Admin-only details stay inside.
        assertThat(result).bodyJson().doesNotHavePath("$.status");
        assertThat(result).bodyJson().doesNotHavePath("$.version");
    }

    @Test
    void draftArchivedAndOtherShopsProductPagesAreNotFound() {
        assertProductNotFound(shopA.slug(), "secret-recipe");
        assertProductNotFound(shopA.slug(), "old-stock");
        assertProductNotFound(shopA.slug(), "no-such-thing");
        // Shop A's product, asked for through shop B's address.
        assertProductNotFound(shopB.slug(), "sukuma-bundle");
    }

    @Test
    void theMenuListsTheShopsCategories() throws Exception {
        MvcTestResult result = mvc.get().uri("/api/store/" + shopA.slug() + "/categories").exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(idsIn(result)).containsExactly(vegetablesOfA);
        assertThat(idsIn(mvc.get().uri("/api/store/" + shopB.slug() + "/categories").exchange())).isEmpty();
    }

    @Test
    void anUnknownShopIsNotFound() {
        String slug = "nobody-" + UUID.randomUUID().toString().substring(0, 6);

        assertShopNotFound("/api/store/" + slug + "/products");
        assertShopNotFound("/api/store/" + slug + "/products/sukuma-bundle");
        assertShopNotFound("/api/store/" + slug + "/categories");
    }

    @Test
    void aSuspendedShopShowsNothing() {
        jdbc.update("UPDATE tenant.tenants SET status = 'SUSPENDED' WHERE slug = ?", shopA.slug());

        assertShopNotFound("/api/store/" + shopA.slug() + "/products");
        assertShopNotFound("/api/store/" + shopA.slug() + "/products/sukuma-bundle");
    }

    // --- helpers ---------------------------------------------------------------------------------

    private void assertProductNotFound(String shopSlug, String productSlug) {
        MvcTestResult result = mvc.get().uri("/api/store/" + shopSlug + "/products/" + productSlug).exchange();
        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("PRODUCT_NOT_FOUND");
    }

    private void assertShopNotFound(String uri) {
        MvcTestResult result = mvc.get().uri(uri).exchange();
        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("SHOP_NOT_FOUND");
    }

    private static List<String> idsIn(MvcTestResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$[*].id");
    }

    private String createProduct(Shop shop, String json) throws Exception {
        MvcTestResult result = send(shop, "POST", "/api/admin/products", json);
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private MvcTestResult send(Shop shop, String method, String uri, String json) {
        if (method.equals("POST")) {
            return mvc.post().uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + shop.token())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json)
                    .exchange();
        }
        return mvc.delete().uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + shop.token())
                .exchange();
    }

    private Shop signUp(String label) throws Exception {
        String suffix = label + UUID.randomUUID().toString().substring(0, 6);
        String slug = "store-" + suffix;
        MvcTestResult result = mvc.post().uri("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"shopName": "Shop %s", "shopSlug": "%s", "ownerName": "Owner",
                         "email": "store+%s@example.com", "password": "a-long-password"}
                        """.formatted(suffix, slug, suffix))
                .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return new Shop(slug, JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken"));
    }

    private record Shop(String slug, String token) {
    }

}
