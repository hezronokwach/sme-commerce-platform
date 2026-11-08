package com.hezron.smecommerce.ordering.internal.application;

import com.hezron.smecommerce.ordering.internal.application.OrderDetailResult.LineResult;
import com.hezron.smecommerce.ordering.internal.application.OrderDetailResult.StatusChangeResult;
import com.hezron.smecommerce.ordering.internal.domain.OrderStatus;
import com.hezron.smecommerce.ordering.internal.persistence.OrderEntity;
import com.hezron.smecommerce.ordering.internal.persistence.OrderLineEntity;
import com.hezron.smecommerce.ordering.internal.persistence.OrderLineRepository;
import com.hezron.smecommerce.ordering.internal.persistence.OrderRepository;
import com.hezron.smecommerce.ordering.internal.persistence.OrderStatusHistoryEntity;
import com.hezron.smecommerce.ordering.internal.persistence.OrderStatusHistoryRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reading a shop's orders for the admin (Guide 4.5). Every method takes the caller's {@code tenantId}
 * and passes it into each query, so a shop only ever sees its own orders.
 */
@Service
public class OrderQueryService {

    /** The guide's limit: a page never has more than 50 orders, whatever the caller asks for. */
    static final int MAX_PAGE_SIZE = 50;

    private final OrderRepository orders;
    private final OrderLineRepository orderLines;
    private final OrderStatusHistoryRepository history;

    OrderQueryService(OrderRepository orders, OrderLineRepository orderLines, OrderStatusHistoryRepository history) {
        this.orders = orders;
        this.orderLines = orderLines;
        this.history = history;
    }

    /**
     * One page of the shop's orders, newest first. With a status, only orders in that status.
     *
     * @param page counting from 0; a negative number is treated as 0
     * @param size 1 to 50; anything outside is brought back into that range
     */
    @Transactional(readOnly = true)
    public OrderPageResult list(UUID tenantId, OrderStatus status, int page, int size) {
        int safePage = page;
        if (safePage < 0) {
            safePage = 0;
        }
        int safeSize = size;
        if (safeSize < 1) {
            safeSize = 1;
        }
        if (safeSize > MAX_PAGE_SIZE) {
            safeSize = MAX_PAGE_SIZE;
        }

        // Which page, how big, and newest first.
        PageRequest pageRequest = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<OrderEntity> found;
        if (status == null) {
            found = orders.findAllByTenantId(tenantId, pageRequest);
        } else {
            found = orders.findAllByTenantIdAndStatus(tenantId, status, pageRequest);
        }

        List<OrderSummaryResult> items = new ArrayList<>();
        for (OrderEntity order : found.getContent()) {
            items.add(OrderSummaryResult.fromEntity(order));
        }
        return new OrderPageResult(items, safePage, safeSize, found.getTotalElements());
    }

    /** @throws OrderNotFoundException if the id is unknown or belongs to another shop */
    @Transactional(readOnly = true)
    public OrderDetailResult get(UUID tenantId, UUID orderId) {
        Optional<OrderEntity> found = orders.findByIdAndTenantId(orderId, tenantId);
        if (found.isEmpty()) {
            throw new OrderNotFoundException();
        }
        OrderEntity order = found.get();

        List<LineResult> lines = new ArrayList<>();
        for (OrderLineEntity line : orderLines.findAllByOrderIdAndTenantId(orderId, tenantId)) {
            lines.add(new LineResult(line.getProductId(), line.getProductName(), line.getUnitPriceMinor(),
                    line.getQuantity(), line.getLineTotalMinor()));
        }

        List<StatusChangeResult> changes = new ArrayList<>();
        for (OrderStatusHistoryEntity entry : history.findAllByOrderIdAndTenantIdOrderByCreatedAtAsc(orderId, tenantId)) {
            changes.add(new StatusChangeResult(entry.getFromStatus(), entry.getToStatus(), entry.getReason(),
                    entry.getActorType(), entry.getCreatedAt()));
        }

        return new OrderDetailResult(order.getId(), order.getOrderNumber(), order.getCustomerName(),
                order.getCustomerPhone(), order.getStatus(), order.getPaymentMethod(), order.getTotalMinor(),
                order.getCurrency(), order.getExpiresAt(), order.getCreatedAt(), lines, changes);
    }

}
