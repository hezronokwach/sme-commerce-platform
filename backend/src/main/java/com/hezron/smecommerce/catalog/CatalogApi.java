package com.hezron.smecommerce.catalog;

import com.hezron.smecommerce.shared.TenantId;
import java.util.List;
import java.util.UUID;

/**
 * What other modules may ask of catalog. Ordering is the only caller so far (Guide 3.1, "public API for
 * ordering"). Implemented in catalog.internal; callers only see this interface and PriceSnapshot.
 */
public interface CatalogApi {

    /**
     * The current name and price of each product, read in one query. A product that doesn't exist in
     * this shop is simply missing from the answer; the caller decides what that means.
     */
    List<PriceSnapshot> getPriceSnapshots(TenantId tenantId, List<UUID> productIds);

}
