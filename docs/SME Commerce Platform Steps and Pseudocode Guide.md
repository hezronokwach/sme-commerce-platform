# SME Commerce Platform: Steps and Pseudocode Guide

Version 2: every flow now has its inputs, the tables it touches, step-by-step pseudocode, the edge cases, and the tests that prove it. Still no Java: you turn each step into code yourself. Read it alongside the Architecture doc (the workflows W1 to W9) and the Database Design doc (the table names used here).

## How to read this guide

**Notation used in every pseudocode block**

| Word | Meaning |
| --- | --- |
| PROCEDURE name(inputs) | One use case. Usually one public service method. |
| IN TRANSACTION ... END | Everything inside saves together or not at all |
| x ← value | Store a value |
| IF / ELSE | A decision |
| FAIL ErrorName "message" | Stop and return a clear error (your global handler turns it into an HTTP response) |
| CALL module.method | A call to another module's public API |
| PUBLISH EventName{fields} | Announce something that happened, inside the current transaction |
| ON EventName | A listener in another module that reacts after the transaction commits |
| LOCK row | Read a row so nobody else can change it until this transaction ends |
| -- note | An explanation, not a step |

**Every request passes through the same layers**

1. **Security filter:** reads the token or the shop name, sets the shop context.
2. **Controller (web):** turns JSON into a request record, runs validation, calls the service. No business rules here.
3. **Service (application):** runs the use case: transaction, rules, calls to other modules, events.
4. **Domain object:** holds the rules that belong to one thing, such as "an order can only be paid from PENDING\_PAYMENT".
5. **Repository (persistence):** loads and saves. Entities never leave the module.
6. **Mapper:** turns entities into response records on the way out.

If you're unsure where a line of code belongs, ask: is it about HTTP (controller), about the order of steps (service), about what is allowed for this thing (domain), or about storage (repository)?

**Shared conventions**

- Money is always cents in a Money value (amount + currency). Shillings only appear on screen and in the M-Pesa request.
- Phone numbers are normalised to 2547XXXXXXXX the moment they enter the system.
- Every timestamp is UTC.
- Every event carries tenantId and an eventId, so listeners know the shop and can ignore duplicates.

## Part 1: Foundations

### 1.1 Project layout

```
backend/src/main/java/com/zelani/commerce/
  CommerceApplication          -- main class, root package
  shared/                      -- Money, TenantId, PhoneNumber, DomainEvent base. No logic.
  tenant/
    TenantApi                  -- public: findBySlug, getPaymentSettings, createTenant
    TenantCreated              -- public event
    internal/web | application | domain | persistence
  identity/      IdentityApi (findOrCreateCustomer), events
  catalog/       CatalogApi (getPriceSnapshots), ProductCreated, ProductChanged
  inventory/     InventoryApi (reserve, release, confirm), StockLow
  ordering/      OrderingApi (getOrderSummary), OrderPlaced, OrderPaid, OrderCancelled, OrderFulfilled
  payments/      PaymentsApi (getStatusForOrder), PaymentSucceeded, PaymentFailed
  notification/  (no public API; listens only)
  assistant/     AssistantApi (ask)
  audit/         AuditApi (record)
```

Rule of thumb: a type sits in the module's base package only if another module genuinely needs it. Start with everything in internal and move a type out when the boundary test tells you someone needs it.

### 1.2 Module boundary test

```
TEST modulesAreValid
  modules ← build module model from CommerceApplication
  modules.verify()          -- fails on internal access or cycles
  write diagrams to docs/modules
```

Practice: make ordering use a class from payments/internal, run the test, read the error message, revert. Do this once so you recognise the error later.

Expected dependency directions (anything else is a mistake):

