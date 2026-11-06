package com.hezron.smecommerce.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.hezron.smecommerce.TestcontainersConfiguration;
import com.hezron.smecommerce.catalog.internal.domain.ProductStatus;
import com.hezron.smecommerce.catalog.internal.persistence.ProductEntity;
import com.hezron.smecommerce.catalog.internal.persistence.ProductRepository;
import com.hezron.smecommerce.shared.Money;
import com.hezron.smecommerce.shared.TenantId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** CatalogApi as ordering uses it at checkout. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class CatalogApiTests {

    @Autowired
    CatalogApi catalog;

    @Autowired
    ProductRepository products;

    @Test
    void givesTheServersNameAndPriceAndWhetherTheProductCanBeOrdered() {
        UUID shop = UUID.randomUUID();
        UUID sukuma = saveProduct(shop, "Sukuma bundle", 5000, ProductStatus.ACTIVE);
        UUID draft = saveProduct(shop, "Coming soon", 100, ProductStatus.DRAFT);

        List<PriceSnapshot> snapshots = catalog.getPriceSnapshots(TenantId.of(shop), idList(sukuma, draft));

        assertThat(snapshots).hasSize(2);
        PriceSnapshot sukumaSnapshot = findSnapshot(snapshots, sukuma);
        assertThat(sukumaSnapshot.name()).isEqualTo("Sukuma bundle");
        assertThat(sukumaSnapshot.priceMinor()).isEqualTo(5000);
        assertThat(sukumaSnapshot.currency()).isEqualTo("KES");
        assertThat(sukumaSnapshot.active()).isTrue();
        assertThat(findSnapshot(snapshots, draft).active()).isFalse();
    }

    @Test
    void unknownProductsAndOtherShopsProductsAreSimplyMissing() {
        UUID shopA = UUID.randomUUID();
        UUID productOfA = saveProduct(shopA, "Mandazi", 2000, ProductStatus.ACTIVE);
        UUID shopB = UUID.randomUUID();

        List<PriceSnapshot> snapshots = catalog.getPriceSnapshots(TenantId.of(shopB),
                idList(productOfA, UUID.randomUUID()));

        assertThat(snapshots).isEmpty();
    }

    private UUID saveProduct(UUID tenantId, String name, long priceMinor, ProductStatus status) {
        String slug = "p-" + UUID.randomUUID().toString().substring(0, 8);
        ProductEntity product = ProductEntity.newProduct(tenantId, null, name, slug, null,
                Money.of(priceMinor, "KES"), null, status);
        products.saveAndFlush(product);
        return product.getId();
    }

    private static List<UUID> idList(UUID first, UUID second) {
        List<UUID> ids = new ArrayList<>();
        ids.add(first);
        ids.add(second);
        return ids;
    }

    private static PriceSnapshot findSnapshot(List<PriceSnapshot> snapshots, UUID productId) {
        for (PriceSnapshot snapshot : snapshots) {
            if (snapshot.productId().equals(productId)) {
                return snapshot;
            }
        }
        throw new AssertionError("No snapshot for " + productId);
    }

}
