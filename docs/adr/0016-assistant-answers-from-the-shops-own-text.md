# 0016: The assistant answers only from the shop's own text, within a daily budget

Status: Accepted (2026-11-24)

## Context

Issue "Assistant part 2: retrieval and chat". Done when: tenant A's assistant can never quote tenant B's
documents. The endpoint is public, and every answer costs money.

## Decision

1. **`POST /api/store/{shopSlug}/assistant/ask`**, with no login. The shop comes from the URL's slug on
   the server, never from the request body.
2. **Two locks on tenant isolation.** The vector search always filters on `metadata.tenant_id`. The
   results are then checked again in Java, so a bug in one filter still can't reach the model.
3. **The module rule stays.** The assistant may only depend on catalog, so it gets the shop through
   `CatalogApi.findStorefrontShop`, and catalog asks tenant (ADR 0011).
4. **The checks run cheapest first:** question length (500), rate limit (10 a minute per shop and IP),
   daily budget, search, and only then the model. With no relevant chunk the model isn't called.
5. **The daily budget** (200 questions and 100,000 tokens per shop, per UTC day) is one conditional SQL
   upsert, so simultaneous questions can't slip past it.
6. **The prompt** says to answer only from the context, and treats the context and the question as
   information rather than instructions.
7. **Questions are never logged**, because customers may type personal details into them. When a
   provider fails, the customer gets a plain message.
8. **Chat is Groq** through the same Spring AI client (ADR 0007). The model name is `GROQ_MODEL`.

## Consequences

- The rate limit lives in memory, per instance. The database budget is the hard limit.
- Behind Render's proxy every caller shares one address until forwarded headers are trusted. This must
  be set in the deployment issue.
- pgvector filters after the index scan. If the table grows to many shops, a small shop could get too
  few matches; iterative index scans or a real `tenant_id` column would then be the fix.
- `tenants.contact_phone` isn't set by anything yet, so replies say "contact the shop directly".
