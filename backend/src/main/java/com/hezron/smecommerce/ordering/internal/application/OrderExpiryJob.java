package com.hezron.smecommerce.ordering.internal.application;

import com.hezron.smecommerce.ordering.internal.domain.OrderStatus;
import com.hezron.smecommerce.ordering.internal.persistence.OrderEntity;
import com.hezron.smecommerce.ordering.internal.persistence.OrderRepository;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Ends unpaid orders whose time to pay ran out (Guide 3.5), so their held stock goes back on sale.
 * <p>
 * Every minute it takes up to 100 PENDING_PAYMENT orders past their deadline and hands each one to
 * OrderExpirySteps, which decides under a lock (and skips the order while a payment is still under way).
 * One order that fails doesn't stop the others; it is simply picked up again next time.
 * fixedDelay means a run never starts while the previous one is still going.
 */
@Component
class OrderExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(OrderExpiryJob.class);
    private static final int BATCH_SIZE = 100;

    private final OrderRepository orders;
    private final OrderExpirySteps steps;

    OrderExpiryJob(OrderRepository orders, OrderExpirySteps steps) {
        this.orders = orders;
        this.steps = steps;
    }

    @Scheduled(fixedDelayString = "${app.ordering.expiry.every:60s}")
    void expireUnpaidOrders() {
        Instant now = Instant.now();
        List<OrderEntity> late = orders.findPastDeadline(OrderStatus.PENDING_PAYMENT, now,
                PageRequest.of(0, BATCH_SIZE));
        for (OrderEntity order : late) {
            try {
                steps.expireIfUnpaid(order.getTenantId(), order.getId(), now);
            } catch (RuntimeException e) {
                log.warn("Could not expire order {}; will try again", order.getId(), e);
            }
        }
    }

}
