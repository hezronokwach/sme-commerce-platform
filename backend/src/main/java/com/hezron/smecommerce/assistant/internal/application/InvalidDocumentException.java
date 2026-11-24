package com.hezron.smecommerce.assistant.internal.application;

import com.hezron.smecommerce.shared.BaseException;
import org.springframework.http.HttpStatus;

/** An upload we can't use (wrong type, too large, empty, not UTF-8, no title). Returns 400. */
public class InvalidDocumentException extends BaseException {

    public InvalidDocumentException(String detail) {
        super(HttpStatus.BAD_REQUEST, "INVALID_DOCUMENT", "Document can't be used", detail);
    }

}
