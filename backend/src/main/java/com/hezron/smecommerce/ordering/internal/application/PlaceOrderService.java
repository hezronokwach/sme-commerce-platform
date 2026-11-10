package com.hezron.smecommerce.ordering.internal.application;

import com.hezron.smecommerce.catalog.CatalogApi;
import com.hezron.smecommerce.catalog.PriceSnapshot;
import com.hezron.smecommerce.identity.IdentityApi;
import com.hezron.smecommerce.inventory.InventoryApi;
import com.hezron.smecommerce.inventory.ReservationLine;
import com.hezron.smecommerce.ordering.OrderPlacedEvent;
import com.hezron.smecommerce.ordering.internal.application.PlaceOrderCommand.OrderLineCommand;
import com.hezron.smecommerce.ordering.internal.domain.ActorType;
import com.hezron.smecommerce.ordering.internal.domain.OrderStatus;
import com.hezron.smecommerce.ordering.internal.persistence.OrderEntity;
import com.hezron.smecommerce.ordering.internal.persistence.OrderLineEntity;
import com.hezron.smecommerce.ordering.internal.persistence.OrderLineRepository;
import com.hezron.smecommerce.ordering.internal.persistence.OrderRepository;
import com.hezron.smecommerce.ordering.internal.persistence.OrderStatusHistoryEntity;
import com.hezron.smecommerce.payments.PaymentProviderName;
import com.hezron.smecommerce.payments.PaymentsApi;
import com.hezron.smecommerce.payments.RequestPaymentCommand;
import com.hezron.smecommerce.shared.PhoneNumber;
import com.hezron.smecommerce.shared.TenantId;
import com.hezron.smecommerce.tenant.TenantApi;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Placing an order from the storefront (Guide 4.1, workflow W6 steps 1 to 5). It uses five other modules,
 * each only through its public API: tenant (which shop), catalog (prices), identity (the customer),
 * inventory (holding the stock) and payments (asking for the money).
 */
@Service
public class PlaceOrderService {

    /** How long the customer has to pay. Inventory holds the stock for the same time. */
    static final Duration TIME_TO_PAY = Duration.ofMinutes(15);

    private final TenantApi tenantApi;
    private final CatalogApi catalogApi;
    private final IdentityApi identityApi;
    private final InventoryApi inventoryApi;
    private final PaymentsApi paymentsApi;
    private final OrderNumberService orderNumbers;
    private final OrderRepository orders;
    private final OrderLineRepository orderLines;
    private final OrderStatusRecorder statusRecorder;
    private final ApplicationEventPublisher events;

    PlaceOrderService(TenantApi tenantApi, CatalogApi catalogApi, IdentityApi identityApi, InventoryApi inventoryApi,
            PaymentsApi paymentsApi, OrderNumberService orderNumbers, OrderRepository orders,
            OrderLineRepository orderLines, OrderStatusRecorder statusRecorder, ApplicationEventPublisher events) {
        this.tenantApi = tenantApi;
        this.catalogApi = catalogApi;
        this.identityApi = identityApi;
        this.inventoryApi = inventoryApi;
        this.paymentsApi = paymentsApi;
        this.orderNumbers = orderNumbers;
        this.orders = orders;
        this.orderLines = orderLines;
        this.statusRecorder = statusRecorder;
        this.events = events;
    }

