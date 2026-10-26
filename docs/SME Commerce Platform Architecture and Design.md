# SME Commerce Platform: Architecture and Design

The what and why behind the build. Read this fully before Day 1 of the 30-Day Plan. Code details live in the Steps and Pseudocode Guide.

## 1. Goals and non-goals

Goals: show enterprise-level skill in one coherent system: strict modular design, multi-tenant security, payments (M-Pesa plus a second provider), reliable events, realtime updates, an AI assistant, two frontends, and a real deployment.

Non-goals: microservices, Kubernetes, a mobile app, handling real customer money (sandbox and test modes only for the portfolio). Saying what you are not building is itself a senior habit.

Why a modulith: one deployable unit (simple to run and debug) with boundaries that are enforced by tests, so any module could later become a service. You get most of the design benefit of microservices without their operational cost.

## 2. The big picture

```
[Angular admin]        [React storefront]
        \                 /
      HTTPS REST + SSE / WebSocket
                 |
  +-------------------------------------+
  |  Spring Boot modulith (one app)     |
  |  tenant  identity  catalog          |
  |  inventory  ordering  payments      |
  |  notification  assistant            |
  +-------------------------------------+
      |             |              |
  PostgreSQL     (Redis,        External: Daraja,
  + pgvector     optional)      card gateway, LLM provider
```

## 2.1 The platform in plain words

One system, many shops. You build and run the system once; each small business rents a shop inside it, the way businesses rent online shops from Shopify. A shoe seller doesn't hire a developer: they sign up, add products, pick a colour and logo, connect M-Pesa, and they are selling the same day.

**Three kinds of people use it**

| Who | What they want | Where they work |
| --- | --- | --- |
| You (platform owner) | Sign businesses up, keep the system running, charge a monthly fee | Render dashboard and the database for now. A platform admin screen is a later feature. |
| Shop owner and staff (the tenant) | Sell products, track stock, see orders and money | Angular admin, after logging in |
| Customer | Buy from one shop and pay by M-Pesa | React storefront at yourplatform.com/s/\<shop-name>. No login needed. |

**What each shop has to itself:** products and prices, stock, orders, customers, branding (colour, logo, name), its M-Pesa paybill or till settings, and the documents its assistant learns from. No shop can ever see another shop's data.

**What every shop shares:** the code, the servers, the database, the payment integration, live updates, the assistant engine. You build these once and every shop benefits. This is the whole point: a small business couldn't afford to build any of it alone.

**Where the money goes:** the customer's payment goes straight to the shop's own paybill or till, through that shop's own Daraja credentials. It never passes through you. You earn from the shop's subscription. That is why each shop stores its own payment settings. (During development, every shop uses the one Daraja sandbox.)

**One customer, one shop at a time.** A customer opening /s/mamamboga sees only Mama Mboga's products. There is no page listing all shops in this version; a Jumia-style mixed marketplace would be a separate feature on top.

**How a request knows which shop it belongs to:**

- Admin requests: the shop is inside the owner's login token.
- Storefront requests: the shop comes from the shop name in the web address.
- Background work (events, scheduled jobs): the shop id travels inside the event or the database row.

Everything else in this doc builds on that one idea: every piece of data belongs to exactly one shop, and every request knows which shop it is acting for.

## 2.2 Shop owner workflows

Each workflow names the trigger, the steps, and the modules involved, so you can trace it through the code later. Example shop: Mama Mboga, owner Wanjiru.

### W1. A business joins the platform

Trigger: Wanjiru fills the sign-up form on the landing page (shop name, shop web name such as mamamboga, her name, email, phone, password).

1. **identity** receives the sign-up. It checks the email isn't already used.
2. **identity** calls **tenant**'s public API: create a shop named Mama Mboga with web name mamamboga. Tenant checks the web name is free and saves the shop as ACTIVE with default branding.
3. **identity** saves Wanjiru as a staff user with role OWNER, linked to the new shop id. Steps 2 and 3 run in one transaction: either both are saved or neither is.
4. **tenant** announces TenantCreated (used later for a welcome message).
5. Wanjiru is logged in and lands on the admin's setup checklist.

Why identity calls tenant, not the other way round: sign-up is account creation, and identity needs the shop id to save the owner. Calling in one direction only keeps the modules free of cycles. Passwords never travel inside events, because events are stored in the database.

### W2. The owner sets up the shop

Trigger: Wanjiru opens Settings in the admin.

