package com.hezron.smecommerce.assistant.internal.application;

import com.hezron.smecommerce.assistant.internal.persistence.DocumentEntity;
import java.time.Instant;
import java.util.UUID;

/** One document, as the admin sees it (without its full text). */
public record DocumentResult(UUID id, String title, String sourceType, UUID sourceId, String status,
        int chunkCount, String error, Instant createdAt, Instant updatedAt) {

    static DocumentResult fromEntity(DocumentEntity document) {
        return new DocumentResult(document.getId(), document.getTitle(), document.getSourceType().name(),
                document.getSourceId(), document.getStatus().name(), document.getChunkCount(), document.getError(),
                document.getCreatedAt(), document.getUpdatedAt());
    }

}
