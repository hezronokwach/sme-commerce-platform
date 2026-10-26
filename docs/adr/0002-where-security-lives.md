# 0002: Security is split by owner, not a shared module

Status: Accepted (2026-10-26)

## Context

Authentication, tenant resolution, webhook protection and endpoint authorisation are needed across
modules. A single "security" or "common" module that every module depends on is the usual shortcut.

## Decision

Spring Security is built from beans and filters that Spring assembles into one pipeline wherever they
are declared, so each piece lives with the module that owns the knowledge:

| Piece | Owner |
| --- | --- |
| Login, password hashing, JWT issue and validation, refresh tokens, filter chain for `/api/admin/**` | `identity` (internal) |
| Storefront filter: `/api/store/{slug}/**` resolves the shop via `TenantApi.findBySlug` | `identity` (identity → tenant is already allowed) |
| Minimal filter chain for `/api/webhooks/**` (own `@Order`) | `payments` (internal) |
| Endpoint rules such as `@PreAuthorize("hasRole('OWNER')")` | each module's own controllers |
| `TenantId`, `TenantContext`, tenant-owned entity base type | `shared` |

Not in `shared`: no filter chains, token code or Spring Security configuration. `shared` holds only
the types every module needs to read the current tenant.

## Consequences

- No new module dependencies: identity → tenant already exists, and payments needs nothing extra.
- Identity never needs a list of other modules' URLs; each module protects its own endpoints.
- Security config is spread across modules, so the filter chain `@Order` values must be documented
  where they are declared.
