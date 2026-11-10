package com.hezron.smecommerce.ordering.internal.application;

import com.hezron.smecommerce.ordering.OrderStatusView;
import com.hezron.smecommerce.ordering.OrderingApi;
import com.hezron.smecommerce.ordering.internal.persistence.OrderEntity;
import com.hezron.smecommerce.ordering.internal.persistence.OrderRepository;
import com.hezron.smecommerce.shared.TenantId;
import com.hezron.smecommerce.tenant.ShopNotFoundException;
import com.hezron.smecommerce.tenant.TenantApi;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ordering's answers to other modules (OrderingApi). Read-only. */
@Service
class OrderLookupService implements OrderingApi {

    private final TenantApi tenantApi;
    private final OrderRepository orders;

    OrderLookupService(TenantApi tenantApi, OrderRepository orders) {
        this.tenantApi = tenantApi;
        this.orders = orders;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OrderStatusView> findOrderForCustomer(String shopSlug, String orderNumber, String orderToken) {
        TenantId tenantId;
        try {
            tenantId = tenantApi.findActiveTenantIdBySlug(shopSlug);
        } catch (ShopNotFoundException unknownShop) {
            return Optional.empty();
        }

        Optional<OrderEntity> found = orders.findByTenantIdAndOrderNumber(tenantId.value(), orderNumber);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        OrderEntity order = found.get();

        // The private order-link token proves the order is theirs; only its hash is stored.
        if (orderToken == null || !OrderAccessTokens.matches(orderToken, order.getAccessTokenHash())) {
            return Optional.empty();
        }
        return Optional.of(viewOf(order));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OrderStatusView> findOrder(TenantId tenantId, UUID orderId) {
        Optional<OrderEntity> found = orders.findByIdAndTenantId(orderId, tenantId.value());
        if (found.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(viewOf(found.get()));
    }

    private static OrderStatusView viewOf(OrderEntity order) {
        return new OrderStatusView(TenantId.of(order.getTenantId()), order.getId(), order.getOrderNumber(),
                order.getStatus().name());
    }

}
