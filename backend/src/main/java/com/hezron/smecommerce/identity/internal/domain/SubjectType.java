package com.hezron.smecommerce.identity.internal.domain;

/** Who a token belongs to: a staff user of the admin, or a customer of the storefront. */
public enum SubjectType {

    STAFF,

    /** Storefront accounts. Not issued yet; they come with customer accounts. */
    CUSTOMER

}
