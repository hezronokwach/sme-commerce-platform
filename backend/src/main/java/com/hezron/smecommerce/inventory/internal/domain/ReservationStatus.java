package com.hezron.smecommerce.inventory.internal.domain;

/** Where a hold for an unpaid order stands. */
public enum ReservationStatus {

    /** Items set aside while the customer pays. They don't count as available. */
    HELD,

    /** Paid: the items are sold and left on_hand. */
    CONFIRMED,

    /** Cancelled or expired: the items went back to available. */
    RELEASED

}
