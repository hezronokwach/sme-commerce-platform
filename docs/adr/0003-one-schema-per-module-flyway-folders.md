# 0003: One Postgres schema per module, one Flyway folder per module

Status: Accepted (2026-10-28)

## Context

All modules share one PostgreSQL database. Without separation, any module's SQL or JPA query can join
another module's tables, which quietly breaks the boundaries that `ModularityTests` guards in Java.

## Decision

- Each module gets its own schema named after it (`tenant`, `identity`, `catalog`, ...). All schemas are
  created up front in `V1__platform_extensions_and_schemas.sql`, together with the `vector` extension.
- No foreign keys across schemas. A module stores another module's id as a plain `uuid` column.
- Migrations live in `backend/src/main/resources/db/migration/<module>/`. Flyway scans `db/migration`
  recursively, so a new module folder needs no config change.
- Versions are global across folders, with a block per module: V1 platform, V10–19 tenant,
  V20–29 identity, V30 catalog, V40 inventory, V50 ordering, V60 payments, V70 notification,
  V80 assistant, V90 audit, V95 Modulith event publication.
- File name: `V<version>__<module>_<what_it_does>.sql`, for example `V11__tenant_add_timezone.sql`.
- A migration that has run anywhere shared (CI aside) is never edited; fix forward with a new version.
- Flyway's own history table stays in `public`. Hibernate never changes the schema (`ddl-auto=none`).

## Consequences

- A cross-module join must name the other schema (`catalog.products`), so it stands out in review.
- Two migrations with the same version in different folders fail startup, which the version blocks avoid.
- The database can't catch a wrong id across modules; code and events must.
- Requires the pgvector image everywhere (Compose, Testcontainers, Render).
