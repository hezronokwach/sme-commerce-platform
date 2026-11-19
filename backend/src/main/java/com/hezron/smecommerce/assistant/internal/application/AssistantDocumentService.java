package com.hezron.smecommerce.assistant.internal.application;

import com.hezron.smecommerce.assistant.internal.domain.SourceType;
import com.hezron.smecommerce.assistant.internal.persistence.DocumentEntity;
import com.hezron.smecommerce.assistant.internal.persistence.DocumentRepository;
import com.hezron.smecommerce.catalog.ActiveProductView;
import com.hezron.smecommerce.catalog.CatalogApi;
import com.hezron.smecommerce.shared.TenantId;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The admin side of the assistant's knowledge: upload, list and delete documents (Guide 7.1). */
@Service
public class AssistantDocumentService {

    /** Guide 7.1: at most 2 MB. */
    public static final int MAX_UPLOAD_BYTES = 2 * 1024 * 1024;
    private static final int MAX_TITLE_LENGTH = 160;

    private final DocumentRepository documents;
    private final DocumentChunks chunks;
    private final ProductKnowledge productKnowledge;
    private final CatalogApi catalogApi;
    private final ApplicationEventPublisher events;

    AssistantDocumentService(DocumentRepository documents, DocumentChunks chunks, ProductKnowledge productKnowledge,
            CatalogApi catalogApi, ApplicationEventPublisher events) {
        this.documents = documents;
        this.chunks = chunks;
        this.productKnowledge = productKnowledge;
        this.catalogApi = catalogApi;
        this.events = events;
    }

    /**
     * Saves the file's text as PROCESSING; its chunks are made after this transaction commits.
     *
     * @throws InvalidDocumentException if the title, type, size or encoding isn't acceptable
     */
    @Transactional
    public DocumentResult upload(UUID tenantId, UploadDocumentCommand command) {
        String title = checkedTitle(command.title());
        checkFileType(command.fileName());
        byte[] bytes = command.bytes();
        if (bytes == null || bytes.length == 0) {
            throw new InvalidDocumentException("The file is empty.");
        }
        if (bytes.length > MAX_UPLOAD_BYTES) {
            throw new InvalidDocumentException("The file is larger than 2 MB.");
        }
        String text = readUtf8(bytes);
        if (text.isBlank()) {
            throw new InvalidDocumentException("The file has no text in it.");
        }

        DocumentEntity document = DocumentEntity.newDocument(tenantId, title, SourceType.UPLOAD, null, text,
                ContentHash.of(text));
        documents.save(document);
        events.publishEvent(new DocumentIndexRequestedEvent(tenantId, document.getId()));
        return DocumentResult.fromEntity(document);
    }

    @Transactional(readOnly = true)
    public List<DocumentResult> list(UUID tenantId) {
        List<DocumentResult> results = new ArrayList<>();
        for (DocumentEntity document : documents.findAllByTenantIdOrderByCreatedAtDesc(tenantId)) {
            results.add(DocumentResult.fromEntity(document));
        }
        return results;
    }

    /**
     * Removes an uploaded document and its chunks.
     *
     * @throws DocumentNotFoundException       if the id is unknown or another shop's
     * @throws ProductDocumentNotDeletableException if it describes a product (it follows the product instead)
     */
    @Transactional
    public void delete(UUID tenantId, UUID documentId) {
        Optional<DocumentEntity> found = documents.findByIdAndTenantId(documentId, tenantId);
        if (found.isEmpty()) {
            throw new DocumentNotFoundException();
        }
        DocumentEntity document = found.get();
        if (document.getSourceType() == SourceType.PRODUCT) {
            throw new ProductDocumentNotDeletableException();
        }
        chunks.removeFor(document.getId());
        documents.delete(document);
    }

    /**
     * Teaches the assistant every product the shop sells now: for products that existed before the
     * assistant listened to catalog, or to retry failed ones. Unchanged products cost nothing.
     *
     * @return how many products are being (re-)indexed
     */
    @Transactional
    public int reindexProducts(UUID tenantId) {
        List<ActiveProductView> products = catalogApi.listActiveProducts(TenantId.of(tenantId));
        int queued = 0;
        for (ActiveProductView product : products) {
            boolean indexed = productKnowledge.remember(tenantId, product.productId(), product.name(),
                    product.description(), product.price(), "ACTIVE");
            if (indexed) {
                queued = queued + 1;
            }
        }
        return queued;
    }

    private static String checkedTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new InvalidDocumentException("Give the document a title.");
        }
        String trimmed = title.strip();
        if (trimmed.length() > MAX_TITLE_LENGTH) {
            throw new InvalidDocumentException("The title is longer than 160 characters.");
        }
        return trimmed;
    }

    private static void checkFileType(String fileName) {
        String lower = "";
        if (fileName != null) {
            lower = fileName.toLowerCase(Locale.ROOT);
        }
        if (lower.endsWith(".txt") || lower.endsWith(".md")) {
            return;
        }
        throw new InvalidDocumentException("Only .txt and .md files can be uploaded.");
    }

    /** Strict UTF-8: a file that isn't text (or isn't UTF-8) is refused instead of stored as garbage. */
    private static String readUtf8(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new InvalidDocumentException("The file isn't UTF-8 text.");
        }
    }

}
