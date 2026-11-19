package com.hezron.smecommerce.assistant.internal.application;

import com.hezron.smecommerce.assistant.internal.domain.DocumentStatus;
import com.hezron.smecommerce.assistant.internal.persistence.DocumentEntity;
import com.hezron.smecommerce.assistant.internal.persistence.DocumentRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The two short database steps around indexing, each with its own transaction, so that NO transaction is
 * open while the embedding model is asked (the same reasoning as PaymentStartService):
 *
 * <pre>
 *   loadJob        (transaction 1: read the document's text)
 *   chunk + embed  (no transaction: Gemini may take seconds)
 *   finish / fail  (transaction 2: write how it went)
 * </pre>
 */
@Service
class IndexingSteps {

    private final DocumentRepository documents;

    IndexingSteps(DocumentRepository documents) {
        this.documents = documents;
    }

    /** What to index. Empty if the document is gone or not waiting (already done, a repeated event). */
    @Transactional(readOnly = true)
    public Optional<IndexJob> loadJob(UUID tenantId, UUID documentId) {
        Optional<DocumentEntity> found = documents.findByIdAndTenantId(documentId, tenantId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        DocumentEntity document = found.get();
        if (document.getStatus() != DocumentStatus.PROCESSING) {
            return Optional.empty();
        }
        return Optional.of(new IndexJob(document.getTenantId(), document.getId(), document.getTitle(),
                document.getSourceType().name(), document.getContent(), document.getContentHash()));
    }

    /**
     * The chunks are in. Only counts if the text is still the one we indexed: if it changed meanwhile, a
     * newer run is on its way and this one's chunks are stale.
     *
     * @return false if the text changed meanwhile (the caller then removes its own chunks)
     */
    @Transactional
    public boolean finish(IndexJob job, int chunkCount) {
        Optional<DocumentEntity> found = documents.lockByIdAndTenantId(job.documentId(), job.tenantId());
        if (found.isEmpty()) {
            return false; // deleted while we were indexing
        }
        DocumentEntity document = found.get();
        if (!document.getContentHash().equals(job.contentHash())) {
            return false;
        }
        document.markReady(chunkCount);
        return true;
    }

    @Transactional
    public void fail(IndexJob job, String error) {
        Optional<DocumentEntity> found = documents.lockByIdAndTenantId(job.documentId(), job.tenantId());
        if (found.isEmpty()) {
            return;
        }
        DocumentEntity document = found.get();
        if (!document.getContentHash().equals(job.contentHash())) {
            return; // a newer text is being indexed; its own run decides
        }
        document.markFailed(error);
    }

    /** One document's text, read in transaction 1 and used without a transaction afterwards. */
    record IndexJob(UUID tenantId, UUID documentId, String title, String sourceType, String content,
            String contentHash) {
    }

}
