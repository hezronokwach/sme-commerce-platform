# Architecture Decision Records

One file per decision, numbered in order. Never rewrite an accepted record; add a new one that supersedes it.

| # | Decision | Status |
| --- | --- | --- |
| [0001](0001-explicit-module-dependencies-and-shared-kernel.md) | Explicit module dependencies and an open shared kernel | Accepted |
| [0002](0002-where-security-lives.md) | Security is split by owner, not a shared module | Accepted |
| [0003](0003-one-schema-per-module-flyway-folders.md) | One Postgres schema per module, one Flyway folder per module | Accepted |
| [0004](0004-jwt-resource-server-rotating-refresh-tokens.md) | JWT access tokens via Spring Resource Server, HS256, rotating refresh tokens | Accepted |
| [0005](0005-explicit-tenant-filtering-in-queries.md) | Tenant filtering is written into each query for now | Accepted |
