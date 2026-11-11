# 0011: Catalog may ask tenant which shop a web name belongs to

Status: Accepted (2026-11-10)

## Context

The storefront needs a shop's products without a login (workflow W5 step 4). Its only clue to the shop
is the web name in the address: `/s/mamamboga`. Catalog's queries filter by `tenant_id` (ADR 0005), so
catalog must turn "mamamboga" into the shop's id first. Only tenant knows that.

The architecture's dependency table had catalog depend on nothing, so in the catalog issue (#7) the
storefront product list was put off. Two ways to fix it:

1. **Allow catalog → tenant.** Catalog's storefront service calls `TenantApi.findActiveTenantIdBySlug`,
   exactly as ordering's `PlaceOrderService` already does.
2. **A resolver outside the modules.** Code in the root package turns `{slug}` into a `TenantId`
   before catalog's controller runs, so catalog itself never names tenant.

## Decision

**Option 1: catalog may call tenant's public API.** `catalog/package-info.java` lists `"tenant"` in
`allowedDependencies`.

- It's the same pattern ordering uses, so there's one way to do it, not two.
- Tenant depends only on `shared`, so no cycle appears. `ModularityTests` still checks every call.
- Option 2 would hide the lookup: reading catalog's controller, you couldn't see where the shop id
  came from. And it would move storefront rules (a suspended shop counts as missing) away from both
  modules that own them.

The new endpoints, all under `/api/store/{slug}` and open without a login:

| Endpoint | Returns |
| --- | --- |
| `GET /products` (optional `?categoryId=`) | ACTIVE products only, newest first |
| `GET /products/{productSlug}` | one ACTIVE product, else 404 `PRODUCT_NOT_FOUND` |
| `GET /categories` | the menu, in the owner's order |

An unknown or suspended shop gives 404 `SHOP_NOT_FOUND` from every one of them.

## Consequences

- Catalog can now also read the shop's currency from tenant when shops may pick one (it's fixed to
  KES today).
- Every storefront request asks tenant for the shop once. That's one indexed lookup by slug; it can be
  cached later if it ever shows up in timings.
- The dependency table in the architecture doc and CLAUDE.md now list catalog → tenant.
