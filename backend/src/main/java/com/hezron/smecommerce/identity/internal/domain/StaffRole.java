package com.hezron.smecommerce.identity.internal.domain;

/**
 * What a staff user may do in the admin. Stored as text in {@code staff_users.role} and carried in
 * the access token's {@code role} claim, where Spring turns it into {@code ROLE_OWNER} / {@code ROLE_STAFF}.
 */
public enum StaffRole {

    /** Created the shop at sign-up. Manages settings, staff and everything staff can do. */
    OWNER,

    /** Invited by the owner. Manages products and orders, not settings. */
    STAFF

}