1. **Branding:** she picks a colour and uploads a logo. **tenant** saves them. The storefront shows them on the next page load.
2. **Payments:** she enters her paybill or till details and Daraja credentials. **tenant** stores them encrypted and marks M-Pesa as enabled.
3. **Staff:** she invites a staff member. **identity** creates a STAFF user for the same shop. Staff can manage orders but not settings.
4. **Assistant knowledge:** she uploads her delivery policy. **assistant** saves it as PROCESSING, splits it into chunks, stores them tagged with her shop id, then marks it READY.

### W3. The owner adds a product

Trigger: Wanjiru adds "Sukuma bundle, KES 50" in the Products screen.

1. The admin sends the product. The shop id comes from her login token, never from the form, so she can't save into another shop.
2. **catalog** checks the data (name present, price not negative, web name unique within her shop) and saves it as ACTIVE.
3. **catalog** announces ProductCreated.
4. **inventory** hears it and creates a stock record for the product with 0 in stock.
5. **assistant** hears it and adds the product description to the shop's knowledge.
6. Wanjiru opens the Stock screen and adds 40. **inventory** records the change in its stock history ("restock +40 by Wanjiru").
7. The product now shows on her storefront, and only hers.

Why stock is a separate step: catalog owns what a product is, inventory owns how many exist. Each module owns one kind of fact.

### W4. Staff fulfil an order

Trigger: a paid order appears on the admin's live order list.

1. Staff pack the order and press "Mark as delivered".
2. **ordering** moves the order from PAID to FULFILLED and records who did it and when.
3. **ordering** announces OrderFulfilled. **notification** updates the customer's order page.

Stock was already deducted when payment succeeded, so fulfilment doesn't touch inventory.

## 2.3 Customer workflows

Example customer: Amina, buying from Mama Mboga.

### W5. Amina finds the shop and browses

1. Amina opens yourplatform.com/s/mamamboga, from a WhatsApp link or the shop's Instagram.
2. The storefront reads "mamamboga" from the address and asks the backend for that shop's public details.
3. **tenant** finds the shop by its web name and returns its name, logo, colours and enabled payment methods. If the shop doesn't exist or is suspended, the storefront shows "shop not found".
4. The storefront applies the colours and logo, then asks **catalog** for the shop's active products. Every product request carries the shop's web name, so the backend only returns that shop's products.
5. Amina adds 2 sukuma bundles to her cart. The cart lives only in her browser until checkout. No login, nothing saved on the server yet.

### W6. Amina checks out and pays with M-Pesa (the happy path)

1. **Checkout form:** Amina enters her name and phone and taps "Pay with M-Pesa".
2. **ordering** receives the cart and, for each line, asks **catalog** for the current price. It uses the server price, never the price the browser sent, so nobody can edit the price in their browser.
3. **ordering** asks **inventory** to hold 2 bundles for 15 minutes. If there isn't enough stock, checkout stops with "only N left".
4. **ordering** finds or creates a customer record for Amina's phone within Mama Mboga's shop, saves the order as PENDING\_PAYMENT, and gives it a readable number such as MM-000123.
5. **ordering** asks payments to record a new payment (nothing is sent to Safaricom yet), announces OrderPlaced, and returns the order number plus a private link to the order page.
6. **payments** waits until the order is safely saved, then reads Mama Mboga's M-Pesa settings from **tenant**, and asks Safaricom to send the PIN prompt to Amina's phone. The payment is saved as PENDING with Safaricom's request id.
7. Amina's screen opens a live connection to the order page and shows "Check your phone and enter your M-Pesa PIN".
8. Amina enters her PIN. Safaricom calls your callback address with the result.
9. **payments** finds the payment by Safaricom's request id, checks the amount matches, saves the M-Pesa receipt number, marks it SUCCEEDED, and announces PaymentSucceeded.
10. Three modules react to that one announcement:
    - **ordering** moves the order to PAID.
    - **inventory** turns the 15-minute hold into a real deduction, because ordering asks it to while marking the order paid.
    - **notification** pushes "Paid" to Amina's screen and adds the order to the admin's live list.

### W7. When payment goes wrong

