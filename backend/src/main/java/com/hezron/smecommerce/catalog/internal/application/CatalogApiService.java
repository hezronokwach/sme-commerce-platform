package com.hezron.smecommerce.catalog.internal.application;

import com.hezron.smecommerce.catalog.CatalogApi;
import com.hezron.smecommerce.catalog.PriceSnapshot;
import com.hezron.smecommerce.catalog.internal.domain.ProductStatus;
import com.hezron.smecommerce.catalog.internal.persistence.ProductEntity;
import com.hezron.smecommerce.catalog.internal.persistence.ProductRepository;
import com.hezron.smecommerce.shared.TenantId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The only implementation of CatalogApi. Package-private: other modules see the interface only. */
@Service
class CatalogApiService implements CatalogApi {

    private final ProductRepository products;

    CatalogApiService(ProductRepository products) {
        this.products = products;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PriceSnapshot> getPriceSnapshots(TenantId tenantId, List<UUID> productIds) {
        // One query for all the products: WHERE tenant_id = ? AND id IN (?, ?, ...), not one query each.
        List<ProductEntity> found = products.findAllByTenantIdAndIdIn(tenantId.value(), productIds);

        List<PriceSnapshot> snapshots = new ArrayList<>();
        for (ProductEntity product : found) {
            boolean active = product.getStatus() == ProductStatus.ACTIVE;
            snapshots.add(new PriceSnapshot(product.getId(), product.getName(), product.getPriceMinor(),
                    product.getCurrency(), active));
        }
        return snapshots;
    }

}
