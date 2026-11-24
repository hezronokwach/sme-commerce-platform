package com.hezron.smecommerce.assistant.internal.application;

import com.hezron.smecommerce.shared.BaseException;
import org.springframework.http.HttpStatus;

/**
 * No document with this id in the caller's shop. Another shop's id gives the same 404, so a caller can't
 * learn that it exists elsewhere.
 */
public class DocumentNotFoundException extends BaseException {

    public DocumentNotFoundException() {
        super(HttpStatus.NOT_FOUND, "DOCUMENT_NOT_FOUND", "Document not found",
                "There is no assistant document with this id in your shop.");
    }

}
