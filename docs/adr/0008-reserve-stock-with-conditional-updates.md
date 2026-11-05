# 0008: Stock is reserved with one conditional UPDATE per line

Status: Accepted (2026-11-06)

## Context

Two customers can try to buy the last item at the same moment. The Guide (3.4) suggests optimistic
locking: both read `reserved = 0`, the first save wins, the second gets a version conflict, catch it and
retry the whole reserve once.

But `InventoryApi.reserve` is called from ordering's transaction, so the order and its holds are saved
together or not at all. A transaction that hit a version conflict is marked rollback-only: a retry inside
it can't work, and only the outermost caller could retry.

## Decision

`reserve`, `release` and `confirm` change stock with one SQL statement per line, with the rule in the
WHERE clause, for example:

```sql
UPDATE inventory.stock_items
SET reserved = reserved + :quantity, version = version + 1
WHERE product_id = :productId AND tenant_id = :tenantId AND on_hand - reserved >= :quantity
```

- Postgres locks the row while updating it. A second transaction for the same row waits, then
  re-checks the WHERE against the committed numbers (READ COMMITTED). The statement returns 0 changed
  rows when the rule says no, and the caller throws `InsufficientStockException`. No retry is needed,
  and it works inside any caller's transaction.
- Lines are handled sorted by product id, so two orders always lock shared rows in the same order and
  can't deadlock.
- An exception on any line rolls back the lines held before it: all or nothing.
- The UPDATEs also add 1 to `version`, so the admin's restock (which loads the entity and uses
  `@Version`) still detects that the row changed underneath it.
- The `ck_stock_items_reserved` CHECK (`0 <= reserved <= on_hand`) stays as the last line of defence.

Proved by `ParallelReservationTests`: on real Postgres, 2 threads for 1 item give exactly 1 hold, and
10 threads for 5 items give exactly 5, over 20 rounds each.

## Consequences

- Product rows are locked for the moment of each UPDATE; a very popular product serializes its
  checkouts. Fine at this scale.
- JPQL bulk updates bypass the entity: `@Modifying(clearAutomatically = true)` makes Hibernate forget
  loaded stock entities, so the next read in the same transaction is fresh.
- Admin restocks still use optimistic locking (`@Version`) and answer 409 CONCURRENT_UPDATE on a clash,
  which is right for a person editing a form.
