# 0013: Card payments through Stripe test mode, chosen per shop

Status: Accepted (2026-11-18)

## Context

Issue "Second payment provider (card)": add a card provider behind the same `PaymentProvider` interface
as the fake one. The point is proving the interface holds. Done when: a shop can be switched between
M-Pesa and card without changing the ordering module.

M-Pesa itself isn't built yet (issues #13/#14 were moved later), so the fake provider stands in for it.
Each shop's payment methods had a table since V10 (`tenant.tenant_payment_providers`) that nothing used.

## Decision

1. **Each shop chooses its methods.** The owner turns FAKE (standing in for M-Pesa) and CARD on or off
   in the admin's Settings. At least one stays on. The storefront profile lists them, and checkout
   shows one button each. **Payments checks the shop's choice** before starting any payment, inside
   ordering's transaction: a method the shop doesn't offer rolls the whole order back. So switching a
   shop changes data, never code, and ordering has no rule of its own about it.
2. **Card means Stripe test mode, one platform account for now.** The keys come from the environment
   (`STRIPE_SECRET_KEY`, `STRIPE_PUBLISHABLE_KEY`, `STRIPE_WEBHOOK_SECRET`, in `backend/.env`). Without
   them, a card attempt is refused politely ("not set up yet"). Per-shop keys, encrypted in
   `secret_config`, come later.
3. **The flow:**
   - after the order commits, payments creates a PaymentIntent, with the payment id as its idempotency
     key and in its metadata. The payment is PENDING with the intent id as its reference;
   - the order page shows **Stripe's own card form** (Payment Element). The card number goes from the
     browser to Stripe, never through our server;
   - Stripe sends `payment_intent.succeeded` or `payment_intent.payment_failed` to
     `POST /api/webhooks/card`.
4. **The webhook is trusted only after its signature checks out.** The signature is HMAC-SHA256 of
   `timestamp.raw body` with the webhook secret. It's compared in constant time, and it must be at most
   5 minutes old. It's checked on the raw bytes, before any JSON parsing. Then the result goes through
   `PaymentCallbackService`, the one place results are applied, so duplicates, wrong amounts and
   retries behave as for every provider. A failed card is retryable.
5. **No Stripe Java SDK.** There are two HTTPS calls (create an intent, read one) with Spring's
   `RestClient`, and our own 30-line signature check. Less to learn, nothing hidden, and the base URL
   points at a pretend Stripe in tests, so CI needs no keys or network.
6. **One provider-neutral pass-through in ordering.** The customer's order view gains
   `paymentNextAction` ("what the browser must do to finish paying"). Payments fills it, and ordering
   passes it on unread. For card it holds the intent's client secret, read from Stripe when asked and
   never stored or logged, plus the publishable key. M-Pesa needs none. This was added once; switching
   providers never touches it again.

## Which gateways work for Kenyan businesses

Stripe is right for this portfolio's test mode, but **a business registered in Kenya can't open a
direct Stripe account**. Kenya is in Stripe's "extended network", reached through Paystack (which
Stripe owns). Before promising card payments to a real Kenyan client, check these. All take cards and
M-Pesa; fees and terms change, so check each provider's own pricing page:

| Gateway | Notes from current sources (Nov 2026) |
| --- | --- |
| Paystack | Stripe-owned; widely praised API and docs; cards and M-Pesa, KES or USD settlement |
| Pesapal | Longest-running East African option; strong on M-Pesa and on physical card terminals |
| Flutterwave | Widest pan-African coverage (many currencies and methods); higher card fees in comparisons |
| DPO Pay | Strong in cross-border, travel and hospitality |
| IntaSend | Kenya-focused; M-Pesa and Airtel Money as well as cards |

Because our providers sit behind `PaymentProvider`, adding Paystack or Pesapal later is one new class
plus its webhook, the same shape as this one.

Sources: [IntaSend, "Is Stripe available in Kenya?"](https://intasend.com/payments/is-stripe-available-in-kenya-best-alternative-in-kenya);
[Red Stag, "How many countries does Stripe operate in?"](https://redstagfulfillment.com/how-many-countries-does-stripe-operate-in/);
[Stripe docs, Kenya (tax)](https://docs.stripe.com/tax/supported-countries/africa/kenya);
[Mctaba, "Choosing between Paystack, IntaSend, Pesapal and Flutterwave in Kenya"](https://mctaba.com/learn/paystack/choosing-between-paystack-intasend-pesapal-and-flutterwave-in-kenya);
[PhotonPay, "The 7 best payment gateways in Kenya"](https://www.photonpay.com/hk/blog/article/payment-gateways-in-kenya?lang=en);
[IntaSend, "Payment systems in Kenya"](https://intasend.com/payments/payment-systems-in-kenya-complete-guide-2026).
Most comparison sources are vendor blogs; Stripe's own list at stripe.com/global is the one to trust
for Stripe.

## Consequences

- The Done-when holds: switching a shop between FAKE and CARD is two clicks in the admin, and no code
  changes.
- Reading a card order's page makes one call to Stripe (for the client secret). It happens outside any
  database transaction. Frequent lookups (the live stream) never call Stripe.
- `result_code` is `varchar(10)`, so card failures use the short code `CARD_FAIL`.
- A real end-to-end card payment needs the test keys and `stripe listen` forwarding webhooks to the
  local backend. The automated tests cover the same paths against a pretend Stripe.
