package com.hezron.smecommerce.assistant.internal.application;

import com.hezron.smecommerce.assistant.internal.domain.DocumentStatus;
import com.hezron.smecommerce.assistant.internal.domain.SourceType;
import com.hezron.smecommerce.assistant.internal.persistence.DocumentEntity;
import com.hezron.smecommerce.assistant.internal.persistence.DocumentRepository;
import com.hezron.smecommerce.shared.Money;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps the assistant's knowledge of one product in step with catalog (Guide 7.1, "ON ProductCreated /
 * ProductChanged"). Only products for sale (ACTIVE) are known: a draft isn't for sale yet, and an
 * archived product is forgotten.
 */
@Service
public class ProductKnowledge {

    private final DocumentRepository documents;
    private final DocumentChunks chunks;
    private final ApplicationEventPublisher events;

    ProductKnowledge(DocumentRepository documents, DocumentChunks chunks, ApplicationEventPublisher events) {
        this.documents = documents;
        this.chunks = chunks;
        this.events = events;
    }

    /**
     * @param status the product's status as text: DRAFT, ACTIVE or ARCHIVED
     * @return true if the product's text is (re-)indexed, false if nothing had to happen
     */
    @Transactional
    public boolean remember(UUID tenantId, UUID productId, String name, String description, Money price,
            String status) {
        Optional<DocumentEntity> existing = documents.findByTenantIdAndSourceTypeAndSourceId(tenantId,
                SourceType.PRODUCT, productId);

        if (!"ACTIVE".equals(status)) {
            if (existing.isPresent()) {
                // Not for sale (any more): the assistant must not offer it.
                chunks.removeFor(existing.get().getId());
                documents.delete(existing.get());
            }
            return false;
        }

        String text = describe(name, description, price);
        String hash = ContentHash.of(text);
        DocumentEntity document;
        if (existing.isEmpty()) {
            document = DocumentEntity.newDocument(tenantId, titleFor(name), SourceType.PRODUCT, productId, text, hash);
            documents.save(document);
        } else {
            document = existing.get();
            boolean sameText = document.getContentHash().equals(hash);
            if (sameText && document.getStatus() != DocumentStatus.FAILED) {
                return false; // nothing changed: no embedding cost (a failed one is simply tried again)
            }
            document.replaceContent(titleFor(name), text, hash);
        }
        events.publishEvent(new DocumentIndexRequestedEvent(tenantId, document.getId()));
        return true;
    }

    /**
     * One sentence about the product, which is what the assistant will read:
     * "Sukuma bundle costs KES 50.00. Fresh sukuma wiki, tied in bundles."
     */
    static String describe(String name, String description, Money price) {
        StringBuilder text = new StringBuilder();
        text.append(name).append(" costs ").append(formatPrice(price)).append('.');
        if (description != null && !description.isBlank()) {
            text.append(' ').append(description.strip());
        }
        return text.toString();
    }

    /** 5000 KES -> "KES 50.00" (minor units are cents). */
    static String formatPrice(Money price) {
        long whole = price.amountMinor() / 100;
        long cents = price.amountMinor() % 100;
        return String.format(Locale.ROOT, "%s %d.%02d", price.currency(), whole, cents);
    }

    private static String titleFor(String name) {
        if (name.length() > 160) {
            return name.substring(0, 160);
        }
        return name;
    }

}
