# 0015: Assistant ingestion: Gemini embeddings, our own chunking, Flyway-owned vector table

Status: Accepted (2026-11-19)

## Context

Issue "Assistant part 1: ingestion". Done when: documents for tenant A are stored with their tenant id.

ADR 0007 made Groq the chat model, but Groq has no embeddings API, and retrieval needs embeddings. The
embeddings provider was left open, and with it the vector size of `assistant.vector_store`. The owner
chose the Gemini API.

## Decision

1. **Embeddings come from Gemini:** `gemini-embedding-001`, through Gemini's OpenAI-compatible endpoint
   (`https://generativelanguage.googleapis.com/v1beta/openai`).
   - Spring AI's OpenAI client (2.0.2, built on the official `openai-java` SDK) serves both providers.
     The embedding settings have their own base URL and key, and chat (Groq) uses the common ones from #24.
   - **768 dimensions.** The model can give 768, 1536 or 3072, and pgvector's HNSW index takes at most
     2000. 768 is plenty for short shop texts.
   - The key is `GEMINI_API_KEY`, only from the environment. Without it the app still starts, and each
     indexing attempt ends FAILED with a short message.
   - The code depends on Spring AI's `EmbeddingModel` and `VectorStore`, never on Gemini types. Tests
     replace the model with a deterministic fake (`FakeEmbeddingModel`), so CI needs no key.
2. **What the assistant knows:**
   - **ACTIVE products**, from catalog's `ProductCreated` and `ProductChanged` events. Both events now
     carry the status and price. The product becomes one sentence: "Sukuma bundle costs KES 50.00.
     Fresh sukuma wiki…". A draft isn't known yet, and an archived product is forgotten: its chunks and
     document row are removed.
   - **Uploads:** `.txt` or `.md` files of at most 2 MB, in strict UTF-8. Only the owner may upload.
     PDFs wait for a later issue.
   - An owner-only "reindex products" call (`CatalogApi.listActiveProducts`) teaches the assistant
     products that existed before it listened.
3. **Our own chunking:** about 400 words per chunk, with a 50-word overlap (Guide 7.1). Spring AI's
   `TokenTextSplitter` counts tokens and doesn't overlap.
4. **Flyway owns every table**, the vector table included (V80, `initialize-schema=false`). It keeps
   Spring AI's shape (`id, content, metadata json, embedding vector(768)`), with an HNSW cosine index and
   indexes on `metadata->>'tenant_id'` and `metadata->>'document_id'`.
   - `assistant.documents` gains a **`content`** column that the database design didn't have. Indexing
     runs after the upload's transaction commits, so the text must be kept somewhere, and it also lets a
     document be indexed again later.
5. **Indexing never holds a transaction while the embedding model is called.** This is the same pattern
   as starting a payment:
   - saving the text publishes an internal `DocumentIndexRequestedEvent`;
   - `@Async @TransactionalEventListener` picks it up after commit;
   - it reads the text (transaction 1), cuts it, deletes the document's old chunks, and adds the new
     ones (the vector store embeds, then inserts);
   - it marks the document READY or FAILED (transaction 2).

   The old chunks are deleted first so that an outdated policy can't contradict the new one.
6. **Every chunk's metadata says whose it is:** `tenant_id`, `document_id`, `title`, `source_type` and
   `chunk_index`. These values come from our own document row, never from a request. Search (#24)
   always filters on `tenant_id`.
7. **Unchanged text costs nothing.** `content_hash` (SHA-256) is compared before indexing again. If the
   text changed during an indexing run, that run doesn't mark the document READY and removes its own
   stale chunks.

## Consequences

- No local model and no extra memory, so the app stays small for Render. Embeddings need the network
  and a key, and Gemini's free-tier rate limits apply. Indexing is per document and batched by Spring AI.
- A product's knowledge lags a save by a second or two, while the embedding runs.
- Between deleting old chunks and adding new ones, a document briefly has no chunks. A question at that
  exact moment just won't find it.
- Switching providers later means re-embedding everything. The vector size is part of the migration, so
  a different size needs a new migration and a reindex.
- The admin screen for documents is #25. Search, the prompt and cost limits are #24.
