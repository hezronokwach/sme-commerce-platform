package com.hezron.smecommerce.inventory.internal.domain;

/** Why on_hand changed. Each change is one row in the stock history. */
public enum MovementReason {

    /** New stock arrived (a positive change). */
    RESTOCK,

    /** Sold through a paid order (a negative change). */
    SALE,

    /** A correction after counting the shelf (positive or negative). */
    ADJUSTMENT,

    /** A customer brought items back (a positive change). */
    RETURN

}
