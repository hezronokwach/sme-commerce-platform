package com.hezron.smecommerce.ordering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hezron.smecommerce.TestcontainersConfiguration;
import com.hezron.smecommerce.ordering.internal.domain.ActorType;
import com.hezron.smecommerce.ordering.internal.domain.OrderStatus;
import com.hezron.smecommerce.ordering.internal.domain.PaymentMethod;
import com.hezron.smecommerce.ordering.internal.persistence.OrderEntity;
import com.hezron.smecommerce.ordering.internal.persistence.OrderLineEntity;
import com.hezron.smecommerce.ordering.internal.persistence.OrderLineRepository;
import com.hezron.smecommerce.ordering.internal.persistence.OrderRepository;
import com.hezron.smecommerce.ordering.internal.persistence.OrderStatusHistoryEntity;
import com.hezron.smecommerce.ordering.internal.persistence.OrderStatusHistoryRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * What V50 guarantees even if the Java code has a bug: the database refuses the row. Plus round trips
 * through the entities, so the mapping matches the tables.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class OrderingDatabaseRulesTests {

    private static final String PHONE = "254712345678";

    @Autowired
    OrderRepository orders;

    @Autowired
    OrderLineRepository lines;

    @Autowired
    OrderStatusHistoryRepository history;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void orderWithItsLineAndHistoryIsSavedAndOnlyFoundInItsOwnShop() {
        UUID shop = UUID.randomUUID();
        OrderEntity order = orders.saveAndFlush(newOrder(shop, "MM-000001", 10000));
        lines.saveAndFlush(OrderLineEntity.newLine(shop, order.getId(), UUID.randomUUID(), "Sukuma bundle", 5000, 2));
        history.saveAndFlush(OrderStatusHistoryEntity.newEntry(shop, order.getId(), null, OrderStatus.CREATED,
                "Order placed", ActorType.CUSTOMER, null));

        Optional<OrderEntity> found = orders.findByIdAndTenantId(order.getId(), shop);
        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(found.get().getTotalMinor()).isEqualTo(10000);

        List<OrderLineEntity> savedLines = lines.findAllByOrderIdAndTenantId(order.getId(), shop);
        assertThat(savedLines).hasSize(1);
        assertThat(savedLines.get(0).getLineTotalMinor()).isEqualTo(10000);
        assertThat(history.findAllByOrderIdAndTenantIdOrderByCreatedAtAsc(order.getId(), shop)).hasSize(1);

        assertThat(orders.findByIdAndTenantId(order.getId(), UUID.randomUUID())).isEmpty();
    }

    @Test
    void sameOrderNumberTwiceInOneShopIsRefused() {
        UUID shop = UUID.randomUUID();
        orders.saveAndFlush(newOrder(shop, "MM-000007", 100));
        // Another shop may use the same number.
        orders.saveAndFlush(newOrder(UUID.randomUUID(), "MM-000007", 100));

        assertThatThrownBy(() -> orders.saveAndFlush(newOrder(shop, "MM-000007", 100)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_orders_tenant_number");
    }

    @Test
    void aLineCannotBelongToAnotherShopsOrder() {
        UUID shopA = UUID.randomUUID();
        OrderEntity orderOfA = orders.saveAndFlush(newOrder(shopA, "MM-000002", 100));
        UUID shopB = UUID.randomUUID();

        OrderLineEntity sneaky = OrderLineEntity.newLine(shopB, orderOfA.getId(), UUID.randomUUID(), "X", 100, 1);
        assertThatThrownBy(() -> lines.saveAndFlush(sneaky))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_order_lines_order");
    }

    @Test
    void aLineTotalThatDisagreesWithPriceTimesQuantityIsRefused() {
        UUID shop = UUID.randomUUID();
        OrderEntity order = orders.saveAndFlush(newOrder(shop, "MM-000003", 100));

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO ordering.order_lines
                    (id, tenant_id, order_id, product_id, product_name, unit_price_minor, quantity, line_total_minor)
                VALUES (?, ?, ?, ?, 'Tampered', 5000, 2, 1)
                """, UUID.randomUUID(), shop, order.getId(), UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_order_lines_total");
    }

    @Test
    void phoneMustBeInTheStandardKenyanForm() {
        UUID shop = UUID.randomUUID();
        OrderEntity withLocalForm = OrderEntity.newOrder(shop, "MM-000004", UUID.randomUUID(), "Amina",
                "0712345678", PaymentMethod.MPESA, 100, "KES", randomHash());

        assertThatThrownBy(() -> orders.saveAndFlush(withLocalForm))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_orders_phone_format");
    }

    @Test
    void counterPrefixMustBeShortCapitals() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO ordering.order_number_counters (tenant_id, prefix) VALUES (?, 'mm')", UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_order_number_counters_prefix");
    }

    private static OrderEntity newOrder(UUID tenantId, String orderNumber, long totalMinor) {
        return OrderEntity.newOrder(tenantId, orderNumber, UUID.randomUUID(), "Amina", PHONE, PaymentMethod.MPESA,
                totalMinor, "KES", randomHash());
    }

    /** 64 hex characters, like a SHA-256 hash; random so the unique index never clashes between tests. */
    private static String randomHash() {
        String twoUuids = UUID.randomUUID().toString() + UUID.randomUUID().toString();
        return twoUuids.replace("-", "");
    }

}