- identity → tenant
- ordering → catalog, inventory, identity, tenant, payments. Ordering conducts checkout: it calls the others, and only payment results come back to it as events. If payments also listened for OrderPlaced, ordering and payments would depend on each other and the boundary test would fail.
- payments → tenant
- inventory → catalog only, through its ProductCreated event (listening to a module's event still counts as depending on it)
- assistant → catalog
- every module → shared, audit

### 1.3 Migrations

```
CONFIGURE Flyway
  locations ← one folder per module (db/migration/tenant, db/migration/identity, ...)
  schemas   ← all module schemas
  local profile also adds db/seed-local

ON application start
  Flyway applies any versions not yet applied, in version order
  IF a past script was edited → startup fails with a checksum error (good: it protects you)
```

### 1.4 One error shape everywhere

```
GLOBAL ERROR HANDLER
  ON validation failure       → 400, list of {field, message}
  ON NotFound                 → 404
  ON NotAllowed               → 403
  ON BusinessRuleBroken       → 409 or 422 with a code such as INSUFFICIENT_STOCK
  ON optimistic lock conflict → 409 CONCURRENT_UPDATE, "try again"
  ON anything else            → 500, generic message, full details only in the log with the correlation id
  ALWAYS include: code, message, correlationId, timestamp
```

Test: call one endpoint with bad input and one with an unknown id; both responses must have the same shape.

## Part 2: Identity and tenancy

### 2.1 Sign-up (workflow W1)

Inputs: shopName, shopSlug, ownerName, email, phone, password. Tables: tenant.tenants, tenant.tenant\_branding, ordering.order\_number\_counters (via event), identity.staff\_users.

```
PROCEDURE signUp(request)
  validate: all fields present, password at least 10 characters,
            slug matches [a-z0-9-]{3,50} and is not reserved
  email ← lowercase(request.email)
  IF staff user with email exists → FAIL EmailTaken

  IN TRANSACTION
    tenantId ← CALL tenant.createTenant(shopName, slug)
       -- inside tenant: IF slug taken → FAIL SlugTaken
       -- saves tenants row (ACTIVE) + tenant_branding row with defaults
       -- PUBLISH TenantCreated{tenantId, slug, shopName}
    user ← new StaffUser(tenantId, email, ownerName, hash(password), role OWNER, ACTIVE)
    save user
  END

  tokens ← issueTokens(user)       -- see 2.2
  RETURN tokens

ON TenantCreated (ordering)
  insert order_number_counters(tenantId, prefix ← initials of shopName, next_value 1)
```

Edge cases: two people grab the same slug at the same second (the unique index on slug makes one fail; turn the database error into SlugTaken); password hashing is slow by design, so do it before opening the transaction.

Tests: sign-up creates exactly one shop and one owner; a duplicate slug returns 409; a failure saving the user leaves no shop behind.

### 2.2 Login and tokens

```
PROCEDURE login(email, password)
  user ← find staff user by lowercase(email)
  IF user missing → run a dummy hash check (same timing), FAIL InvalidCredentials
  IF user.status ≠ ACTIVE → FAIL InvalidCredentials
  IF user.failed_logins ≥ 5 and last failure < 15 min ago → FAIL TooManyAttempts
  IF NOT passwordMatches(password, user.password_hash)
     increment failed_logins; FAIL InvalidCredentials
  reset failed_logins; set last_login_at
  RETURN issueTokens(user, newFamily ← true)

PROCEDURE issueTokens(user, family)
  access  ← sign token {sub: user.id, tid: user.tenant_id, role, type STAFF, exp: now + 15 min}
  raw     ← 64 random bytes as text
  save refresh_tokens{hash(raw), family_id, subject STAFF/user.id, expires now + 14 days}
  RETURN {access, refresh: raw}

PROCEDURE refresh(raw)
  row ← find refresh_tokens by hash(raw)
  IF row missing or expired or revoked → FAIL Unauthorized
  IF row.used_at is set               -- reused: likely stolen
     revoke every token in row.family_id; FAIL Unauthorized
  mark row used
  RETURN issueTokens(subject of row, same family)
```

Where the refresh token lives in the browser: an httpOnly, secure cookie limited to the refresh path is safest. The access token stays in memory only.

Tests: wrong password and unknown email give the same message; a reused refresh token kills the whole family; an expired access token returns 401.

### 2.3 Shop context: admin requests

```
FILTER adminAuth (paths /api/admin/**)
  token ← read "Authorization: Bearer ..."
  IF missing or invalid or expired → 401
  TenantContext.set(token.tid)
  SecurityContext.set(user id, role)
  continue the request
  FINALLY TenantContext.clear()     -- threads are reused; never skip this
```

### 2.4 Shop context: storefront requests

Storefront paths carry the shop name: /api/store/{slug}/products, /api/store/{slug}/orders.

```
FILTER storeContext (paths /api/store/{slug}/**)
  shop ← CALL tenant.findBySlug(slug)     -- cache for 5 minutes
  IF shop missing or SUSPENDED → 404
  TenantContext.set(shop.id)
  continue; FINALLY TenantContext.clear()

PROCEDURE getShopProfile(slug)       -- the first call the storefront makes
  RETURN {name, logoUrl, colors, tagline, enabled payment methods (display names only)}
```

Never return secret\_config or internal ids the storefront doesn't need.

### 2.5 Automatic shop filtering

```
FOR every entity with tenant_id
  on load or query: add "tenant_id = TenantContext.current" automatically (Hibernate filter)
  on insert: IF tenant_id empty → set from TenantContext
             IF tenant_id ≠ TenantContext → FAIL (bug, never allowed)

IF TenantContext is empty during a tenant-owned query → FAIL loudly
   -- better a crash in testing than a silent leak in production
```

Background work has no request, so listeners and jobs set the context themselves from the event or row, and clear it afterwards (Part 6).

Test to write first: seed two shops, call every list and get endpoint as shop B, assert nothing from shop A appears.

### 2.6 Guest customers and optional accounts (workflow W9)

```
PROCEDURE findOrCreateCustomer(tenantId, name, phone)   -- IdentityApi, used by ordering
  phone ← normalise(phone)                -- 0712..., +254712..., 254712... all → 254712...
  customer ← find customers by (tenantId, phone)
  IF missing → create {tenantId, phone, name}
  ELSE IF name changed → update name
  RETURN customer.id

PROCEDURE createAccount(orderAccessToken, password)
  order ← find order by hash(orderAccessToken); IF missing → 401
  customer ← customer of that order
  IF customer already has a password → FAIL AlreadyRegistered
  set password_hash; RETURN customer tokens (type CUSTOMER)
```

The private order token proves the person just placed that order, so no SMS code is needed for this first version. Mention a one-time SMS code as a future improvement in your README.

## Part 3: Catalog and inventory

### 3.1 Create and change a product (workflow W3)

Tables: catalog.products, audit.audit\_log.

```
PROCEDURE createProduct(request)            -- admin, OWNER or STAFF
  validate: name 1..120 chars, price ≥ 0, categoryId belongs to this shop (if given)
  slug ← request.slug OR slugify(name)
  IF product with (tenant, slug) exists → slug ← slug + "-2", "-3"... until free
  IN TRANSACTION
    product ← new Product(tenant, name, slug, description, Money(price, shop currency), DRAFT or ACTIVE)
    save product
    PUBLISH ProductCreated{tenantId, productId, name, description}
  END
  RETURN mapper.toResponse(product)

PROCEDURE updateProduct(id, request, expectedVersion)
  product ← load by id (shop filter applies; another shop's id → 404)
  IF product.version ≠ expectedVersion → FAIL ConcurrentUpdate
  IN TRANSACTION
    IF price changed → CALL audit.record(PRODUCT_PRICE_CHANGED, before, after)
    apply changes; save
    PUBLISH ProductChanged{tenantId, productId, name, description, status}
  END

PROCEDURE archiveProduct(id)  -- never delete
  status ← ARCHIVED; PUBLISH ProductChanged
```

**Public API for ordering:**

```
PROCEDURE getPriceSnapshots(tenantId, productIds) → list of {productId, name, priceMinor, currency, active}
  load products in one query (not one per id)
  RETURN only what ordering needs
```

Tests: another shop's product id returns 404, not 403 (don't reveal that it exists); an archived product is missing from the storefront list.

