package com.hezron.smecommerce.assistant.internal.domain;

/** Whether a document's chunks are in the vector store yet. */
public enum DocumentStatus {
    /** Saved; its chunks are being made (the embedding model is slow, so this runs after the request). */
    PROCESSING,
    /** Its chunks are searchable. */
    READY,
    /** Indexing went wrong; {@code error} says why. Saving the same text again tries again. */
    FAILED
}
