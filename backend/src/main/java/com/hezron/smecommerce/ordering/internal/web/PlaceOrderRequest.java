package com.hezron.smecommerce.ordering.internal.web;

import com.hezron.smecommerce.ordering.internal.application.PlaceOrderCommand;
import com.hezron.smecommerce.ordering.internal.application.PlaceOrderCommand.OrderLineCommand;
import com.hezron.smecommerce.ordering.internal.domain.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.NonNull;

/**
 * JSON body of {@code POST /api/store/{slug}/orders}:
 *
 * <pre>
 * {"customerName": "Amina", "customerPhone": "0712 345 678", "paymentMethod": "MPESA",
 *  "lines": [{"productId": "...", "quantity": 2}]}
 * </pre>
 *
 * There is no price field: any price the browser adds is simply ignored (Jackson skips unknown fields).
 */
record PlaceOrderRequest(
        @NotBlank @Size(max = 120) String customerName,
        @NotBlank @Size(max = 20) String customerPhone,
        @NotNull PaymentMethod paymentMethod,
        // @Valid: also check each line's own rules below.
        @NotEmpty @Size(max = 50) List<@Valid @NotNull LineRequest> lines) {

    /** One cart line. */
    record LineRequest(@NotNull UUID productId, @NotNull @Min(1) @Max(99) Integer quantity) {
    }

    PlaceOrderCommand toCommand() {
        List<OrderLineCommand> commandLines = new ArrayList<>();
        for (LineRequest line : lines) {
            commandLines.add(new OrderLineCommand(line.productId(), line.quantity()));
        }
        return new PlaceOrderCommand(customerName, customerPhone, paymentMethod, commandLines);
    }

    /** Keeps the full phone number out of logs if the request is ever printed. */
    @Override
    public @NonNull String toString() {
        return "PlaceOrderRequest[customerName=" + customerName + ", customerPhone=***, paymentMethod="
                + paymentMethod + ", lines=" + lines + "]";
    }

}