### 3.2 Stock record for a new product

```
ON ProductCreated (inventory)
  TenantContext.set(event.tenantId)
  IF stock_items for productId exists → stop       -- duplicate event, ignore
  insert stock_items{productId, tenantId, on_hand 0, reserved 0}
```

### 3.3 Adjust stock (owner restocks)

```
PROCEDURE adjustStock(productId, change, reason, note)
  IN TRANSACTION
    item ← load stock_items by productId
    IF item.on_hand + change < item.reserved → FAIL WouldBreakReservations
    item.on_hand ← item.on_hand + change     -- version check on save
    insert stock_movements{productId, change, reason, note, actor}
    IF available(item) ≤ item.low_stock_threshold → PUBLISH StockLow
  END
```

### 3.4 Reserve, release, confirm (InventoryApi)

```
PROCEDURE reserve(tenantId, orderId, lines[{productId, qty}])
  IN TRANSACTION (the caller's, so the order and the hold save together)
    FOR each line, sorted by productId        -- fixed order prevents deadlocks
      item ← load stock_items by productId
      IF item missing → FAIL ProductNotStocked
      IF item.on_hand - item.reserved < line.qty → FAIL InsufficientStock{productId, available}
      item.reserved ← item.reserved + line.qty   -- version check on save
      insert reservations{orderId, productId, qty, HELD, expires now + 15 min}
  END

PROCEDURE release(orderId)
  FOR each reservation of orderId with status HELD
    item.reserved ← item.reserved - qty
    reservation.status ← RELEASED
  -- calling it twice changes nothing the second time

PROCEDURE confirm(orderId)
  FOR each reservation of orderId with status HELD
    item.reserved ← item.reserved - qty
    item.on_hand  ← item.on_hand  - qty
    insert stock_movements{change -qty, SALE, reference order number}
    reservation.status ← CONFIRMED
```

**Choosing a locking strategy:** with optimistic locking (the version column), two checkouts for the last item both read reserved = 0; the first save wins and the second gets a version conflict. Catch it and retry the whole reserve once; the retry then sees no stock and fails cleanly. With pessimistic locking you'd lock the stock row while reading it, so the second checkout waits. Start optimistic and be ready to explain both.

**Who calls these:**

- reserve: ordering, synchronously, when placing an order (it needs the answer now).
- confirm: ordering, while marking the order PAID (4.4).
- release: ordering, while cancelling or expiring an order (3.5 and 4.4). Inventory never listens to ordering's events, which keeps the arrows one-way.

### 3.5 Expire unpaid holds

```
SCHEDULED every minute (ordering)
  orders ← PENDING_PAYMENT orders where expires_at < now, max 100
  FOR each order (set TenantContext from the order)
    status ← CALL payments.getStatusForOrder(order.id)
    IF status is PENDING or UNKNOWN → skip for now
       -- the customer may have paid; let reconciliation decide first
    IN TRANSACTION
      order.expire()
      CALL inventory.release(order.id)
      PUBLISH OrderExpired
    END
```

Tests: two parallel reservations for the last item, exactly one succeeds; release twice leaves stock correct; on\_hand always equals the sum of stock\_movements.

## Part 4: Ordering

### 4.1 Place an order (workflow W6, steps 1 to 5)

Inputs from the storefront: shop slug (in the path), lines \[{productId, quantity}\], customerName, customerPhone, paymentMethod. Tables: ordering.orders, order\_lines, order\_status\_history, order\_number\_counters, plus inventory.reservations through InventoryApi.

```
PROCEDURE placeOrder(request)                       -- TenantContext already set from the slug
  validate: 1..50 lines, each quantity 1..99, no product repeated,
            phone is a valid Kenyan mobile number,
            paymentMethod is enabled for this shop (CALL tenant.getEnabledProviders)

  snapshots ← CALL catalog.getPriceSnapshots(tenant, productIds)
  IF any product missing or not ACTIVE → FAIL ProductUnavailable{productId}
  -- prices come from the server, never from the request

  IN TRANSACTION
    customerId ← CALL identity.findOrCreateCustomer(tenant, customerName, customerPhone)
    number     ← nextOrderNumber(tenant)                  -- 4.2
    order      ← new Order(tenant, number, customer snapshot, paymentMethod)
    FOR each line
      order.addLine(productId, snapshot.name, snapshot.price, quantity)
    order.total ← sum of line totals
    order.placeAwaitingPayment(expiresAt ← now + 15 min)  -- CREATED → PENDING_PAYMENT
    rawToken ← random 32 bytes; order.access_token_hash ← hash(rawToken)
    save order (lines and first history row cascade)
    CALL inventory.reserve(tenant, order.id, lines)       -- may FAIL InsufficientStock → everything rolls back
    CALL payments.requestPayment(tenant, order.id, order.number, order.total,
                                 paymentMethod, customerPhone)
         -- payments only SAVES an INITIATED payment here and records its own
         -- PaymentRequested event; nothing is sent to Safaricom inside this transaction
    PUBLISH OrderPlaced{eventId, tenantId, orderId, orderNumber, totalMinor}
         -- for notification (admin live feed); payments doesn't need it
  END

  RETURN {orderNumber, orderId, accessToken: rawToken, status, total, expiresAt}
```

