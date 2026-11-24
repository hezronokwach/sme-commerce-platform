# 0017: Hardening and observability

Status: Accepted (2026-11-24)

## Context

Issue "Hardening and observability". Done when: one order can be traced through the logs by its
correlation id.

## Decision

1. **One id follows the work across threads.** The correlation id and shop are registered with Micrometer's
   context registry (`LogContextPropagation`). Modulith's task decorator then copies them onto background
   threads, so event listeners keep the id of the request that started them. Each log line shows the
   correlation id and the shop. `OrderStatusRecorder` logs every status change with the order number.
2. **Rate limits run before security**, per IP and minute: login 5, signup 5, refresh 30, place order 20,
   payment attempt 10. A blocked login doesn't even cost a password check.
3. **One phone can receive at most 10 orders a minute per shop**, counted in the database. Each order can
   send a PIN prompt, so this protects someone else's phone from being flooded.
4. **CORS** allows only `CORS_ALLOWED_ORIGINS`, by default the two local frontends.
5. **Metrics:** health is public. `/actuator/metrics` covers every shop, so only the server itself can read
   it. `sme.payments.results` counts payment results by provider and outcome.
6. **Audit log** (V90): price changes, manual stock changes and payment settings. The row is written in the
   caller's transaction, and the actor comes from the checked token. No secrets go in it.

## Consequences

- The in-memory limits count per instance and reset on restart.
- Behind a proxy every caller looks like the proxy until forwarded headers are trusted. The deployment
  issue must set this, or real customers would share one limit.
- A real provider's callback is a new request with a new correlation id. The order number in the log lines
  links it to the original order.
- There's no screen for the audit log yet. It's read with SQL.
