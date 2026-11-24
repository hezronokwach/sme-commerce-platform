-- Assistant module (Guide 7.1, ADR 0015): what each shop has given its assistant, the searchable chunks,
-- and the daily usage that keeps AI costs in check.

-- One row per thing the assistant knows: an uploaded policy or FAQ, or one product.
CREATE TABLE assistant.documents (
    id           uuid         NOT NULL,
    tenant_id    uuid         NOT NULL,
    title        varchar(160) NOT NULL,
    -- UPLOAD: a file the owner gave us. PRODUCT: written from a catalog product. POLICY: reserved.
    source_type  varchar(20)  NOT NULL,
    -- The product's id when source_type is PRODUCT (a plain column: no foreign key across schemas).
    source_id    uuid,
    -- The text that was (or is being) indexed. Kept so indexing can run after the upload's transaction
    -- commits, and so a document can be indexed again later without asking for the file twice.
    content      text         NOT NULL,
    -- SHA-256 of content, in hex: when it hasn't changed, indexing is skipped (no embedding cost).
    content_hash char(64)     NOT NULL,
    status       varchar(20)  NOT NULL DEFAULT 'PROCESSING',
    chunk_count  int          NOT NULL DEFAULT 0,
    -- A short message, only when status is FAILED.
    error        varchar(300),
    created_at   timestamptz  NOT NULL DEFAULT now(),
    updated_at   timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT pk_documents PRIMARY KEY (id),
    CONSTRAINT ck_documents_source_type CHECK (source_type IN ('UPLOAD', 'PRODUCT', 'POLICY')),
    CONSTRAINT ck_documents_status CHECK (status IN ('PROCESSING', 'READY', 'FAILED')),
    CONSTRAINT ck_documents_product_has_source CHECK (source_type <> 'PRODUCT' OR source_id IS NOT NULL)
);

-- Each product has at most one document per shop. Uploads have no source_id, so they aren't limited
-- (NULLs never clash in a unique index).
CREATE UNIQUE INDEX uq_documents_tenant_source ON assistant.documents (tenant_id, source_type, source_id);
CREATE INDEX ix_documents_tenant_created ON assistant.documents (tenant_id, created_at DESC);

-- The searchable chunks, in exactly the shape Spring AI's PgVectorStore reads and writes. Flyway creates
-- it (spring.ai.vectorstore.pgvector.initialize-schema=false), so every table comes from a migration.
-- tenant_id and document_id live inside metadata; every search must filter on tenant_id (#24).
CREATE TABLE assistant.vector_store (
    id        uuid NOT NULL DEFAULT gen_random_uuid(),
    content   text,
    metadata  json,
    -- 768 numbers per chunk: gemini-embedding-001 cut down to 768 dimensions (ADR 0015).
    embedding vector(768),
    CONSTRAINT pk_vector_store PRIMARY KEY (id)
);

-- Nearest-neighbour search by cosine distance stays fast as the table grows.
CREATE INDEX ix_vector_store_embedding ON assistant.vector_store USING hnsw (embedding vector_cosine_ops);
-- Finding one shop's or one document's chunks (the tenant filter, and "delete this document's chunks").
CREATE INDEX ix_vector_store_tenant ON assistant.vector_store ((metadata ->> 'tenant_id'));
CREATE INDEX ix_vector_store_document ON assistant.vector_store ((metadata ->> 'document_id'));

-- AI cost control (#24): one row per shop per day.
CREATE TABLE assistant.usage_daily (
    tenant_id     uuid   NOT NULL,
    day           date   NOT NULL,
    questions     int    NOT NULL DEFAULT 0,
    input_tokens  bigint NOT NULL DEFAULT 0,
    output_tokens bigint NOT NULL DEFAULT 0,
    CONSTRAINT pk_usage_daily PRIMARY KEY (tenant_id, day)
);
