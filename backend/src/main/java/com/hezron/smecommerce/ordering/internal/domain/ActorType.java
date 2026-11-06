package com.hezron.smecommerce.ordering.internal.domain;

/** Who caused a status change, for the order's history. */
public enum ActorType {

    /** The app itself, e.g. the expiry job or a payment callback. */
    SYSTEM,

    /** A shop owner or staff member in the admin. */
    STAFF,

    /** The customer, e.g. cancelling their own order. */
    CUSTOMER

}
