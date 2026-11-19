package com.hezron.smecommerce.assistant.internal.web;

import com.hezron.smecommerce.assistant.internal.application.AssistantDocumentService;
import com.hezron.smecommerce.assistant.internal.application.DocumentResult;
import com.hezron.smecommerce.assistant.internal.application.UploadDocumentCommand;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * What a shop's assistant knows (Guide 7.1). Owners and staff can see the list; only owners add or remove
 * documents, since that changes what customers are told. hasRole('OWNER') matches authority ROLE_OWNER.
 */
@RestController
@RequestMapping("/api/admin/assistant")
class AssistantDocumentController {

    private final AssistantDocumentService documentService;

    AssistantDocumentController(AssistantDocumentService documentService) {
        this.documentService = documentService;
    }

    /** {@code GET /api/admin/assistant/documents}: newest first, with each one's indexing status. */
    @GetMapping("/documents")
    List<DocumentResponse> list(@AuthenticationPrincipal Jwt jwt) {
        UUID tenantId = CallerTenant.idFrom(jwt);
        List<DocumentResponse> responses = new ArrayList<>();
        for (DocumentResult result : documentService.list(tenantId)) {
            responses.add(DocumentResponse.fromResult(result));
        }
        return responses;
    }

    /**
     * {@code POST /api/admin/assistant/documents} (multipart: title, file). A .txt or .md file of at most
     * 2 MB. Answers 202 Accepted: the document is saved, and its chunks are made right after.
     */
    @PostMapping(path = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasRole('OWNER')")
    DocumentResponse upload(@AuthenticationPrincipal Jwt jwt, @RequestParam("title") String title,
            @RequestPart("file") MultipartFile file) {
        UUID tenantId = CallerTenant.idFrom(jwt);
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the uploaded file", e);
        }
        UploadDocumentCommand command = new UploadDocumentCommand(title, file.getOriginalFilename(), bytes);
        return DocumentResponse.fromResult(documentService.upload(tenantId, command));
    }

    /** Removes an uploaded document and its chunks. Product documents follow their product instead (409). */
    @DeleteMapping("/documents/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('OWNER')")
    void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID documentId) {
        UUID tenantId = CallerTenant.idFrom(jwt);
        documentService.delete(tenantId, documentId);
    }

    /** Teaches the assistant every product on sale now. Unchanged products are skipped (no cost). */
    @PostMapping("/products/reindex")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasRole('OWNER')")
    ReindexResponse reindexProducts(@AuthenticationPrincipal Jwt jwt) {
        UUID tenantId = CallerTenant.idFrom(jwt);
        return new ReindexResponse(documentService.reindexProducts(tenantId));
    }

}
