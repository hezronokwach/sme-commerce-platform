# 0021: The database is on Neon, not Render

Status: Accepted (2026-11-24). Changes where ADR 0018 puts Postgres; the rest of 0018 stands.

## Context

ADR 0018 put Postgres on Render next to the backend. Render's free database is deleted after 30 days,
and the assistant needs the `vector` extension. Neon's free plan keeps the database, supports pgvector,
and runs Postgres 17 in Frankfurt, the same region as the Render service.

## Decision

1. **Postgres runs on Neon.** The Blueprint (`infra/render.yaml`) no longer creates a database. The four
   connection values (`DB_HOST`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`) are typed into Render's dashboard.
2. **The backend uses Neon's direct address, not the pooled one.** The pooled address hands a connection
   to another client after each transaction, which breaks Flyway's migration lock and the session state
   the JDBC driver keeps. Our own pool (Hikari, 5 connections) is enough for one instance.
3. **Connections are encrypted** (`sslmode=require` in `application-prod.properties`), as Neon requires.

## Consequences

- The database and the backend are run by two companies, so there are two dashboards and two sets of
  limits. Neon's free plan has 0.5 GB of storage, plenty for a demo.
- Neon's free database sleeps after 5 minutes without queries and wakes in a few seconds, so the first
  request after a quiet spell is slower. The scheduled jobs (reconciliation every 30 s) keep it awake while
  the backend itself is awake.
- Moving to another Postgres later only means changing the four values.
