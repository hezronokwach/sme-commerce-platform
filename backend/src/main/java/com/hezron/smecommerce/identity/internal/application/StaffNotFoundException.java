package com.hezron.smecommerce.identity.internal.application;

import com.hezron.smecommerce.shared.BaseException;
import org.springframework.http.HttpStatus;

/**
 * No staff user with this id in the caller's shop. Also what another shop's user id gives: 404, not
 * 403, so a caller can't even learn that the id exists elsewhere.
 */
public class StaffNotFoundException extends BaseException {

    public StaffNotFoundException() {
        super(HttpStatus.NOT_FOUND, "STAFF_NOT_FOUND", "Staff member not found",
                "There is no staff member with this id in your shop.");
    }

}
