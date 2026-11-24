package com.hezron.smecommerce.assistant.internal.application;

import java.util.UUID;

/**
 * Inside the assistant only: "this document has new text, make its chunks". Published in the same
 * transaction that saved the text; DocumentIndexer picks it up after that transaction commits.
 */
record DocumentIndexRequestedEvent(UUID tenantId, UUID documentId) {
}