    /**
     * One transaction for everything: the customer, the order number, the order, its lines, its history
     * and the stock hold. If anything fails (most often: not enough stock), nothing at all is saved, not
     * even half an order, and the order number is given back.
     *
     * @throws com.hezron.smecommerce.tenant.ShopNotFoundException    if no open shop has this web name
     * @throws InvalidOrderException                                   if the same product is on two lines
     * @throws com.hezron.smecommerce.shared.InvalidPhoneNumberException if the phone isn't a Kenyan mobile
     * @throws ProductUnavailableException                             if a product is unknown, a draft or archived
     * @throws com.hezron.smecommerce.inventory.InsufficientStockException if a product has too few left
     */
    @Transactional
    public PlaceOrderResult placeOrder(String shopSlug, PlaceOrderCommand command) {
        // 1. Which shop, from the web address.
        TenantId tenantId = tenantApi.findActiveTenantIdBySlug(shopSlug);
        UUID tenant = tenantId.value();

        // 2. Check what the customer sent.
        checkNoProductTwice(command.lines());
        PhoneNumber phone = PhoneNumber.parse(command.customerPhone());

        // 3. The server's current prices. Whatever price the browser may have sent is never read.
        Map<UUID, PriceSnapshot> prices = currentPrices(tenantId, command.lines());

        // 4. The customer: found by phone within this shop, or created on their first order.
        String customerName = command.customerName().strip();
        UUID customerId = identityApi.findOrCreateCustomer(tenantId, customerName, phone);

        // 5. The order, its lines and its first two history rows.
        String orderNumber = orderNumbers.nextOrderNumber(tenant, shopSlug.strip().toLowerCase(Locale.ROOT));
        String accessToken = OrderAccessTokens.newToken();
        long totalMinor = totalOf(command.lines(), prices);
        String currency = currencyOf(prices);

        OrderEntity order = OrderEntity.newOrder(tenant, orderNumber, customerId, customerName, phone.value(),
                command.paymentMethod(), totalMinor, currency, OrderAccessTokens.hash(accessToken));
        orders.saveAndFlush(order);
        statusRecorder.record(order, OrderStatusHistoryEntity.newEntry(tenant, order.getId(), null, OrderStatus.CREATED,
                "Order placed", ActorType.CUSTOMER, customerId));
        saveLines(order, command.lines(), prices);

        Instant expiresAt = Instant.now().plus(TIME_TO_PAY);
        statusRecorder.record(order, order.placeAwaitingPayment(expiresAt));

        // 6. Hold the stock. Not enough? InsufficientStockException rolls back everything above.
        inventoryApi.reserve(tenantId, order.getId(), reservationLines(command.lines()));

        // 7. Ask for the money. Payments only saves the attempt here, in this same transaction; it asks
        //    the provider (PIN prompt on the phone) after we commit, so a rolled-back order never
        //    charges anyone. The two PaymentMethod/PaymentProviderName enums share their names.
        PaymentProviderName provider = PaymentProviderName.valueOf(command.paymentMethod().name());
        paymentsApi.requestPayment(new RequestPaymentCommand(tenantId, order.getId(), orderNumber, totalMinor,
                currency, provider, phone.value()));

        // 8. Tell the rest of the system (notification will show it in the admin's live list).
        events.publishEvent(new OrderPlacedEvent(tenantId, order.getId(), orderNumber, totalMinor, currency));

        return new PlaceOrderResult(order.getId(), orderNumber, accessToken, order.getStatus(), totalMinor,
                currency, expiresAt);
    }

    private static void checkNoProductTwice(List<OrderLineCommand> lines) {
        Set<UUID> seen = new HashSet<>();
        for (OrderLineCommand line : lines) {
            boolean isNew = seen.add(line.productId());
            if (!isNew) {
                throw new InvalidOrderException("Each product may appear only once. Change the quantity instead.");
            }
        }
    }

    /** Asks catalog for every product in one call, and refuses the order if any can't be bought. */
    private Map<UUID, PriceSnapshot> currentPrices(TenantId tenantId, List<OrderLineCommand> lines) {
        List<UUID> productIds = new ArrayList<>();
        for (OrderLineCommand line : lines) {
            productIds.add(line.productId());
        }

        Map<UUID, PriceSnapshot> prices = new HashMap<>();
        for (PriceSnapshot snapshot : catalogApi.getPriceSnapshots(tenantId, productIds)) {
            prices.put(snapshot.productId(), snapshot);
        }

        for (UUID productId : productIds) {
            PriceSnapshot snapshot = prices.get(productId);
            // Missing means: not a product of this shop (or not a product at all).
            if (snapshot == null || !snapshot.active()) {
                throw new ProductUnavailableException(productId);
            }
        }
        return prices;
    }

    private static long totalOf(List<OrderLineCommand> lines, Map<UUID, PriceSnapshot> prices) {
        long total = 0;
        for (OrderLineCommand line : lines) {
            total = total + prices.get(line.productId()).priceMinor() * line.quantity();
        }
        return total;
    }

    /** Every product of a shop has the shop's currency (KES for now), so the first one tells us. */
    private static String currencyOf(Map<UUID, PriceSnapshot> prices) {
        for (PriceSnapshot snapshot : prices.values()) {
            return snapshot.currency();
        }
        throw new IllegalStateException("An order always has at least one line");
    }

    private void saveLines(OrderEntity order, List<OrderLineCommand> lines, Map<UUID, PriceSnapshot> prices) {
        for (OrderLineCommand line : lines) {
            PriceSnapshot snapshot = prices.get(line.productId());
            orderLines.save(OrderLineEntity.newLine(order.getTenantId(), order.getId(), line.productId(),
                    snapshot.name(), snapshot.priceMinor(), line.quantity()));
        }
    }

    private static List<ReservationLine> reservationLines(List<OrderLineCommand> lines) {
        List<ReservationLine> reservation = new ArrayList<>();
        for (OrderLineCommand line : lines) {
            reservation.add(new ReservationLine(line.productId(), line.quantity()));
        }
        return reservation;
    }

}
