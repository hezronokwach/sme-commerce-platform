package com.hezron.smecommerce.identity.internal.domain;

/** Whether a staff user can log in. Only {@link #ACTIVE} users get tokens. */
public enum StaffStatus {

    /** Can log in. Owners start here; invited staff get here when they accept. */
    ACTIVE,

    /** Invited by the owner but hasn't set a password yet. */
    INVITED,

    /** Switched off by the owner. Kept, never deleted, so audit history still points at a real user. */
    DISABLED

}
