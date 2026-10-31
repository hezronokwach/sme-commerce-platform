package com.hezron.smecommerce.tenant;

import com.hezron.smecommerce.shared.BaseException;
import org.springframework.http.HttpStatus;

/**
 * Another shop already uses this web name. Returns 409, so a caller like sign-up can let it pass
 * straight through to the client.
 */
public class SlugTakenException extends BaseException {

    public SlugTakenException(String slug) {
        super(HttpStatus.CONFLICT, "SLUG_TAKEN", "Shop web name taken",
                "The shop web name '" + slug + "' is already taken.");
    }

}
