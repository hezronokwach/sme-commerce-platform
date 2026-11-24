package com.hezron.smecommerce.assistant.internal.web;

/** @param productsQueued how many products had new (or failed) text and are being indexed now */
record ReindexResponse(int productsQueued) {
}