Why the reserve call sits inside the same transaction: if stock runs out, nothing is saved at all, not even a half-made order. Why requestPayment is called inside the same transaction: the payment row and its PaymentRequested event are saved together with the order, so every saved order is guaranteed to get a payment attempt, even if the app crashes a second later (Part 6).

The storefront keeps rawToken and builds the private order link: /s/mamamboga/orders/MM-000123?t=rawToken.

### 4.2 Readable order numbers

```
PROCEDURE nextOrderNumber(tenant)        -- must run inside the order's transaction
  LOCK order_number_counters row for tenant
  n ← row.next_value; row.next_value ← n + 1
  RETURN row.prefix + "-" + n padded to 6 digits      -- MM-000123
```

The lock means two orders at the same moment queue for a few milliseconds instead of getting the same number.

### 4.3 The order state machine

```
Order.placeAwaitingPayment   CREATED          → PENDING_PAYMENT
Order.markPaid(receipt)      PENDING_PAYMENT  → PAID
                             EXPIRED          → PAID   -- late payment: money arrived after the deadline
Order.cancel(reason)         CREATED | PENDING_PAYMENT → CANCELLED
Order.expire                 PENDING_PAYMENT  → EXPIRED
Order.fulfil(staffId)        PAID             → FULFILLED

EVERY transition
  IF current status not in the allowed "from" list → FAIL IllegalTransition{from, to}
  add order_status_history row {from, to, reason, actor}
```

The late-payment rule matters: if a customer pays after the order expired, you still have their money. Mark it PAID and alert staff to check stock (it may have been released), instead of ignoring real money.

### 4.4 Reacting to payments

```
ON PaymentSucceeded (ordering)
  TenantContext.set(event.tenantId)
  IN TRANSACTION
    order ← load event.orderId
    IF order.status = PAID or FULFILLED → stop             -- already handled
    wasExpired ← order.status = EXPIRED
    order.markPaid(event.receiptNumber)
    IF NOT wasExpired → CALL inventory.confirm(order.id)
    ELSE flag order for staff: "paid after expiry, check stock"
    PUBLISH OrderPaid{tenantId, orderId, orderNumber, totalMinor, lateAfterExpiry: wasExpired}
  END

ON PaymentFailed (ordering)
  IN TRANSACTION
    order ← load event.orderId
    IF order.status ≠ PENDING_PAYMENT → stop
    IF event.retryable                       -- cancelled prompt, wrong PIN
       keep PENDING_PAYMENT; the customer may press "Try again" before expiry
    ELSE
       order.cancel(event.reason)
       CALL inventory.release(order.id)
       PUBLISH OrderCancelled
  END
```

Retry flow: "Try again" on the order page calls a retryPayment endpoint, which checks the order is still PENDING\_PAYMENT and not expired, then calls payments.requestPayment again; payments starts a fresh attempt.

### 4.5 Fulfilment and viewing orders

```
PROCEDURE fulfil(orderId)              -- admin, OWNER or STAFF
  order.fulfil(currentStaffId); PUBLISH OrderFulfilled

PROCEDURE getOrderForCustomer(slug, orderNumber, rawToken)   -- storefront, no login
  order ← find by (tenant, orderNumber)
  IF order missing or hash(rawToken) ≠ order.access_token_hash → 404
  RETURN {status, lines, total, payment status, created time}

PROCEDURE listOrders(filters, page)    -- admin
  page size max 50; filter by status and date; newest first
```

Dependency note: ordering calls inventory and payments; payments and inventory never call or listen to ordering. Keep it that way and the boundary test stays green.

Tests: a tampered price in the request is ignored; out-of-stock rolls back the whole order; every illegal transition throws; a duplicate PaymentSucceeded leaves one history row; a wrong order token returns 404.

## Part 5: Payments

Check every Daraja field name below against the official portal (developer.safaricom.co.ke) before coding; they're listed here so you know what to look for.

### 5.1 The provider contract

```
CONTRACT PaymentProvider
  name()                                        -- MPESA, CARD, FAKE
  start(paymentRequest, shopSettings)  → StartResult{ACCEPTED + reference | REJECTED + reason | UNKNOWN}
  queryStatus(reference, shopSettings) → StatusResult{SUCCEEDED + receipt | FAILED + reason | PENDING}
  parseCallback(rawBody)               → CallbackResult{reference, outcome, receipt, amount, resultCode}

PaymentRequest: paymentId, amountMinor, currency, payerPhone, orderNumber
```

Nothing M-Pesa-specific (shortcode, passkey) appears in the contract. Each provider reads what it needs from shopSettings.

### 5.2 Starting a payment

