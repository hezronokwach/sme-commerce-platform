package com.hezron.smecommerce.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.hezron.smecommerce.TestcontainersConfiguration;
import com.hezron.smecommerce.inventory.internal.application.StockService;
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

/** Shop B can't see or change shop A's stock through the API (ADR 0005: cross-tenant tests per module). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class InventoryCrossTenantTests {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    StockService stockService;

    private String tokenOfA;
    private String tokenOfB;
    private UUID productOfA;

    @BeforeEach
    void shopAHasStock() throws Exception {
        String[] shopA = signUp("a");
        String[] shopB = signUp("b");
        tokenOfA = shopA[1];
        tokenOfB = shopB[1];

        productOfA = UUID.randomUUID();
        stockService.createStockItemForProduct(UUID.fromString(shopA[0]), productOfA);
        assertThat(adjust(tokenOfA, 10)).hasStatus(HttpStatus.OK);
    }

    @Test
    void shopBCannotReadAsStock() {
        MvcTestResult result = get(tokenOfB);

        // 404, not 403: shop B doesn't even learn that the product exists.
        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("STOCK_ITEM_NOT_FOUND");
    }

    @Test
    void shopBCannotChangeAsStock() {
        MvcTestResult result = adjust(tokenOfB, -10);

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        // And A's stock is unchanged.
        assertThat(get(tokenOfA)).bodyJson().extractingPath("$.onHand").isEqualTo(10);
    }

    private MvcTestResult get(String token) {
        return mvc.get().uri("/api/admin/stock/" + productOfA)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).exchange();
    }

    private MvcTestResult adjust(String token, int change) {
        return mvc.post().uri("/api/admin/stock/" + productOfA + "/adjustments")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"change": %d, "reason": "ADJUSTMENT"}
                        """.formatted(change))
                .exchange();
    }

    /** Returns {tenantId, accessToken}. */
    private String[] signUp(String label) throws Exception {
        String suffix = label + "-" + UUID.randomUUID().toString().substring(0, 8);
        MvcTestResult result = mvc.post().uri("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"shopName": "Shop %s", "shopSlug": "inv-%s", "ownerName": "Owner",
                         "email": "inv+%s@example.com", "password": "a-long-password"}
                        """.formatted(suffix, suffix, suffix))
                .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        String json = result.getResponse().getContentAsString();
        String tenantId = JsonPath.read(json, "$.tenantId");
        String token = JsonPath.read(json, "$.accessToken");
        return new String[] {tenantId, token};
    }

}
