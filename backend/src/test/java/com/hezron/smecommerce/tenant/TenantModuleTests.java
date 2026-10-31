package com.hezron.smecommerce.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hezron.smecommerce.TestcontainersConfiguration;
import com.hezron.smecommerce.shared.TenantId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.AssertablePublishedEvents;
import org.springframework.test.context.ActiveProfiles;

/**
 * Boots only the tenant module and the shared kernel, against a real Postgres. If tenant secretly
 * needed another module, this context would fail to start.
 */
@ApplicationModuleTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class TenantModuleTests {

    @Autowired
    TenantApi tenants;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void createsActiveShopWithDefaultBranding(AssertablePublishedEvents events) {
        TenantId id = tenants.createTenant("Mama Mboga", "mamamboga");

        assertThat(jdbc.queryForObject("SELECT status FROM tenant.tenants WHERE id = ?",
                String.class, id.value())).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tenant.tenant_branding WHERE tenant_id = ?",
                Integer.class, id.value())).isEqualTo(1);

        assertThat(events)
                .contains(TenantCreatedEvent.class)
                .matchingValue(TenantCreatedEvent::tenantId, id)
                .matchingValue(TenantCreatedEvent::slug, "mamamboga");
    }

    @Test
    void rejectsSlugAlreadyInUse() {
        tenants.createTenant("Kamau Hardware", "kamau-hardware");

        assertThatThrownBy(() -> tenants.createTenant("Another Kamau", "kamau-hardware"))
                .isInstanceOf(SlugTakenException.class)
                .hasFieldOrPropertyWithValue("code", "SLUG_TAKEN")
                .hasMessageContaining("kamau-hardware");
    }

    @Test
    void rejectsReservedAndMalformedSlugs() {
        assertThatThrownBy(() -> tenants.createTenant("Admin Shop", "admin"))
                .isInstanceOf(SlugNotAllowedException.class);
        assertThatThrownBy(() -> tenants.createTenant("Bad Shop", "Bad_Slug"))
                .isInstanceOf(SlugNotAllowedException.class);
        assertThatThrownBy(() -> tenants.createTenant("Short", "ab"))
                .isInstanceOf(SlugNotAllowedException.class);
    }

}
