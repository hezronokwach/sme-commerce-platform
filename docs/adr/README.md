# Architecture Decision Records

One file per decision, numbered in order. Never rewrite an accepted record; add a new one that supersedes it.

| # | Decision | Status |
| --- | --- | --- |
| [0001](0001-explicit-module-dependencies-and-shared-kernel.md) | Explicit module dependencies and an open shared kernel | Accepted |
| [0002](0002-where-security-lives.md) | Security is split by owner, not a shared module | Accepted |
| [0003](0003-one-schema-per-module-flyway-folders.md) | One Postgres schema per module, one Flyway folder per module | Accepted |
| [0004](0004-jwt-resource-server-rotating-refresh-tokens.md) | JWT access tokens via Spring Resource Server, HS256, rotating refresh tokens | Accepted |
| [0005](0005-explicit-tenant-filtering-in-queries.md) | Tenant filtering is written into each query for now | Accepted |
| [0006](0006-one-error-shape-and-correlation-id.md) | One error shape everywhere, with a correlation id | Accepted |
| [0007](0007-groq-as-the-llm-provider.md) | Groq is the LLM provider for the assistant | Accepted |
| [0008](0008-reserve-stock-with-conditional-updates.md) | Stock is reserved with one conditional UPDATE per line | Accepted |
| [0009](0009-events-delivered-at-least-once.md) | Events between modules are delivered at least once | Accepted |
| [0010](0010-live-order-status-with-sse-and-stream-tickets.md) | Live order status over SSE, opened with one-time stream tickets | Accepted |
| [0011](0011-catalog-may-ask-tenant-which-shop.md) | Catalog may ask tenant which shop a web name belongs to | Accepted |
| [0012](0012-admin-order-feed-reuses-sse.md) | The admin's live order feed reuses SSE, not STOMP | Accepted |
