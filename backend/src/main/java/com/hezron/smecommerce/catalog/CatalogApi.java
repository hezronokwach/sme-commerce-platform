package com.hezron.smecommerce.catalog;

import com.hezron.smecommerce.shared.TenantId;
import java.util.List;
import java.util.UUID;

/**
 * What other modules may ask of catalog: ordering (prices, Guide 3.1) and the assistant (what the shop
 * sells). Implemented in catalog.internal; callers only see this interface and its small records.
 */
public interface CatalogApi {

    /**
     * The current name and price of each product, read in one query. A product that doesn't exist in
     * this shop is simply missing from the answer; the caller decides what that means.
     */
    List<PriceSnapshot> getPriceSnapshots(TenantId tenantId, List<UUID> productIds);

    /**
     * Every product the shop sells right now (ACTIVE), for the assistant to learn about products that
     * existed before it listened to catalog's events.
     */
    List<ActiveProductView> listActiveProducts(TenantId tenantId);

    /**
     * The open shop behind a storefront's web name. The assistant asks here because its only allowed
     * dependency for shop data is catalog (ADR 0016).
     *
     * @throws com.hezron.smecommerce.tenant.ShopNotFoundException if there is no such open shop
     */
    StorefrontShop findStorefrontShop(String slug);

}
