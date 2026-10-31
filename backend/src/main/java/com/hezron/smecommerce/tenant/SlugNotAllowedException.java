package com.hezron.smecommerce.tenant;

import com.hezron.smecommerce.shared.BaseException;
import org.springframework.http.HttpStatus;

/** The web name has the wrong format or is reserved by the platform (admin, api, ...). Returns 400. */
public class SlugNotAllowedException extends BaseException {

    public SlugNotAllowedException(String slug, String reason) {
        super(HttpStatus.BAD_REQUEST, "SLUG_NOT_ALLOWED", "Shop web name not allowed",
                "The shop web name '" + slug + "' can't be used: " + reason);
    }

}
