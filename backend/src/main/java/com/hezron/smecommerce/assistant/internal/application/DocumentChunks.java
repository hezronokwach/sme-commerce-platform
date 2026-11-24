package com.hezron.smecommerce.assistant.internal.application;

import java.util.List;
import java.util.UUID;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Component;

/**
 * The assistant's chunks in the vector store (assistant.vector_store, through Spring AI's PgVectorStore).
 * The one place that knows the metadata keys, so every write and delete uses the same ones.
 */
@Component
class DocumentChunks {

    /** Metadata key: which shop a chunk belongs to. Every search filters on it. */
    static final String TENANT_ID = "tenant_id";
    /** Metadata key: which document a chunk was cut from. */
    static final String DOCUMENT_ID = "document_id";

    private final VectorStore vectorStore;

    DocumentChunks(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /** Adds the chunks. The vector store asks the embedding model for each chunk's numbers first. */
    void add(List<Document> chunks) {
        if (chunks.isEmpty()) {
            return;
        }
        vectorStore.add(chunks);
    }

    /** Removes every chunk of one document. Document ids are random UUIDs, so this never reaches another shop. */
    void removeFor(UUID documentId) {
        Filter.Expression ofThisDocument = new FilterExpressionBuilder().eq(DOCUMENT_ID, documentId.toString()).build();
        vectorStore.delete(ofThisDocument);
    }

    void removeByIds(List<String> chunkIds) {
        if (chunkIds.isEmpty()) {
            return;
        }
        vectorStore.delete(chunkIds);
    }

}
