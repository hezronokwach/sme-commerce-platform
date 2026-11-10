package com.hezron.smecommerce.ordering;

import com.hezron.smecommerce.shared.TenantId;
import java.util.Optional;
import java.util.UUID;

/**
 * What other modules may ask of ordering. Notification uses it to check who may watch an order live
 * and to tell a new watcher where the order is now.
 * <p>
 * Every method answers "empty" both when the order doesn't exist and when the caller may not see it,
 * so a caller can never learn that another shop's order exists.
 */
public interface OrderingApi {

    /**
     * The order a customer may see: the shop's web name, the order number and the private order-link
     * token they got when placing it. Empty if any of the three is wrong.
     */
    Optional<OrderStatusView> findOrderForCustomer(String shopSlug, String orderNumber, String orderToken);

    /** One of this shop's orders, for staff (the shop comes from their login). Empty for another shop's. */
    Optional<OrderStatusView> findOrder(TenantId tenantId, UUID orderId);

}