| What happens | What the system does | What Amina sees |
| --- | --- | --- |
| She cancels the prompt or enters the wrong PIN | Safaricom reports failure. Payment FAILED, order CANCELLED, hold released. | "Payment didn't go through" and a "Try again" button |
| No callback arrives within 2 minutes | The reconciliation job asks Safaricom for the payment's status and applies the answer. | "Checking your payment..." until it resolves |
| Your request to Safaricom times out | Payment marked UNKNOWN, never FAILED. Reconciliation finds the truth. | "Checking your payment..." |
| She never pays at all | The 15-minute hold expires. A cleanup job releases the stock and cancels the order. | The order page shows "Expired" |
| The same callback arrives twice | The second one is ignored because the payment is already final. | Nothing changes |
| She closes the tab | Nothing breaks. The payment still completes on the server. | Her private order link shows the true status |

The rule behind all of these: never tell a customer "failed" when the truth is "we don't know yet".

### W8. Amina asks the assistant

1. She taps the chat bubble and asks "Do you deliver on Sundays?".
2. **assistant** checks the question length and her rate limit.
3. It searches only Mama Mboga's stored chunks for the closest matches. The shop filter is applied by the server, never chosen by the browser.
4. It sends the matches and her question to the AI model with the rule "answer only from this text; say you don't know otherwise".
5. She gets the answer with its source: "Yes, Sundays 9am to 1pm. Source: Delivery policy".

### W9. Amina creates an account (optional)

1. After paying, the order page offers "Save your details for next time".
2. She sets a password. **identity** adds it to her existing customer record, the one found by phone in W6 step 4.
3. Next time she can log in on Mama Mboga's storefront and see her past orders.

Customer accounts belong to one shop, like on Shopify. Her account at Mama Mboga doesn't exist at Kamau Hardware. That keeps each shop's customer list private to that shop.

## 3. Modules and their responsibilities

| Module | Owns | Public API others may use | Events it publishes | Events it listens to |
| --- | --- | --- | --- | --- |
| tenant | Shops, branding, payment settings | Find shop by web name, payment settings, create shop | TenantCreated | none |
| identity | Staff users, customers, tokens | Find or create customer, current user | none | none |
| catalog | Products, categories, prices | Price snapshots | ProductCreated, ProductChanged | none |
| inventory | Stock, reservations, stock history | Reserve, release, confirm | StockLow | ProductCreated |
| ordering | Orders, lines, order state, order numbers | Order summary | OrderPlaced, OrderPaid, OrderCancelled, OrderExpired, OrderFulfilled | TenantCreated, PaymentSucceeded, PaymentFailed |
| payments | Payments, callback log | Request payment, payment status | PaymentSucceeded, PaymentFailed | its own PaymentRequested |
| notification | Live streams, stream tickets | none | none | Order and payment events |
| assistant | Documents, chunks, usage | Ask a question | none | ProductCreated, ProductChanged |
| shared kernel | Money, TenantId, PhoneNumber, base event type | Small value types only | none | none |

The shared kernel must stay tiny and hold zero business logic. If you are tempted to add to it, the code probably belongs in a module.

## 4. The strictness rules (separation of concerns)

1. Modules talk in two ways only: calling another module's public API, or publishing and listening to events.
2. Only a module's base package is visible to others. Everything in sub-packages (like internal) is private. This is the Modulith default.
3. No entity crosses a boundary. Pass ids and small read-only records.
4. No cross-module database joins. Each module owns its own schema and tables.
5. No cycles. Ordering may call catalog and inventory. Payments never calls ordering; it only announces facts as events. The verification test fails if a cycle appears.
6. Controllers live inside their own module. There is no global controllers folder.

Decision rule for call versus event: use a call when you need an answer right now to continue (is there stock?). Use an event when you are announcing something that already happened (the payment succeeded).

Allowed dependencies at a glance:

| From | May call (public API) | Listens to events from |
| --- | --- | --- |
| ordering | catalog, inventory, identity, tenant, payments | tenant, payments |
| payments | tenant | nothing outside payments |
| inventory | nothing | catalog |
| notification | nothing | ordering, payments |
| assistant | catalog | catalog |
| identity | tenant | nothing |
| tenant | nothing | nothing |

**Why ordering conducts checkout.** Spring Modulith counts listening to another module's event as depending on that module. If payments listened for OrderPlaced while ordering listened for PaymentSucceeded, each would depend on the other: a cycle, which the boundary test rejects. So ordering calls inventory and payments directly, and only payment results come back as events. Every arrow then points one way.

How it is enforced: a verification test that builds the module model and fails the build on illegal access or cycles; generated module diagrams as documentation; optional ArchUnit rules for inside-module layering (for example, controllers may not touch repositories directly).

