# 0005: Tenant filtering is written into each query for now

Status: Accepted (2026-11-01)

## Context

Every tenant-owned table has a `tenant_id`, and no shop may ever read another shop's rows. The
architecture doc and CLAUDE.md call for automatic filtering: a Hibernate filter fed by a
request-scoped tenant context, so a repository method can't forget the shop.

A spike on this branch confirmed that Hibernate 7's `@TenantId` does that: with a resolver reading the
current shop, `findById`, `findAll`, `count` and derived queries were all filtered, and saving a row for
another shop was refused. It also showed the costs: Hibernate needs a tenant for every session (even the
one Spring Data opens at startup), so "no shop" must be a sentinel id that matches nothing, and sign-up,
login and refresh need an explicit "across all shops" escape. This is a learning project, and the
invisible `WHERE` clause plus the scoped context were hard to follow at this stage.

## Decision

For now, tenant filtering is explicit and visible in the code:

- The shop id travels as an ordinary parameter: the controller reads it from the token's `tid`
  (admin) or resolves it from the slug with `TenantApi.findActiveTenantIdBySlug` (storefront), then
  passes it to the service and the repository.
- Every query on a tenant-owned table includes it in the method name, e.g.
  `findAllByTenantIdOrderByCreatedAtAsc(tenantId)` and `findByIdAndTenantId(id, tenantId)`.
  The built-in `findById`, `findAll` and `count` are not used on those tables.
- Another shop's id gives 404, not 403, so a caller can't learn that an id exists elsewhere.
- Cross-tenant tests are mandatory per module: two real shops, and each must see only its own rows.
  `CrossTenantTests` was checked by swapping in `findById`/`findAll`, which made it fail.
- The `tenants` table is the list of shops itself (its `id` is the tenant id), so it is looked up by
  id or by its unique slug. `tenant_branding` is keyed by `tenant_id`, so `findById` is already filtered.

No `TenantContext` and no Hibernate tenant resolver exist yet. `spring.jpa.open-in-view` is off.

## Consequences

- Safety depends on every query remembering `...TenantId`. The cross-tenant tests are the guard, so each
  new module's endpoints need one.
- Native SQL queries must filter by hand either way.
- Moving to automatic filtering later (a new ADR superseding this one) means adding `@TenantId` to
  tenant-owned entities, a resolver, and a context set at the edge; the explicit queries can stay as
  documentation or be simplified. The spike's findings above are the starting point.
- The issue's "request-scoped tenant context" task is deferred with it.
