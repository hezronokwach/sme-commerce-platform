package com.hezron.smecommerce.ordering.internal.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Persistable;

/**
 * Row in {@code ordering.order_lines}: one product in an order, with its name and price copied at the
 * moment of purchase. Never changed after saving.
 */
@Entity
@Table(schema = "ordering", name = "order_lines")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA
public class OrderLineEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;

    @Column(name = "product_name", nullable = false, updatable = false)
    private String productName;

    /** The server's price at purchase, in cents. Never the price a browser sent. */
    @Column(name = "unit_price_minor", nullable = false, updatable = false)
    private long unitPriceMinor;

    @Column(nullable = false, updatable = false)
    private int quantity;

    @Column(name = "line_total_minor", nullable = false, updatable = false)
    private long lineTotalMinor;

    @Transient
    @Getter(AccessLevel.NONE)
    private boolean isNew = true;

    /** The line total is always worked out here, so it can't disagree with price times quantity. */
    public static OrderLineEntity newLine(UUID tenantId, UUID orderId, UUID productId, String productName,
            long unitPriceMinor, int quantity) {
        OrderLineEntity line = new OrderLineEntity();
        line.id = UUID.randomUUID();
        line.tenantId = tenantId;
        line.orderId = orderId;
        line.productId = productId;
        line.productName = productName;
        line.unitPriceMinor = unitPriceMinor;
        line.quantity = quantity;
        line.lineTotalMinor = unitPriceMinor * quantity;
        return line;
    }

    /** Written out only to say it's never null: every line gets its id in newLine. */
    @Override
    public @NonNull UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }

}
