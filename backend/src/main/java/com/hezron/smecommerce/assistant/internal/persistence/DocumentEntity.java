package com.hezron.smecommerce.assistant.internal.persistence;

import com.hezron.smecommerce.assistant.internal.domain.DocumentStatus;
import com.hezron.smecommerce.assistant.internal.domain.SourceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Persistable;

/**
 * Row in {@code assistant.documents}: one thing a shop's assistant knows (an uploaded policy or FAQ, or
 * one product). Its searchable chunks are rows in {@code assistant.vector_store}, found by document id.
 */
@Entity
@Table(schema = "assistant", name = "documents")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA
public class DocumentEntity implements Persistable<UUID> {

    /** The longest error message we keep (the column is varchar(300)). */
    public static final int MAX_ERROR_LENGTH = 300;

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, updatable = false)
    private SourceType sourceType;

    /** The product's id for PRODUCT documents, else null. */
    @Column(name = "source_id", updatable = false)
    private UUID sourceId;

    @Column(nullable = false)
    private String content;

    /** SHA-256 of content in hex: the same text twice means nothing to do. */
    @Column(name = "content_hash", nullable = false)
    private String contentHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentStatus status;

    @Column(name = "chunk_count", nullable = false)
    private int chunkCount;

    private String error;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Transient
    @Getter(AccessLevel.NONE)
    private boolean isNew = true;

    /** A new document, waiting to be indexed. */
    public static DocumentEntity newDocument(UUID tenantId, String title, SourceType sourceType, UUID sourceId,
            String content, String contentHash) {
        Instant now = Instant.now();
        DocumentEntity document = new DocumentEntity();
        document.id = UUID.randomUUID();
        document.tenantId = tenantId;
        document.title = title;
        document.sourceType = sourceType;
        document.sourceId = sourceId;
        document.content = content;
        document.contentHash = contentHash;
        document.status = DocumentStatus.PROCESSING;
        document.chunkCount = 0;
        document.createdAt = now;
        document.updatedAt = now;
        return document;
    }

    /** New text (a product was edited): index it again. */
    public void replaceContent(String title, String content, String contentHash) {
        this.title = title;
        this.content = content;
        this.contentHash = contentHash;
        this.status = DocumentStatus.PROCESSING;
        this.error = null;
        this.updatedAt = Instant.now();
    }

    public void markReady(int chunkCount) {
        this.status = DocumentStatus.READY;
        this.chunkCount = chunkCount;
        this.error = null;
        this.updatedAt = Instant.now();
    }

    public void markFailed(String error) {
        String shortError = error;
        if (shortError == null || shortError.isBlank()) {
            shortError = "Indexing failed";
        }
        if (shortError.length() > MAX_ERROR_LENGTH) {
            shortError = shortError.substring(0, MAX_ERROR_LENGTH);
        }
        this.status = DocumentStatus.FAILED;
        this.chunkCount = 0;
        this.error = shortError;
        this.updatedAt = Instant.now();
    }

    /** Written out only to say it's never null: every document gets its id in newDocument. */
    @Override
    public @NonNull UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }

}