```
PROCEDURE requestPayment(tenant, orderId, orderNumber, amount, provider, phone)   -- PaymentsApi
  -- runs inside ordering's transaction
  IF a live payment (INITIATED, PENDING, UNKNOWN) exists for orderId → FAIL PaymentInProgress
  payment ← new Payment(orderId, provider, amount, phone, status INITIATED)
  save payment          -- the partial unique index also blocks a second live payment
  PUBLISH PaymentRequested{tenantId, paymentId}   -- payments' own internal event

ON PaymentRequested (payments, runs after ordering's transaction commits)
  TenantContext.set(event.tenantId)
  payment ← load event.paymentId
  IF payment.status ≠ INITIATED → stop              -- duplicate delivery
  settings ← CALL tenant.getPaymentSettings(tenant, payment.provider)   -- decrypts secrets
  result   ← provider.start(request, settings)
       -- no DB transaction is open during this HTTP call
  IN TRANSACTION
    CASE result
      ACCEPTED → payment.pending(result.reference); next_check_at ← now + 90 s
      REJECTED → payment.fail(result.reason); PUBLISH PaymentFailed{retryable: true}
      UNKNOWN  → payment.unknown(); next_check_at ← now + 60 s
  END
```

This split is the key trick: the fast, safe part (saving) happens with the order; the slow, risky part (calling Safaricom) happens after commit, and the event registry retries it if the app dies in between.

### 5.3 M-Pesa: access token

```
PROCEDURE getAccessToken(settings)
  key ← settings.consumerKey + environment
  IF cache has key and it expires in more than 60 s → RETURN cached token
  response ← GET {baseUrl}/oauth/v1/generate?grant_type=client_credentials
             with Basic auth (consumer key and secret)
  cache token with its expiry (expires_in, usually about an hour)
  RETURN token
```

Cache per shop, because each shop has its own credentials.

### 5.4 M-Pesa: STK push (provider.start)

```
PROCEDURE mpesaStart(request, settings)
  timestamp ← now in Nairobi time as yyyyMMddHHmmss
  password  ← base64(shortcode + passkey + timestamp)
  body ← {
    BusinessShortCode: shortcode,
    Password, Timestamp,
    TransactionType: "CustomerPayBillOnline" (paybill) or "CustomerBuyGoodsOnline" (till),
    Amount: amountMinor / 100, as a whole number   -- refuse orders with cents for M-Pesa
    PartyA: payerPhone, PartyB: shortcode or till, PhoneNumber: payerPhone,
    CallBackURL: {publicBase}/webhooks/mpesa/{secretPathSegment},
    AccountReference: orderNumber (short; check the length limit),
    TransactionDesc: "Order " + orderNumber
  }
  TRY POST {baseUrl}/mpesa/stkpush/v1/processrequest with Bearer token, timeout 10 s
    IF ResponseCode = "0" → RETURN ACCEPTED(CheckoutRequestID), keep MerchantRequestID too
    ELSE → RETURN REJECTED(error message)
  ON timeout or connection error → RETURN UNKNOWN     -- it may have reached Safaricom
  ON 401 → clear cached token, retry once
```

### 5.5 M-Pesa: callback

```
ENDPOINT POST /webhooks/mpesa/{segment}        -- separate minimal security chain, no login
  IF segment ≠ configured secret → 404
  save callback_log{raw body, received_at, outcome PENDING}
  respond 200 with a small "accepted" JSON immediately
  hand the rest to processCallback (same request is fine at first; a queue later)

PROCEDURE processCallback(logId)
  cb ← read Body.stkCallback: CheckoutRequestID, ResultCode, ResultDesc,
        CallbackMetadata items: Amount, MpesaReceiptNumber, TransactionDate, PhoneNumber
  payment ← find by (MPESA, CheckoutRequestID)       -- no tenant filter yet: system lookup
  IF missing → log outcome UNKNOWN_REFERENCE; stop
  TenantContext.set(payment.tenant_id)
  applyResult(payment, result from cb)                 -- 5.6, shared with reconciliation
```

### 5.6 Applying a result (the one place payments change state)

```
PROCEDURE applyResult(payment, result)
  IN TRANSACTION
    LOCK payment row
    IF payment is SUCCEEDED or FAILED → outcome DUPLICATE; stop
    CASE result.code
      0 (success):
        IF result.amount ≠ payment.amount → outcome AMOUNT_MISMATCH; alert staff; stop
        payment.succeed(receipt); completed_at ← now
        PUBLISH PaymentSucceeded{tenantId, orderId, paymentId, receiptNumber, amountMinor}
      1032 (customer cancelled), 2001 (wrong PIN), 1 (insufficient balance):
        payment.fail(friendly reason); PUBLISH PaymentFailed{retryable: true, reason}
      1037 (phone unreachable or no response):
        payment.fail("We couldn't reach your phone"); PUBLISH PaymentFailed{retryable: true}
      other:
        payment.fail(ResultDesc); PUBLISH PaymentFailed{retryable: false}
    update callback_log outcome APPLIED
  END
```

The row lock plus the "already final" check means two copies of the same callback, arriving at the same moment on two threads, can only apply once.

### 5.7 Status query and reconciliation

```
PROCEDURE mpesaQueryStatus(reference, settings)
  POST {baseUrl}/mpesa/stkpushquery/v1/query
       {BusinessShortCode, Password, Timestamp, CheckoutRequestID: reference}
  IF the response says the transaction is still being processed → RETURN PENDING
  ELSE map ResultCode exactly as in 5.6

SCHEDULED every 30 seconds (payments)
  due ← payments with status in (PENDING, UNKNOWN) and next_check_at ≤ now, max 50
  FOR each (set TenantContext from the row)
    IF status = UNKNOWN and reference is empty
       -- the start call timed out before Safaricom gave us an id: we can't query it
       IF older than 3 min → payment.fail("Please try again"); PUBLISH PaymentFailed{retryable: true}
       continue
    result ← provider.queryStatus(reference, settings)
    IF result is final → applyResult(payment, result)
    ELSE
      check_attempts + 1
      next_check_at ← now + backoff(check_attempts)    -- 30 s, 1 min, 2 min, 5 min...
      IF age > 10 min → status TIMED_OUT (keep checking hourly for 24 h)
```

