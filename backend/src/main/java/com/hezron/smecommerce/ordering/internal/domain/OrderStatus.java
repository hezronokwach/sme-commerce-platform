package com.hezron.smecommerce.ordering.internal.domain;

/** Where an order is in its life. The allowed moves between them are in OrderEntity. */
public enum OrderStatus {

    /** Just made; not yet waiting for money. */
    CREATED,

    /** Stock is held and the customer has 15 minutes to pay. */
    PENDING_PAYMENT,

    /** The money arrived. */
    PAID,

    /** Staff handed the goods over. Final. */
    FULFILLED,

    /** Cancelled before payment (by the customer, staff, or a failed payment). Final. */
    CANCELLED,

    /** No payment within 15 minutes. A late payment can still move it to PAID. */
    EXPIRED

}