## 5. Inside one module

Every module follows the same shape so you never wonder where things go.

```
payments/
  PaymentApi            (public interface)
  PaymentSucceeded      (public event)
  internal/
    web/                (controllers, request and response records)
    application/        (service implementations, use cases)
    domain/             (aggregates, state rules)
    persistence/        (entities, repositories)
    providers/          (M-Pesa, card, fake)
```

Rules carried over from your earlier projects: DTOs as records, explicit service interfaces with implementations, manual mappers, entities never reach controllers.

## 6. Data design

- One PostgreSQL database, one schema per module. Flyway migrations live in per-module folders. Never edit a migration that has already run.
- Tenant isolation (default): every tenant-owned table has a tenant id, and queries are filtered automatically through a Hibernate filter fed by the tenant context from the token. Stretch option: Postgres row-level security as a second safety net.
- Money: store whole minor units as integers plus a currency code. Never floating point.
- Payments table: a unique constraint on the provider's reference (for example the checkout request id) is what makes callbacks idempotent.
- Event publication table: created by Modulith's registry, holds events until their listeners finish.
- Vector store table: embeddings plus metadata including tenant id.
- Audit table: who did what, when, to which record, for sensitive actions.

## 7. State machines

Order: CREATED, then PENDING\_PAYMENT, then PAID, then FULFILLED. From CREATED or PENDING\_PAYMENT it may go to CANCELLED. Nothing leaves FULFILLED or CANCELLED.

Payment: INITIATED, then PENDING (provider accepted it), then SUCCEEDED, FAILED, or TIMED\_OUT. A payment that is TIMED\_OUT with no answer goes to UNKNOWN, and reconciliation resolves UNKNOWN to SUCCEEDED or FAILED.

Implement transitions in one place per aggregate and make illegal moves throw. Orders also have EXPIRED (no payment within 15 minutes) and FULFILLED is reached when staff mark delivery (W4). Test every legal and illegal transition.

## 8. Payments design (the centrepiece)

Flow for M-Pesa:

1. Ordering calls payments' requestPayment inside its own order transaction, then publishes OrderPlaced for anyone else interested.
2. Payments creates a Payment (INITIATED) and picks the provider from the tenant's settings.
3. The provider adapter gets a cached access token, builds the STK request, sends it, and stores the checkout request id. State becomes PENDING.
4. The customer enters their PIN on their phone.
5. Safaricom calls your callback endpoint. Payments finds the payment by checkout request id, applies the result once, publishes PaymentSucceeded or PaymentFailed.
6. Ordering reacts to that event and tells inventory to confirm the held stock. Notification pushes it to the browser.

Rules that make it enterprise-grade:

- Provider interface: initiate, query status, parse callback. Fake, M-Pesa and card all implement it. Adding a provider must not touch ordering.
- Idempotency: the same callback twice changes nothing the second time.
- Callback hygiene: callbacks come from the internet, so use a hard-to-guess path segment, validate that the checkout id exists and is pending, compare the amount with what you stored, store the raw payload, and keep the webhook path in its own minimal security chain.
- Unknown is a real state. If no callback arrives, query the provider's status API. Safaricom callbacks can fail, so a scheduled reconciliation job is your safety net. Never show a customer 'failed' when the truth is 'unknown'; show 'checking your payment'.
- Sandbox versus live is configuration only: base URLs, keys and shortcodes come from environment settings.
- Credentials per tenant are secrets: encrypt at rest and never log them.
- Outbound calls get timeouts, limited retries (only for safe operations) and a circuit breaker.

Card provider: payment intent created on your server, confirmed by the customer in the browser, and finalised by a signed webhook that you verify before trusting. Stripe test mode is fine for the portfolio, but check which gateways actually operate for Kenyan businesses before promising it to a client.

## 9. Realtime design

- Customer storefront: Server-Sent Events. One direction (server to browser), simple, works well through proxies, automatic reconnect. A stream per order.
- Admin dashboard: WebSocket with STOMP for a live orders feed (stretch). Two-way, and a good thing to be able to explain.
- Both are fed by the notification module, which listens to events. It never reads other modules' tables.
- Gotcha: the browser's EventSource cannot send an Authorization header. Options: a short-lived one-time stream ticket from an authenticated call, or a fetch-based SSE client library. Pick one and record it as a decision.
- Scaling note (write it in the README, do not build it): with more than one app instance you need a shared broker to fan messages out to every instance.