Nightly, compare the day's SUCCEEDED payments with the shop's M-Pesa statement, and list differences for a human to review. Build this last; it's a portfolio talking point more than a Week 3 need.

### 5.8 Card payments (Stripe test mode)

```
PROCEDURE cardStart(request, settings)
  create a PaymentIntent {amount: amountMinor, currency, metadata: {paymentId, tenantId, orderNumber}}
  RETURN ACCEPTED(intent id) and give the client_secret to the storefront

ENDPOINT POST /webhooks/card
  rawBytes ← read body as bytes BEFORE any JSON parsing
  IF signature header doesn't verify with the webhook secret → 400
  event ← parse rawBytes
  CASE event.type
    payment_intent.succeeded      → applyResult(payment by intent id, success)
    payment_intent.payment_failed → applyResult(payment by intent id, failure)
    anything else                 → ignore, 200
```

### 5.9 Fake provider (build first)

```
fakeStart → ACCEPTED("FAKE-" + random); schedule a fake callback in 3 s
  outcome chosen by the phone's last digit: 0 succeed, 1 cancelled, 2 wrong PIN, 3 never answers
fakeQueryStatus → same outcome by digit
```

The digit trick lets you test every path in W7 from the storefront without touching Safaricom.

Tests: duplicate callbacks apply once; a mismatched amount never marks paid; a timed-out start ends UNKNOWN and is resolved by reconciliation; a bad card signature returns 400; switching a shop from FAKE to MPESA needs no change in ordering.

## Part 6: Events and realtime

### 6.1 Reliable events

The problem: you save an order, then the app crashes before payments hears about it. The fix: Spring Modulith's event publication registry saves a row per listener in the same transaction as your business change, and marks it complete only after that listener succeeds.

```
SETUP
  add the Modulith JDBC event registry
  setting: republish outstanding events on restart = on
  setting: delete or archive completed publications after 7 days

PUBLISHING (inside a service transaction)
  PUBLISH SomeEvent → Spring writes one event_publication row per listener, same transaction
  IF the transaction rolls back → the rows vanish too: nothing is announced that didn't happen

AFTER COMMIT
  each @ApplicationModuleListener runs asynchronously, in its own new transaction
  IF it finishes → its row gets completion_date
  IF it throws  → row stays incomplete → retried on restart, or by your own retry job
```

