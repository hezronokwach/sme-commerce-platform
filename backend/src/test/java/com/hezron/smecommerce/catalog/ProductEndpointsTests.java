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
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * The product endpoints through the real HTTP stack (Guide 3.1). Each test signs up its own shop.
 * {@code @RecordApplicationEvents} keeps every event published during a test, so we can check them.
 */
@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class ProductEndpointsTests {

    private static final String PRODUCTS = "/api/admin/products";

    @Autowired
    MockMvcTester mvc;

    @Autowired
    ApplicationEvents events;

    private String ownerToken;

    @BeforeEach
    void signUpAShop() throws Exception {
        ownerToken = signUpAndGetToken();
    }

    @Test
    void createSavesAnActiveProductInKes() {
        MvcTestResult result = post(PRODUCTS, """
                {"name": "Sukuma bundle", "priceMinor": 5000, "description": "Fresh from Limuru"}
                """);

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).bodyJson().extractingPath("$.slug").isEqualTo("sukuma-bundle");
        assertThat(result).bodyJson().extractingPath("$.priceMinor").isEqualTo(5000);
        assertThat(result).bodyJson().extractingPath("$.currency").isEqualTo("KES");
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("ACTIVE");
        assertThat(result).bodyJson().extractingPath("$.version").isEqualTo(0);
    }

    @Test
    void createAnnouncesTheNewProduct() {
        post(PRODUCTS, """
                {"name": "Mandazi", "priceMinor": 2000}
                """);

        long count = events.stream(ProductCreatedEvent.class).count();
        assertThat(count).isEqualTo(1);
    }

    @Test
    void sameNameGetsTheNextFreeSlug() {
        post(PRODUCTS, """
                {"name": "Chapati", "priceMinor": 3000}
                """);

        MvcTestResult second = post(PRODUCTS, """
                {"name": "Chapati", "priceMinor": 3500}
                """);

        assertThat(second).hasStatus(HttpStatus.CREATED);
        assertThat(second).bodyJson().extractingPath("$.slug").isEqualTo("chapati-2");
    }

    @Test
    void negativePriceIsAValidationError() {
        MvcTestResult result = post(PRODUCTS, """
                {"name": "Refund", "priceMinor": -100}
                """);

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
        assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("priceMinor");
    }

    @Test
    void cannotCreateAnArchivedProduct() {
        MvcTestResult result = post(PRODUCTS, """
                {"name": "Old stock", "priceMinor": 100, "status": "ARCHIVED"}
                """);

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("PRODUCT_STATUS_INVALID");
    }

    @Test
    void unknownCategoryIsRefused() {
        MvcTestResult result = post(PRODUCTS, """
                {"name": "Tea", "priceMinor": 1500, "categoryId": "%s"}
                """.formatted(UUID.randomUUID()));

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("CATEGORY_NOT_FOUND");
    }

    @Test
    void listCanBeFilteredByStatus() throws Exception {
        post(PRODUCTS, """
                {"name": "On sale", "priceMinor": 100}
                """);
        post(PRODUCTS, """
                {"name": "Coming soon", "priceMinor": 100, "status": "DRAFT"}
                """);

        List<String> all = namesIn(get(PRODUCTS));
        List<String> active = namesIn(get(PRODUCTS + "?status=ACTIVE"));

        assertThat(all).containsExactlyInAnyOrder("On sale", "Coming soon");
        assertThat(active).containsExactly("On sale");
    }

    @Test
    void updateWithTheLoadedVersionSucceedsAndBumpsTheVersion() throws Exception {
        String id = createProduct("Milk", 6000);

        MvcTestResult result = put(PRODUCTS + "/" + id, """
                {"version": 0, "name": "Milk 500ml", "priceMinor": 6500, "status": "ACTIVE"}
                """);

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.name").isEqualTo("Milk 500ml");
        assertThat(result).bodyJson().extractingPath("$.priceMinor").isEqualTo(6500);
        assertThat(result).bodyJson().extractingPath("$.version").isEqualTo(1);
        // The slug stays: it is the product page's address.
        assertThat(result).bodyJson().extractingPath("$.slug").isEqualTo("milk");
    }

    @Test
    void updateWithAnOldVersionIsRefused() throws Exception {
        String id = createProduct("Bread", 7000);
        // Wanjiru saves first: version 0 becomes 1.
        put(PRODUCTS + "/" + id, """
                {"version": 0, "name": "Bread 400g", "priceMinor": 7000, "status": "ACTIVE"}
                """);

        // Otieno still has version 0 open in his form.
        MvcTestResult stale = put(PRODUCTS + "/" + id, """
                {"version": 0, "name": "White bread", "priceMinor": 6800, "status": "ACTIVE"}
                """);

        assertThat(stale).hasStatus(HttpStatus.CONFLICT);
        assertThat(stale).bodyJson().extractingPath("$.code").isEqualTo("CONCURRENT_UPDATE");
        // Wanjiru's change is still there.
        assertThat(get(PRODUCTS + "/" + id)).bodyJson().extractingPath("$.name").isEqualTo("Bread 400g");
    }

    @Test
    void deleteArchivesTheProduct() throws Exception {
        String id = createProduct("Eggs", 1500);

        assertThat(delete(PRODUCTS + "/" + id)).hasStatus(HttpStatus.NO_CONTENT);

        MvcTestResult afterwards = get(PRODUCTS + "/" + id);
        assertThat(afterwards).hasStatus(HttpStatus.OK);
        assertThat(afterwards).bodyJson().extractingPath("$.status").isEqualTo("ARCHIVED");
        assertThat(namesIn(get(PRODUCTS + "?status=ACTIVE"))).doesNotContain("Eggs");
    }

    @Test
    void unknownProductIsNotFound() {
        MvcTestResult result = get(PRODUCTS + "/" + UUID.randomUUID());

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("PRODUCT_NOT_FOUND");
    }

    private String createProduct(String name, long priceMinor) throws Exception {
        MvcTestResult result = post(PRODUCTS, """
                {"name": "%s", "priceMinor": %d}
                """.formatted(name, priceMinor));
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private static List<String> namesIn(MvcTestResult result) throws Exception {
        assertThat(result).hasStatus(HttpStatus.OK);
        return JsonPath.read(result.getResponse().getContentAsString(), "$[*].name");
    }

    private String signUpAndGetToken() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String body = """
                {"shopName": "Shop", "shopSlug": "prod-%s", "ownerName": "Owner",
                 "email": "prod+%s@example.com", "password": "a-long-password"}
                """.formatted(suffix, suffix);
        MvcTestResult result = mvc.post().uri("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON).content(body).exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
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