## 10. AI assistant design

- Purpose (v1): answer shopper and staff questions from tenant-owned content: product descriptions, shipping and return policies, FAQs.
- Ingestion: split text into chunks, create embeddings, store them in pgvector with tenant id and source id as metadata. Re-index when ProductChanged arrives.
- Query: take the question, search with a mandatory tenant filter, build a prompt from the top matches, call the model, return the answer and its sources.
- Prompt rules: answer only from the supplied context; say you do not know otherwise; never reveal other tenants' content.
- Controls: question length cap, per-user rate limit, daily token budget per tenant, no personal customer data sent to the model.
- Provider abstraction: keep the model behind Spring AI's interfaces so you can swap a hosted model for a local one.
- Evaluation: a fixed set of about 15 questions with expected answers, rerun after every change. Report the score.
- Stretch (v2): let the assistant call your module APIs (for example order status for the logged-in customer) as tools. It goes through public APIs, never the database.

## 11. Security design

| Threat | Control |
| --- | --- |
| Stolen or forged token | Short access token, refresh token rotation, signature and expiry checks |
| Tenant data leak | Token carries tenant id, automatic query filter, cross-tenant tests, vector search filter |
| Fake payment callback | Secret path, pending-payment check, amount check, raw payload stored, reconciliation |
| Brute force login | Rate limiting, lockout or backoff |
| Cost abuse of the AI | Rate limit and token budget |
| Secrets in the repo | Environment variables only, git ignore, secret scanning in CI |
| Cross-site abuse | CORS locked to your two frontend domains, validated input |
| Who did that | Audit table for sensitive actions |

## 12. API surface (outline)

- identity: register, login, refresh, logout, me
- tenant: get and update settings, enable provider
- catalog: products and categories (list, get, create, update, delete)
- inventory: stock for a product, adjust stock
- ordering: place order, get order, list orders, cancel order
- payments: payment status for an order, provider callback endpoints (M-Pesa, card webhook)
- notification: order status stream, admin live feed
- assistant: ask, list documents, upload document
- platform: health, metrics, API description

Generate the OpenAPI description from the backend and generate typed API clients for both frontends from it. One source of truth, no hand-copied types.

## 13. Frontend architecture

Two apps, two jobs, one API.

|  | Angular admin | React storefront |
| --- | --- | --- |
| Users | Owner and staff | Customers |
| Main screens | Login, products, stock, orders, payments, settings, live feed | Browse, product, cart, checkout, payment status, assistant |
| Auth | Login, token held in memory, refresh handled in an interceptor | Guest checkout plus optional account |
| Realtime | WebSocket live feed (stretch) | SSE payment status |
| State | Services with signals or RxJS | Component state plus a data-fetching library |

Suggested balance for learning: Angular in Weeks 1 and 2, React from Week 3. Because both call the same API you will see the same ideas expressed twice, which is the fastest way to hold both in your head. Each app reads its API base URL from an environment setting, never hard-coded.

## 14. Environments and deployment

**Local (Docker)**

- Docker Compose runs Postgres with pgvector, optionally Redis and a mail catcher. Run the Spring app from your IDE for fast feedback; run the full containerised stack before each deploy.
- Use three configuration profiles: local, test, live. Secrets come from a git-ignored env file locally.
- For Daraja callbacks locally, run a tunnel tool that gives your machine a public HTTPS address, and register that as the callback URL.

**Backend and database on Render**

- Create a Render web service using the Docker runtime, pointing at your repo. Create Render Postgres in the same region and use its internal connection URL.
- Watch the URL format: Render gives a postgres:// style URL, but Spring needs the JDBC form (jdbc:postgresql://host/dbname) with user and password supplied separately. This catches almost everyone once.
- Render tells your app which port to listen on through an environment variable; make the server read it.
- Set the health check path to your Actuator health endpoint.
- Free instances sleep when idle, so the first request is slow and an early M-Pesa callback could time out. Use a paid starter instance for demo day and check Render's current free database limits and whether the pgvector extension is enabled on its Postgres before you rely on them.
- A render.yaml file (a Blueprint) lets you describe services in the repo. See the example repo in the resources.

**Frontends on Vercel**