**Your own retry job** (so you don't depend on restarts):

```
SCHEDULED every 2 minutes
  resubmit incomplete publications older than 1 minute (Modulith offers this)
  IF any publication is older than 15 minutes → log an ERROR alert with its listener and event type
```

### 6.2 Writing listeners that are safe to run twice

"At least once" delivery means every listener must cope with seeing the same event twice. Three patterns, used throughout this guide:

| Pattern | How | Used in |
| --- | --- | --- |
| Check the state first | If the thing is already done, stop | Ordering ON PaymentSucceeded (already PAID? stop) |
| Unique key | Let the database refuse a second insert | Inventory ON ProductCreated (stock row already exists) |
| Processed-event table | Store eventId; skip if present | Only if neither of the above fits |

```
TEMPLATE for every listener
  ON SomeEvent
    TenantContext.set(event.tenantId)
    TRY
      do the work, checking state first
    FINALLY
      TenantContext.clear()
```

Test with Modulith's Scenario API: publish an event in a test, wait for the expected outcome, assert. Then publish the same event twice and assert the outcome happened once.

### 6.3 Live payment status for the customer (SSE)

```
PROCEDURE issueStreamTicket(slug, orderNumber, rawOrderToken)     -- storefront
  order ← verify as in getOrderForCustomer (4.5)
  ticket ← random 32 bytes
  save stream_tickets{hash(ticket), tenant, order.id, expires now + 60 s}
  RETURN ticket

ENDPOINT GET /api/store/{slug}/orders/{orderNumber}/stream?ticket=...
  row ← find stream_tickets by hash(ticket)
  IF missing, expired, used, or for another order → 401
  mark row used
  emitter ← new SSE emitter, timeout 10 min
  registry.add(order.id, emitter)
  send event "status" with the CURRENT order and payment status   -- late joiners miss nothing
  ON emitter completion, timeout or error → registry.remove(order.id, emitter)

ON PaymentSucceeded / PaymentFailed / OrderPaid / OrderExpired (notification)
  FOR each emitter in registry.get(event.orderId)
    send event "status" {status, message}
    IF status is final → complete emitter

SCHEDULED every 20 s
  send a comment line ":keep-alive" to every open emitter    -- stops proxies closing idle streams
```

In the storefront: request a ticket, open EventSource with it, update the screen on each "status" event, and if the connection drops, request a new ticket and reconnect (a ticket works once).

Scaling note: the registry lives in one app's memory. With two app instances, the event might arrive on the instance that doesn't hold the customer's connection. Write in the README how you'd fix it (a shared broker such as Redis pub/sub); don't build it now.

### 6.4 Admin live order feed (stretch, STOMP over WebSocket)

```
SETUP
  WebSocket endpoint /ws, simple in-memory broker, topics /topic/tenant/{tenantId}/orders

ON CONNECT
  read the access token from the STOMP CONNECT headers
  IF invalid → reject the connection
  remember tenantId and role on the session

ON SUBSCRIBE to /topic/tenant/{x}/orders
  IF x ≠ session.tenantId → reject       -- staff only see their own shop

ON OrderPaid / OrderPlaced / OrderFulfilled (notification)
  send {orderNumber, status, total, time} to /topic/tenant/{event.tenantId}/orders
```

In Angular: connect after login, reconnect with growing delays (1 s, 2 s, 5 s, max 30 s), and merge each message into the orders list without reloading it.

Tests: a ticket can't be used twice; another shop's staff can't subscribe to your topic; the stream sends the current status straight after connecting.

## Part 7: Assistant

**The idea in one paragraph.** An embedding model turns a piece of text into a long list of numbers, so that texts with similar meaning get similar numbers. You store every chunk of a shop's text with its numbers. When a question arrives, you turn the question into numbers too, find the closest chunks (that is the vector search), and give only those chunks to the chat model with the question. This is called RAG: retrieval-augmented generation. The model answers from the shop's own text instead of guessing.

### 7.1 Ingestion: turning a document into searchable chunks

```
PROCEDURE uploadDocument(title, file)             -- admin, OWNER only
  IF file type not in (txt, md, pdf) or size > 2 MB → FAIL
  text ← extract text
  doc  ← save documents{tenant, title, UPLOAD, status PROCESSING, content_hash ← hash(text)}
  PUBLISH DocumentUploaded{tenantId, documentId}       -- indexing runs after commit
  RETURN doc (status PROCESSING)

ON ProductCreated / ProductChanged (assistant)
  text ← product name + description + price as a sentence
  IF status is ARCHIVED → removeChunks(document for this product); stop
  doc ← find or create documents{tenant, PRODUCT, source_id productId}
  IF doc.content_hash = hash(text) → stop               -- nothing changed, save the cost
  index(doc, text)

PROCEDURE index(doc, text)
  TenantContext.set(doc.tenant_id)
  TRY
    chunks ← split text into pieces of about 300 to 500 words, overlapping by about 50
    FOR each chunk
      metadata ← {tenant_id, document_id, title, source_type}
    delete existing vector rows where metadata.document_id = doc.id
    add chunks to the vector store       -- the framework calls the embedding model
    doc.status ← READY; doc.chunk_count ← count
  ON error
    doc.status ← FAILED; doc.error ← short message
```

Why delete then add: when a policy changes, old chunks must not linger and contradict the new one.

### 7.2 Answering a question

```
PROCEDURE ask(slug, question, sessionKey)             -- storefront, no login
  IF length(question) > 500 chars → FAIL TooLong
  IF rate limit for (shop, sessionKey or IP) exceeded (e.g. 10 per minute) → FAIL TooManyRequests
  usage ← usage_daily for (tenant, today)
  IF usage.questions ≥ shop's daily limit → RETURN "The assistant is resting for today. Contact the shop at {phone}."

  matches ← vector search: query = question, top 4,
            filter = metadata.tenant_id == current tenant,          -- set by the SERVER, never the browser
            minimum similarity = a threshold you tune (start around 0.5)
  IF matches is empty
     RETURN "I don't have that information. You can reach the shop at {phone}."   -- no model call, no cost

  prompt ←
    SYSTEM: "You are the shop assistant for {shop name}. Answer ONLY using the context.
             If the answer isn't in the context, say you don't know and suggest contacting the shop.
             Never invent prices, stock or delivery promises. Keep answers under 80 words."
    CONTEXT: each match's text, labelled with its title
    USER: question
  reply ← call the chat model (low temperature, max output about 200 tokens)

  record usage: questions + 1, input and output token counts from the reply
  RETURN {answer: reply text, sources: distinct titles of the matches used}
```

**Things to tune and record in your README:** chunk size, overlap, top-K, the similarity threshold, and the prompt. Change one at a time and re-run the evaluation.

### 7.3 Evaluation

```
FILE assistant-eval.csv: question, must_contain, must_not_contain, shop
  "Do you deliver on Sundays?", "9am", "", mamamboga
  "What is your return policy?", "7 days", "", mamamboga
  "Do you sell cement?", "don't", "KES", mamamboga          -- not their product: must refuse
  "What does cement cost?", "750", "", kamauhardware
  "What are Kamau Hardware's prices?", "don't", "750", mamamboga   -- cross-shop leak test

TEST runEvaluation
  FOR each row: ask as that shop; check must_contain present and must_not_contain absent
  report: passed / total
```

Write about 15 rows. A score like "14 of 15, including every cross-shop leak test" is a concrete line for your portfolio.

### 7.4 Stretch: tool calling

Let the model ask your system for live facts, such as "where is my order?". Give it one tool, getOrderStatus(orderNumber), that runs through OrderingApi with the current shop and the customer's order token. The model never touches the database; it only calls the tool you expose, with the same checks as any other request.

## Part 8: Platform

### 8.1 Branding and shop settings (workflow W2)

```
PROCEDURE updateBranding(primaryColor, accentColor, tagline)    -- OWNER only
  validate colours match #RRGGBB; tagline ≤ 160 chars
  save tenant_branding; CALL audit.record(BRANDING_UPDATED)
  evict the cached shop profile for this slug     -- storefront sees it on next load

PROCEDURE uploadLogo(file)
  IF not PNG/JPG/WebP or > 1 MB → FAIL
  store the file (object storage such as Cloudflare R2 or Cloudinary; Render's disk is wiped on deploy)
  save logo_url

PROCEDURE savePaymentSettings(provider, publicConfig, secrets)   -- OWNER only
  validate required fields per provider
  secret_config ← encrypt(secrets with the key from an environment variable)
  upsert tenant_payment_providers; audit.record(PAYMENT_SETTINGS_UPDATED) with NO secret values
  RETURN settings with secrets masked ("•••• last 4")
```

In the storefront: on load, fetch the shop profile and set CSS variables (--brand, --accent) on the page root. Every button and header uses those variables, so one fetch re-skins the whole shop.

### 8.2 Hardening checklist

1. Rate limits: login 5 per minute per IP; place order 10 per minute per phone; assistant as in 7.2.
2. Validation on every request record; maximum page size 50; maximum lines per order 50.
3. CORS: only your two Vercel domains (and localhost in the local profile).
4. Secrets only from environment variables; a git-ignored .env locally; a secret scanner in CI.
5. Logs: a correlation id on every line (generated per request, returned in a response header), plus tenant id once known. Never log tokens, passwords, secrets or full phone numbers (mask the middle digits).
6. Actuator: health public; everything else locked or turned off.
7. Audit: price changes, staff invites, settings changes, manual stock adjustments.

### 8.3 Docker

```
docker-compose.yml (local)
  postgres: image with pgvector, port 5432, named volume, health check
  app (profile "full"): built from the Dockerfile, depends on healthy postgres, env from .env

Dockerfile (two stages)
  stage build: JDK image → copy build files → download dependencies (cached layer) → copy src → package jar
  stage run:   small JRE image → create non-root user → copy only the jar → expose $PORT → run the jar
```

Daily use: run only Postgres in Docker and the app from your IDE. Before each deploy, run the full stack in Docker to test the exact image Render will run.

### 8.4 CI (GitHub Actions)

```
ON push and pull request
  set up Java 21 with dependency cache
  run all tests (Testcontainers starts its own Postgres; the runner has Docker)
  run the module boundary test (part of the tests)
  build the Docker image
  FAIL the run on any red test
```

### 8.5 Render (backend and database)

1. Create the Render Postgres first, in the same region you'll use for the app. Check whether you can run CREATE EXTENSION vector on it before relying on the assistant there.
2. Create a Web Service from the repo: Docker runtime, root directory backend.
3. Environment variables: database URL in JDBC form (jdbc:postgresql://host:5432/db) plus user and password separately; JWT secret; encryption key; Daraja sandbox values; card test keys and webhook secret; LLM key; CORS origins; active profile prod.
4. Make the server listen on the PORT variable Render provides.
5. Health check path: the Actuator health endpoint.
6. Deploy and read the logs: Flyway versions applied, app started, health UP.
7. Set the Daraja callback base to the Render URL; run one sandbox payment end to end.

Guides: [render.com/docs](https://render.com/docs) and a [Spring Boot plus Postgres on Render walkthrough](https://medium.com/@alamgir.ahosain/deploying-spring-boot-and-postgresql-to-render-9b4d597bbaaf).

### 8.6 Vercel (two frontends)

1. Import the repo twice: one project with root admin-angular, one with root storefront-react.
2. Set each project's build command, output folder and API base URL variable; redeploy after changing variables (they're baked in at build time).
3. Add a rewrite sending all unknown paths to index.html, so refreshing /s/mamamboga/orders/MM-000123 works.
4. Copy both final domains into the backend's CORS list and redeploy the backend.

### 8.7 Frontend flows

**Angular admin**

1. Generate the typed API client from the backend's OpenAPI description.
2. Login page; access token in memory; an interceptor adds it to every call; on 401 it calls refresh once, then retries; if refresh fails, back to login.
3. Route guards: OWNER-only for Settings and Staff.
4. Screens: Orders (live), Products, Stock, Payments, Settings (Branding, Payments, Assistant knowledge, Staff).
5. Show every payment state, including "checking" for UNKNOWN.

**React storefront**

1. Route /s/:slug loads the shop profile first; show "shop not found" on 404.
2. Pages: product list, product detail, cart, checkout, order page, assistant bubble.
3. Cart in state plus localStorage (wrapped in try/catch), keyed by shop slug so two shops' carts never mix.
4. Checkout: validate the phone, place the order, save the order link, open the live status (6.3).
5. Order page states: waiting for PIN, checking, paid, failed (with Try again), expired.

### 8.8 Testing map

| What | Where in this guide | Kind of test |
| --- | --- | --- |
| Module boundaries | 1.2 | Modulith verify |
| Shop isolation on every endpoint | 2.5 | Integration with two seeded shops |
| Token reuse kills the family | 2.2 | Integration |
| Last-item race | 3.4 | Two threads, real Postgres (Testcontainers) |
| Order rollback on no stock | 4.1 | Integration |
| Every illegal state change | 4.3 | Unit |
| Duplicate and concurrent callbacks | 5.6 | Integration with recorded payloads |
| Timeout resolved by reconciliation | 5.7 | Integration with the fake provider |
| Event survives a crash | 6.1 | Modulith Scenario, listener that fails once |
| Stream ticket single use | 6.3 | Integration |
| Assistant leak and accuracy | 7.3 | Evaluation file |

### 8.9 When you get stuck

1. Write the problem in one sentence: what you expected, what happened.
2. Find the part of this guide and the reference docs for that feature; read them slowly.
3. Shrink it to the smallest test that shows the problem.
4. Search the framework's GitHub issues with the exact error message.
5. Then ask, bringing the small test and the full error.
