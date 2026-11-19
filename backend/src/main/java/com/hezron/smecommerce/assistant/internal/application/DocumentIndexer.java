package com.hezron.smecommerce.assistant.internal.application;

import com.hezron.smecommerce.assistant.internal.application.IndexingSteps.IndexJob;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Turns a document's text into searchable chunks (Guide 7.1, "index").
 * <p>
 * Runs after the transaction that saved the text commits ({@code @TransactionalEventListener}), on
 * another thread ({@code @Async}), with no transaction of its own: asking the embedding model is a slow
 * HTTP call, and must not hold a database connection (the same reasoning as starting a payment).
 * <p>
 * Every chunk's metadata says which shop and which document it belongs to. These values come from our
 * own document row, never from a request. The search (next issue) always filters on tenant_id, so a shop's
 * assistant can only ever see its own chunks.
 */
@Component
class DocumentIndexer {

    private static final Logger log = LoggerFactory.getLogger(DocumentIndexer.class);

    private final IndexingSteps steps;
    private final DocumentChunks chunks;

    DocumentIndexer(IndexingSteps steps, DocumentChunks chunks) {
        this.steps = steps;
        this.chunks = chunks;
    }

    @Async
    @TransactionalEventListener
    void on(DocumentIndexRequestedEvent event) {
        Optional<IndexJob> found = steps.loadJob(event.tenantId(), event.documentId());
        if (found.isEmpty()) {
            return;
        }
        index(found.get());
    }

    private void index(IndexJob job) {
        List<Document> pieces = toPieces(job);
        try {
            // Delete, then add: when a policy changes, its old chunks must not linger and contradict it.
            chunks.removeFor(job.documentId());
            chunks.add(pieces); // the vector store asks the embedding model, then inserts the rows
        } catch (RuntimeException e) {
            // Only the message, and only ours to keep: provider errors never contain our key.
            log.warn("Indexing document {} failed: {}", job.documentId(), e.getMessage());
            chunks.removeFor(job.documentId());
            steps.fail(job, "Indexing failed: " + e.getMessage());
            return;
        }

        boolean stillCurrent = steps.finish(job, pieces.size());
        if (!stillCurrent) {
            // The text changed while we worked: our chunks describe an old version. Remove only ours;
            // the newer run puts the current ones in.
            List<String> ours = new ArrayList<>();
            for (Document piece : pieces) {
                ours.add(piece.getId());
            }
            chunks.removeByIds(ours);
        }
    }

    private static List<Document> toPieces(IndexJob job) {
        List<Document> pieces = new ArrayList<>();
        int index = 0;
        for (String text : TextChunker.chunk(job.content())) {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put(DocumentChunks.TENANT_ID, job.tenantId().toString());
            metadata.put(DocumentChunks.DOCUMENT_ID, job.documentId().toString());
            metadata.put("title", job.title());
            metadata.put("source_type", job.sourceType());
            metadata.put("chunk_index", index);
            pieces.add(Document.builder().id(UUID.randomUUID().toString()).text(text).metadata(metadata).build());
            index = index + 1;
        }
        return pieces;
    }

}
