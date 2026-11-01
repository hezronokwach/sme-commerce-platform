package com.hezron.smecommerce.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hezron.smecommerce.TestcontainersConfiguration;
import com.hezron.smecommerce.shared.TenantId;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Storefront requests find their shop from the web name in the URL (Guide 2.4): the public shop
 * profile, and {@link TenantApi#findActiveTenantIdBySlug} that other modules' storefront endpoints use.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class StorefrontShopTests {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    TenantApi tenantApi;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void profileShowsTheShopsPublicDetailsWithoutLogin() {
        String slug = newSlug();
        tenantApi.createTenant("Mama Mboga", slug);

        MvcTestResult result = mvc.get().uri("/api/store/" + slug).exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.name").isEqualTo("Mama Mboga");
        assertThat(result).bodyJson().extractingPath("$.currency").isEqualTo("KES");
        assertThat(result).bodyJson().extractingPath("$.primaryColor").isEqualTo("#0F766E");
        // Internal details stay inside: customers never need the shop's id, plan or status.
        assertThat(result).bodyJson().doesNotHavePath("$.id");
        assertThat(result).bodyJson().doesNotHavePath("$.tenantId");
        assertThat(result).bodyJson().doesNotHavePath("$.status");
    }

    @Test
    void slugInTheUrlIsCaseInsensitive() {
        String slug = newSlug();
        tenantApi.createTenant("Kamau Hardware", slug);

        assertThat(mvc.get().uri("/api/store/" + slug.toUpperCase()).exchange()).hasStatus(HttpStatus.OK);
    }

    @Test
    void unknownShopIsNotFound() {
        MvcTestResult result = mvc.get().uri("/api/store/" + newSlug()).exchange();

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("SHOP_NOT_FOUND");
    }

    @Test
    void suspendedShopLooksExactlyLikeAMissingOne() {
        String slug = newSlug();
        tenantApi.createTenant("Closed Shop", slug);
        jdbc.update("UPDATE tenant.tenants SET status = 'SUSPENDED' WHERE slug = ?", slug);

        MvcTestResult result = mvc.get().uri("/api/store/" + slug).exchange();

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("SHOP_NOT_FOUND");
    }

    @Test
    void slugLookupGivesOtherModulesTheShopsId() {
        String slug = newSlug();
        TenantId created = tenantApi.createTenant("Lookup Shop", slug);

        assertThat(tenantApi.findActiveTenantIdBySlug(slug)).isEqualTo(created);
    }

    @Test
    void slugLookupRefusesASuspendedShop() {
        String slug = newSlug();
        tenantApi.createTenant("Suspended Shop", slug);
        jdbc.update("UPDATE tenant.tenants SET status = 'SUSPENDED' WHERE slug = ?", slug);

        assertThatThrownBy(() -> tenantApi.findActiveTenantIdBySlug(slug))
                .isInstanceOf(ShopNotFoundException.class);
    }

    private static String newSlug() {
        return "shop-" + UUID.randomUUID().toString().substring(0, 8);
    }

}
