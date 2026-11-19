package com.hezron.smecommerce.assistant.internal.application;

import com.hezron.smecommerce.shared.BaseException;
import org.springframework.http.HttpStatus;

/** A product's document follows the product: archive the product instead. Returns 409. */
public class ProductDocumentNotDeletableException extends BaseException {

    public ProductDocumentNotDeletableException() {
        super(HttpStatus.CONFLICT, "PRODUCT_DOCUMENT", "This document describes a product",
                "It follows the product: archive the product to remove it from the assistant.");
    }

}
