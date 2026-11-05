package com.hezron.smecommerce.inventory;

import com.hezron.smecommerce.shared.BaseException;
import java.util.UUID;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Fewer available than the order asks for. Public, so ordering can let it pass through (409) or tell
 * the customer how many are left.
 */
@Getter
public class InsufficientStockException extends BaseException {

    private final UUID productId;
    private final int available;

    public InsufficientStockException(UUID productId, int requested, int available) {
        super(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK", "Not enough in stock",
                "Only " + available + " available, but " + requested + " were asked for.");
        this.productId = productId;
        this.available = available;
        getBody().setProperty("productId", productId);
        getBody().setProperty("available", available);
    }

}
