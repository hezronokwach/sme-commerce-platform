package com.hezron.smecommerce.assistant.internal.web;

import com.hezron.smecommerce.assistant.internal.application.DocumentResult;
import java.time.Instant;
import java.util.UUID;

/**
 * One assistant document in the admin.
 *
 * @param sourceType UPLOAD or PRODUCT
 * @param sourceId   the product's id for PRODUCT documents, else null
 * @param status     PROCESSING, READY or FAILED
 * @param error      why indexing failed, only for FAILED
 */
record DocumentResponse(UUID id, String title, String sourceType, UUID sourceId, String status, int chunkCount,
        String error, Instant createdAt, Instant updatedAt) {

    static DocumentResponse fromResult(DocumentResult result) {
        return new DocumentResponse(result.id(), result.title(), result.sourceType(), result.sourceId(),
                result.status(), result.chunkCount(), result.error(), result.createdAt(), result.updatedAt());
    }

}
