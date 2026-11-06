package com.hezron.smecommerce.ordering.internal.application;

import com.hezron.smecommerce.ordering.internal.domain.PaymentMethod;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.NonNull;

/**
 * Input for placing an order, as the customer filled in the checkout form. There are no prices in here
 * on purpose: prices always come from the server (catalog), never from the browser.
 *
 * @param customerPhone as typed, e.g. "0712 345 678"; it is turned into 2547XXXXXXXX when placing
 */
public record PlaceOrderCommand(String customerName, String customerPhone, PaymentMethod paymentMethod,
        List<OrderLineCommand> lines) {

    /** One cart line: this many of this product. */
    public record OrderLineCommand(UUID productId, int quantity) {
    }

    /** Keeps the full phone number out of logs and error messages if the command is ever printed. */
    @Override
    public @NonNull String toString() {
        return "PlaceOrderCommand[customerName=" + customerName + ", customerPhone=***, paymentMethod="
                + paymentMethod + ", lines=" + lines + "]";
    }

}
