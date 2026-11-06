package com.hezron.smecommerce.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.hezron.smecommerce.TestcontainersConfiguration;
import com.hezron.smecommerce.shared.PhoneNumber;
import com.hezron.smecommerce.shared.TenantId;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** IdentityApi.findOrCreateCustomer as ordering uses it at checkout (Guide 2.6). */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class IdentityApiTests {

    @Autowired
    IdentityApi identity;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void firstOrderCreatesTheCustomerWithTheStandardPhone() {
        TenantId shop = TenantId.of(UUID.randomUUID());

        UUID customerId = identity.findOrCreateCustomer(shop, " Amina Wanjiru ", PhoneNumber.parse("0712 345 678"));

        String stored = jdbc.queryForObject(
                "SELECT phone || ' ' || full_name FROM identity.customers WHERE id = ?", String.class, customerId);
        assertThat(stored).isEqualTo("254712345678 Amina Wanjiru");
    }

    @Test
    void theSamePhoneWrittenDifferentlyIsTheSameCustomer() {
        TenantId shop = TenantId.of(UUID.randomUUID());

        UUID first = identity.findOrCreateCustomer(shop, "Amina", PhoneNumber.parse("0712345678"));
        UUID second = identity.findOrCreateCustomer(shop, "Amina", PhoneNumber.parse("+254 712 345 678"));

        assertThat(second).isEqualTo(first);
        assertThat(customersIn(shop)).isEqualTo(1);
    }

    @Test
    void aReturningCustomerWithANewNameKeepsTheNewestName() {
        TenantId shop = TenantId.of(UUID.randomUUID());
        UUID customerId = identity.findOrCreateCustomer(shop, "Amina", PhoneNumber.parse("0712345678"));

        identity.findOrCreateCustomer(shop, "Amina Wanjiru", PhoneNumber.parse("0712345678"));

        String name = jdbc.queryForObject(
                "SELECT full_name FROM identity.customers WHERE id = ?", String.class, customerId);
        assertThat(name).isEqualTo("Amina Wanjiru");
    }

    @Test
    void theSamePhoneAtTwoShopsIsTwoCustomers() {
        PhoneNumber phone = PhoneNumber.parse("0712345678");

        UUID atShopA = identity.findOrCreateCustomer(TenantId.of(UUID.randomUUID()), "Amina", phone);
        UUID atShopB = identity.findOrCreateCustomer(TenantId.of(UUID.randomUUID()), "Amina", phone);

        assertThat(atShopB).isNotEqualTo(atShopA);
    }

    private int customersIn(TenantId shop) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM identity.customers WHERE tenant_id = ?", Integer.class, shop.value());
        return count;
    }

}