- Create two Vercel projects from the same repo, each with its own root directory (admin-angular, storefront-react), build command and output folder.
- Set the API base URL as an environment variable per project.
- Single-page apps need a rewrite rule sending unknown paths to index.html, otherwise refreshing a deep link gives a 404.
- Add both Vercel domains to the backend's CORS allow list. Do not use a wildcard.

**Environment variables (names only, set in each platform)** Database URL, user and password; JWT signing secret; Daraja consumer key, secret, passkey, shortcode and callback base URL; card gateway keys and webhook secret; LLM provider key; allowed CORS origins; active profile.

**Deployment checklist**

1. Tests green in CI. 2. Image builds. 3. Render deploys and migrations run. 4. Health check passes. 5. Frontends point to the live API. 6. CORS verified from both domains. 7. Daraja callback URL updated and one sandbox payment completed live. 8. Logs show the correlation id for that payment.

## 15. Testing strategy

| Layer | What | Tool idea |
| --- | --- | --- |
| Unit | State machines, price and stock rules, mappers | JUnit, AssertJ |
| Module | One module with its real database | Modulith module tests plus Testcontainers |
| Event flows | Publish an event, assert the reaction | Modulith Scenario API |
| Architecture | Boundaries and cycles | Modulith verification, ArchUnit |
| Contract-ish | Recorded M-Pesa and card payloads | Stored sample files |
| End to end | Order to paid on the live app | Manual checklist, Playwright as stretch |

## 16. CI/CD

On every push: build, run all tests (Testcontainers needs Docker on the runner), build the image. On merge to main: Render and Vercel deploy from the repo automatically. Pull requests get Vercel preview URLs for the frontends.

## 17. Observability

Health and metrics through Actuator and Micrometer, structured logs with correlation id and tenant id on every line, an event publication check (any publication older than a few minutes is an alert), and payment counters by state.

## 18. Decisions log (starter entries)

| Decision | Why | Trade-off |
| --- | --- | --- |
| Modulith, not microservices | One deployable, enforced boundaries | Cannot scale modules independently |
| Schema per module | Prevents hidden joins | More migration folders |
| Tenant id column plus filter | Simple to learn and test | One missed filter is a leak, so test hard |
| Events for cross-module facts | Loose coupling, reliable with the registry | Eventual consistency, harder to trace |
| Provider interface for payments | Add providers without touching orders | One more abstraction to maintain |
| SSE for customers, WebSocket for admin | Right tool for each direction | Two realtime mechanisms to explain |
| RAG over tenant content only | Safe, cheap, explainable | Cannot answer live-data questions in v1 |
| Render plus Vercel | Familiar, quick | Free tier limits and cold starts |

## 19. Risks and mitigations

- Scope too big: tiers and cut order in the plan.
- M-Pesa sandbox flakiness: build with the fake provider first; keep recorded payloads for tests.
- Cold starts on free hosting: paid instance for demo day, health-check warm-up.
- Tenant leak: tests on every module that stores tenant data.
- Version mismatches between Spring Boot, Modulith and Spring AI: choose one matched generation on Day 1.

## 20. Resources

- Spring Modulith reference: https://docs.spring.io/spring-modulith/reference/ (Fundamentals, Verification, Events) and examples at https://github.com/spring-projects/spring-modulith
- Spring Security: https://docs.spring.io/spring-security/reference/
- WebSocket and SSE: https://docs.spring.io/spring-framework/reference/web/websocket.html and https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-async.html
- Postgres row-level security: https://www.postgresql.org/docs/current/ddl-rowsecurity.html. pgvector: https://github.com/pgvector/pgvector
- Spring AI: https://docs.spring.io/spring-ai/reference/. RAG tutorials: https://sohamkamani.com/java/spring-ai-rag-application and https://www.danvega.dev/blog/getting-started-with-spring-ai-rag
- M-Pesa: https://developer.safaricom.co.ke, https://allbusiness.africa/insights/integrate-mpesa-daraja-api-guide, https://paybillke.com/guides/daraja-api-mpesa-integration
- Stripe: https://docs.stripe.com/payments/payment-intents and https://docs.stripe.com/webhooks
- Render: https://render.com/docs. Spring plus Postgres on Render walkthrough: https://medium.com/@alamgir.ahosain/deploying-spring-boot-and-postgresql-to-render-9b4d597bbaaf. Example render.yaml with API and worker: https://github.com/ghorpadeire/render-auto-editor
- Vercel: https://vercel.com/docs. Testcontainers: https://testcontainers.com/guides/. ArchUnit: https://www.archunit.org
