package com.hezron.smecommerce.assistant.internal.domain;

/** Where a document's text came from. */
public enum SourceType {
    /** A file the owner uploaded: a delivery policy, returns, opening hours, FAQs. */
    UPLOAD,
    /** Written by us from one catalog product; kept in step with the product. */
    PRODUCT,
    /** Reserved for policies written in the admin (later). */
    POLICY
}
