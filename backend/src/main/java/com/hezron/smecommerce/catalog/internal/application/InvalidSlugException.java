package com.hezron.smecommerce.catalog.internal.application;

import com.hezron.smecommerce.shared.BaseException;
import org.springframework.http.HttpStatus;

/** No usable web name: the name has no letters or digits, or the given slug breaks the format. 400. */
public class InvalidSlugException extends BaseException {

    public InvalidSlugException() {
        super(HttpStatus.BAD_REQUEST, "SLUG_INVALID", "Invalid web name",
                "Use a name with letters or digits; a web name may only have lowercase letters, digits and single hyphens.");
    }

}
