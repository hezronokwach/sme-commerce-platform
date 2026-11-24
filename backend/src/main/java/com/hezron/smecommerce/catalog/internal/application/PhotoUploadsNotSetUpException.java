package com.hezron.smecommerce.catalog.internal.application;

import com.hezron.smecommerce.shared.BaseException;
import org.springframework.http.HttpStatus;

/** The Cloudinary keys aren't set, so photos can't be uploaded yet. Returns 503. */
public class PhotoUploadsNotSetUpException extends BaseException {

    public PhotoUploadsNotSetUpException() {
        super(HttpStatus.SERVICE_UNAVAILABLE, "PHOTO_UPLOADS_NOT_SET_UP", "Photo uploads are not set up",
                "Photo uploads aren't set up on this server yet. Add the Cloudinary keys to turn them on.");
    }

}
