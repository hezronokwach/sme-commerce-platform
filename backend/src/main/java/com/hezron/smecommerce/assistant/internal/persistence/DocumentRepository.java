package com.hezron.smecommerce.assistant.internal.persistence;

import com.hezron.smecommerce.assistant.internal.domain.SourceType;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Every query names the shop (ADR 0005): don't use the built-in findById / findAll, they cover every shop. */
public interface DocumentRepository extends JpaRepository<DocumentEntity, UUID> {

    Optional<DocumentEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    /** The same document, LOCKED until the transaction ends, so two indexing runs finish one after the other. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM DocumentEntity d WHERE d.id = :id AND d.tenantId = :tenantId")
    Optional<DocumentEntity> lockByIdAndTenantId(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

    /** The shop's documents, newest first. */
    List<DocumentEntity> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    /** A product's document (at most one per shop: uq_documents_tenant_source). */
    Optional<DocumentEntity> findByTenantIdAndSourceTypeAndSourceId(UUID tenantId, SourceType sourceType,
            UUID sourceId);

}
