# 0014: Reconciliation, expiring unpaid orders, safe retries and a circuit breaker

Status: Accepted (2026-11-19)

## Context

Issue "Buffer + reconciliation and timeouts". Done when: a payment stuck in pending resolves itself.

Callbacks get lost and start requests time out. Before this issue a payment without its callback stayed
PENDING or UNKNOWN forever, so the order page said "checking" forever. Unpaid orders never expired, so
their stock stayed held after the 15 minutes. And when a provider was down, every checkout waited for a
timeout. `payments.payments` already had `check_attempts` and `next_check_at`; nothing used them.

## Decision

1. **Reconciliation asks the provider again** (Guide 5.7). `PaymentReconciliationJob` runs every 30 s
   and takes at most 50 PENDING, UNKNOWN or TIMED_OUT payments whose `next_check_at` is due:
   - no transaction is open while the provider is asked; each database step is its own short one;
   - a final answer goes through `PaymentCallbackService.applyStatus`, which takes **the same row lock
     and "already final?" check as a callback**. A callback and a status query for the same payment
     apply once, whichever comes first. The answer is written to `callback_log` with
     `"source": "status-query"`;
   - "not yet" means: ask again after 30 s, 1 min, 2 min, 5 min, then every 10 min;
   - still nothing after 10 minutes: **TIMED_OUT**, which customers see as "checking", never "failed".
     It is still asked about hourly; after 24 h the job stops and people look at it instead;
   - a start that got no answer **and** no provider reference can't be asked about. After 3 minutes it
     is FAILED with `NO_REPLY` ("We couldn't confirm the payment. Please try again."), retryable.
   - The job spans every shop, like the callback lookup: it's the system's own work. Each row carries
     its `tenant_id`, and everything afterwards uses it.
2. **The amount rule for status queries.** Card's query states `amount_received`, which must match,
   exactly as in its webhook. An M-Pesa status query states no amount, so a confirmed reference is
   accepted with our own amount. The reference was ours to begin with: the provider gave it to us for
   this payment.
3. **Unpaid orders expire** (Guide 3.5). `OrderExpiryJob` in ordering runs every minute and takes at most
   100 PENDING_PAYMENT orders past `expires_at`. With the order row locked:
   - if the newest payment is INITIATED, PENDING or UNKNOWN, the order waits: the customer may have
     paid at the last moment, and reconciliation settles that within minutes;
   - otherwise (no attempt, FAILED or TIMED_OUT) the order becomes EXPIRED through
     `OrderStatusRecorder` (so the live pages update) and its stock is released.

   The payment result listener now takes the same order lock, so an expiry and a payment result
   happen one after the other. Money that still arrives after the expiry marks the order PAID, with
   "check the stock by hand" in its history: real money is never ignored.
4. **Retry only what is safe to repeat.**
   - A **status query** is a read: it is tried up to twice more, after 200 ms and 1 s.
   - **Card's intent creation** is sent once more when Stripe doesn't answer. This is safe only because
     of its Idempotency-Key (the payment id): if the first one arrived, Stripe answers the second with
     the same intent.
   - **Any other start is never repeated.** It may have reached the provider (a PIN prompt on the
     phone), so a start with no answer becomes UNKNOWN, and reconciliation finds out.
   - Timeouts belong to each provider's HTTP client (card: `app.payments.card.timeout`, 10 s).
5. **A small circuit breaker of our own**, one per provider (`ProviderCircuitBreaker`, through
   `ProviderCalls`):
   - after 5 failed calls in a row it opens, and for 30 s nobody calls that provider;
   - while it is open, a new payment is refused at once: FAILED, retryable, "Payments are having
     trouble right now. Please try again in a minute." That is honest, not UNKNOWN, because we know
     nothing was sent. Reconciliation skips that provider this round;
   - after the 30 s, exactly one trial call goes through. If it works, the breaker closes; if not, it
     opens for another 30 s;
   - an answer of "no" (a refused start, a declined card) still means the provider is up;
   - all the tries of one status query together count as one failed call;
   - it logs when it opens and closes, with the provider's name only.

All the timings are settings (`app.payments.reconciliation.*`, `app.payments.provider-calls.*`,
`app.ordering.expiry.every`), so tests run them in milliseconds.

## Consequences

- A lost callback costs the customer about 90 seconds of "checking", not forever.
- Held stock comes back within about a minute of the deadline, except while a payment is still under
  way. That is at most about 10 minutes, until reconciliation decides.
- The breaker lives in memory, one per running instance. With several instances each learns about the
  outage on its own, which is fine at this size.
- The expiry job takes the oldest 100 late orders each run. If more than 100 were all waiting on
  payments, newer ones would wait a little longer. Not a concern for a small shop.
- "Try again" checks the deadline itself, so it can't start a payment for an order the job is about to
  expire. Only an attempt in the same instant could slip through, and its money would still mark the
  order PAID.
- **Rejected: Resilience4j.** It does much more (sliding windows, metrics, annotations), and is worth
  it once there are several providers and dashboards. It could replace our class then without changing
  callers, because they only see `ProviderCalls`.
- Not done here: the nightly comparison with the M-Pesa statement (Guide 5.7, "build this last") and
  alerting beyond log lines (hardening issue).
