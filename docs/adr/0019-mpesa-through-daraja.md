# 0019: M-Pesa through Daraja, and what happens when the customer paid but no callback came

Status: Accepted (2026-11-24)

## Context

Issues "M-Pesa part 1" (STK push), "M-Pesa part 2" (the callback) and "Buffer + payment tests". Until now
the fake provider stood in for M-Pesa behind the `PaymentProvider` interface.

## Decision

1. **`MpesaPaymentProvider`** talks to Safaricom's Daraja API over plain HTTPS (`DarajaClient`, no SDK),
   the same way the card provider talks to Stripe.
   - The access token is cached until a minute before it expires. A 401 drops it and retries once, which is
     safe because Daraja did nothing.
   - The STK push sends the shortcode, a timestamp in Nairobi time, the password
     base64(shortcode + passkey + timestamp), the amount in whole shillings, the `2547…` phone and the
     callback address. Orders with cents are refused, because M-Pesa can't charge them.
   - A timeout is never retried: the payment is UNKNOWN and reconciliation finds out (ADR 0014).
2. **The callback** is `POST /api/webhooks/mpesa/{secret}`. Safaricom doesn't sign its callbacks, so:
   - the address ends in a long secret (`MPESA_CALLBACK_SECRET`), compared in constant time; anything else
     gets 404;
   - the CheckoutRequestID must belong to one of our payments;
   - the amount must match;
   - a final payment can't change.

   Every body is stored in `callback_log` with what was done with it.
3. **One platform Daraja app for now**, configured from the environment, like Stripe. Per-shop
   credentials, encrypted in `secret_config`, come later.

## Paid, but no callback arrived (#15)

Callbacks get lost: Safaricom gives up, our service was asleep, or the network dropped it.

1. The payment stays PENDING. The customer sees "checking", never "failed".
2. Reconciliation runs an **STK query** 90 seconds after the prompt, then backs off (ADR 0014).
3. "Still processing" means ask again. A result code is applied exactly like a callback, under the same
   lock, so a late callback is just a DUPLICATE. The query states no amount, so the confirmed
   CheckoutRequestID is the proof.
4. After 10 minutes with no answer the payment is TIMED_OUT, and it is still asked about hourly for a day.
   An unpaid order waits for that before it expires. Money that arrives even later still marks it PAID,
   with "check the stock by hand".
5. If Daraja never confirms but the customer shows an M-Pesa message, staff compare it with the
   statement. The nightly statement comparison is left for later (Guide 5.7).

## Consequences

- Tests use a pretend Daraja and Safaricom's documented callback bodies. A real prompt on a phone needs
  sandbox keys and a public callback address (a tunnel locally, Render when deployed).
- The MerchantRequestID isn't stored yet. The CheckoutRequestID is enough to match callbacks and queries.
