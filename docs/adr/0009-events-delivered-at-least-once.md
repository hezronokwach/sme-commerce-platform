# 0009: Events between modules are delivered at least once

Status: Accepted (2026-11-10)

## Context

Modules react to each other through events: inventory creates stock when catalog adds a product,
payments asks the provider once an order is saved, ordering marks an order paid when payments says so.
The publisher's transaction commits first, and the listener runs afterwards, on another thread.

That gap is where events get lost. If the app dies after the commit but before the listener has
finished, or the listener throws, the customer has paid but the order never becomes PAID, or a product
never gets its stock row. Nothing retries it on its own.

## Decision

1. **Spring Modulith's event publication registry** (the `event_publication` table, V95). Publishing an
   event writes one row per listener in the publisher's own transaction. If that transaction rolls back,
   the rows go too: nothing is announced that didn't happen. A row is marked COMPLETED only when its
   listener finishes.
2. **Delivered again after a restart**: `spring.modulith.events.republish-outstanding-events-on-restart=true`
   hands every unfinished row to its listener again when the app starts.
3. **Retried while the app keeps running**:
   - A listener that throws leaves its row FAILED.
   - Modulith's staleness monitor marks rows stuck in PUBLISHED (2 min), PROCESSING (5 min) or
     RESUBMITTED (5 min) as FAILED.
   - `EventRetryJob` resubmits FAILED rows every 2 minutes. It logs an ERROR for any event still
     unfinished after 15 minutes, and deletes completed rows older than 7 days.
4. **Every listener is safe to run twice** (Guide 6.2). Redelivery means the same event can arrive
   again, also after a listener half-finished. Each listener checks the state first (order already PAID?
   payment no longer INITIATED?) or relies on a unique key (one stock row per product).
5. **Listeners that make slow outside calls don't use `@ApplicationModuleListener`.** That annotation
   wraps the whole method in a transaction. `PaymentRequestedListener` uses `@Async
   @TransactionalEventListener` instead and opens short transactions itself around the provider call.
   The registry records these listeners in exactly the same way.

The registry is the JPA one (`spring-modulith-starter-jpa`). The issue said JDBC; both give the same
guarantee, and JPA was already in place.

## Consequences

- No event is lost by a crash or a failing listener. `EventSurvivesRestartTests` starts the app, cuts
  a listener off mid-work with a shutdown, starts the app again on the same database and sees the event
  completed. `EventRetryTests` makes a listener fail on purpose and sees the retry job complete it.
- Events arrive **at least once, never exactly once**. A listener that forgets to check the state
  first can double-apply (two stock confirmations, two PAID history rows). Every new listener needs a
  "delivered twice" test.
- Order is not guaranteed between events, especially for retries. Listeners must not assume one event
  arrives before another; they look at the current state instead.
- A retry happens 1 to 5 minutes after a failure, not at once. Good enough for stock and order status.
  The customer-facing payment status (#19) reads the payment itself and doesn't depend on this timing.
- When a broker would be worth it: once a module moves to its own service, or another system needs our
  events. Modulith can then publish the same events to Kafka or similar ("externalization") without
  changing the listeners. Until then, a table in the same database is simpler and fully transactional.
