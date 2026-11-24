package com.hezron.smecommerce.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.hezron.smecommerce.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
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
 * A product has up to five photos (https addresses, in practice Cloudinary uploads). The first is the
 * main photo. Without Cloudinary keys, asking to upload says "not set up" instead of failing oddly.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class ProductPhotosTests {

    private static final String PHOTO_1 = "https://res.cloudinary.com/demo/image/upload/v1/sme-commerce/one.jpg";
    private static final String PHOTO_2 = "https://res.cloudinary.com/demo/image/upload/v1/sme-commerce/two.jpg";

    @Autowired
    MockMvcTester mvc;

    private String token;
    private String slug;

    @BeforeEach
    void signUpAShop() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        slug = "photos-" + suffix;
        MvcTestResult result = mvc.post().uri("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"shopName": "Photo Shop", "shopSlug": "%s", "ownerName": "Owner",
                         "email": "photos+%s@example.com", "password": "a-long-password"}
                        """.formatted(slug, suffix))
                .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        token = JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    @Test
    void keepsThePhotosInOrderWithTheFirstAsTheMainOne() {
        MvcTestResult created = createProduct("[\"" + PHOTO_1 + "\", \"" + PHOTO_2 + "\"]");

        assertThat(created).hasStatus(HttpStatus.CREATED);
        assertThat(created).bodyJson().extractingPath("$.imageUrl").isEqualTo(PHOTO_1);
        assertThat(created).bodyJson().extractingPath("$.imageUrls").asArray().containsExactly(PHOTO_1, PHOTO_2);

        // Customers see the main photo in the list, and all of them on the product page.
        MvcTestResult list = mvc.get().uri("/api/store/" + slug + "/products").exchange();
        assertThat(list).bodyJson().extractingPath("$[0].imageUrl").isEqualTo(PHOTO_1);
        MvcTestResult page = mvc.get().uri("/api/store/" + slug + "/products/hammer").exchange();
        assertThat(page).bodyJson().extractingPath("$.imageUrls").asArray().containsExactly(PHOTO_1, PHOTO_2);
    }

    @Test
    void aProductWithoutPhotosHasNoMainPhoto() {
        MvcTestResult created = createProduct(null);

        assertThat(created).hasStatus(HttpStatus.CREATED);
        assertThat(created).bodyJson().extractingPath("$.imageUrl").isNull();
        assertThat(created).bodyJson().extractingPath("$.imageUrls").asArray().isEmpty();
    }

    @Test
    void refusesPlainHttpAddressesAndMoreThanFivePhotos() {
        assertThat(createProduct("[\"http://example.com/a.jpg\"]")).hasStatus(HttpStatus.BAD_REQUEST);

        String six = "[\"" + PHOTO_1 + "\",\"" + PHOTO_1 + "\",\"" + PHOTO_1 + "\",\"" + PHOTO_1 + "\",\""
                + PHOTO_1 + "\",\"" + PHOTO_1 + "\"]";
        assertThat(createProduct(six)).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void uploadingSaysNotSetUpWithoutCloudinaryKeys() {
        MvcTestResult result = mvc.post().uri("/api/admin/photo-uploads/signature")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange();

        assertThat(result).hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("PHOTO_UPLOADS_NOT_SET_UP");
    }

    @Test
    void uploadingNeedsALogin() {
        assertThat(mvc.post().uri("/api/admin/photo-uploads/signature").exchange())
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    private MvcTestResult createProduct(String imageUrlsJson) {
        String photos = "";
        if (imageUrlsJson != null) {
            photos = ", \"imageUrls\": " + imageUrlsJson;
        }
        return mvc.post().uri("/api/admin/products")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Hammer\", \"priceMinor\": 85000" + photos + "}")
                .exchange();
    }

}
