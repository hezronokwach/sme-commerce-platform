# SME Commerce Platform: 30-Day Build Plan

A day-by-day guide for building your flagship Spring Modulith project. It pairs with two other docs: the Architecture and Design doc (the what and why) and the Steps and Pseudocode Guide (the how, in plain steps). You write every line of code yourself; these docs give steps, reasons and reading.

## How to use this plan

- Pace: about 3 focused hours on weekdays, 4 to 5 on weekends. Days 7, 14, 21 and 28 are buffer days. Use them to catch up, never to add scope.
- Every day has a 'Done when' line. If you cannot tick it, carry the leftover into the next buffer day.
- Keep a short log each day (what you built, what confused you, what you would do differently). Day 29 turns this into your write-up.
- Pick one Spring generation and stay on it. Spring Boot 4.x pairs with Spring Modulith 2.x and Spring AI 2.x; Boot 3.x pairs with the 1.x lines of those. Check start.spring.io and each project's release notes on Day 1 before you commit.

## Scope guard (read before Day 1)

One month is tight for this much. Work in three tiers.

- Must: modulith with enforced boundaries, tenant-safe auth, catalog, orders, payments with M-Pesa, live payment status (SSE), Angular admin, React storefront, deployment.
- Should: reliable events (event publication registry), reconciliation job, second payment provider, RAG assistant.
- Stretch: STOMP live admin feed, Postgres row-level security, tool-calling assistant, Playwright end-to-end tests.

If you fall behind, cut in this order: stretch items, second provider, STOMP (keep SSE). Never cut tests or the module boundary verification. Those are what make this look senior.

---

## Week 1: Foundation and strict modules (Days 1 to 7)

Read this week: Spring Modulith reference (https://docs.spring.io/spring-modulith/reference/), especially Fundamentals and Verification. Spring Security reference (https://docs.spring.io/spring-security/reference/). Flyway docs (https://flywaydb.org/documentation).

**Day 1: Design day, almost no code**

- Read the Architecture doc fully. Redraw the module map by hand in your own words.
- Create a monorepo with folders: backend, admin-angular, storefront-react, docs, infra.
- Generate the backend at start.spring.io (Web, Validation, Data JPA, PostgreSQL driver, Security, Actuator, Flyway, Modulith, Docker Compose support).
- Write a Docker Compose file with Postgres (use a pgvector-enabled image now so you do not migrate later). Done when: the app starts, connects to Postgres in Docker, and the Actuator health endpoint answers.

**Day 2: Module skeleton and the boundary test**

- Create one top-level package per module: tenant, identity, catalog, inventory, ordering, payments, notification, assistant, plus a very small shared kernel.
- Add a test that builds the application module model and verifies it.
- Break a rule on purpose (reach into another module's internal package), watch the test fail, then fix it.
- Generate the module documentation snippets and keep the output in /docs. Done when: the verification test passes and you have seen it fail for the right reason.

**Day 3: Database migrations per module**

- Decide: one Postgres schema per module (recommended) so modules cannot join each other's tables by accident.
- Create Flyway migration folders per module with a naming convention.
- Write migrations for tenant and identity tables. Done when: a clean database migrates from nothing on startup, and a second run changes nothing.

**Day 4: Identity and JWT**

- Registration and login endpoints, password hashing, access token plus refresh token.
- Build the security filter chain; roles: OWNER, STAFF, CUSTOMER.
- Add a separate, minimal filter chain for webhook paths (you will need it for payment callbacks). Done when: a protected endpoint rejects no token, a bad token, and an expired token, and accepts a good one.

**Day 5: Tenant context and isolation**

- Put the tenant id inside the token; read it into a request-scoped tenant context.
- Make every tenant-owned table carry a tenant id and make queries filter by it automatically (a Hibernate filter is the learning-friendly route).
- Write a test where tenant A tries to read tenant B's data. Done when: the cross-tenant test proves a leak is impossible through the normal API.

**Day 6: Catalog module**

- Product and category CRUD, scoped to tenant, with request and response DTOs, validation, and one global error handler returning a standard problem format.
- Expose an OpenAPI description (springdoc) so the frontends can generate API clients. Done when: you can create, list, update and delete products with Postman, and errors look the same everywhere.

**Day 7: Buffer plus Angular scaffold**

- Catch up first. If you are on track: create the Angular admin app, add routing, a login page, an HTTP interceptor for the token, and generate the API client from OpenAPI. Done when: you can log in from Angular and see the product list.

---

## Week 2: Orders and payments (Days 8 to 14)

Read this week: Daraja portal (https://developer.safaricom.co.ke), M-Pesa integration walkthrough (https://allbusiness.africa/insights/integrate-mpesa-daraja-api-guide), Daraja guide with callback and reconciliation advice (https://paybillke.com/guides/daraja-api-mpesa-integration). Compare your approach with the MpesaBridge example repo (https://github.com/felixojiambo/MpesaBridge) only after you have built your own.

**Day 8: Inventory module**

- Stock levels per product, and a reservation concept (hold stock for an unpaid order).
- Public API of the module: reserve, release, confirm. Nothing else is visible.
- Publish events: StockReserved, StockReleased. Done when: reserving more than available is rejected and two parallel reservations cannot oversell (test it).

**Day 9: Ordering module**

- Order and order line model; copy the price into the order at purchase time (a snapshot).
- Order state machine: CREATED, PENDING\_PAYMENT, PAID, FULFILLED, CANCELLED. Illegal jumps must be impossible.
- Placing an order calls catalog and inventory through their public APIs and publishes OrderPlaced. Done when: an order can be placed and moves to PENDING\_PAYMENT with stock reserved.

**Day 10: Angular admin: products and orders**

- Product list and form, order list and detail page, role-based route guards. Done when: staff can manage products and see orders in the admin app.

**Day 11: Payments with a fake provider first**

- Define a payment provider interface (initiate, query status, parse callback). Build a fake provider that always succeeds after a delay.
- When placing an order, ordering calls payments' requestPayment. Payments saves the payment, calls the provider after the transaction commits, and later publishes PaymentSucceeded or PaymentFailed. (Payments must not listen for OrderPlaced: that would make ordering and payments depend on each other.)
- Ordering listens, moves the order to PAID or CANCELLED, and calls inventory to confirm or release the stock. Done when: the whole order-to-paid flow works end to end with zero real money integration. This proves your architecture before the hard part.

**Day 12: M-Pesa part 1 (outbound)**

- Create a Daraja sandbox app. Get an access token and cache it until it expires.
- Build the STK Push request (shortcode, timestamp, generated password, amount, phone in 254 format, callback URL).
- Store the checkout request id on your payment record. Done when: your phone (or a sandbox test number) gets the prompt from your code.

**Day 13: M-Pesa part 2 (the callback)**

- Expose a public callback endpoint. Locally, use a tunnel tool so Safaricom can reach you.
- Handle the callback idempotently (the same callback may arrive twice). Map result codes to your payment states.
- Add the STK query call for when no callback arrives. Done when: success, cancel, wrong PIN and duplicate callbacks all leave the payment in the correct final state.

**Day 14: Buffer and payment tests**

- Write tests with a recorded callback payload: duplicate delivery, out-of-order delivery, unknown checkout id.
- Write down (in the ADR log) what your system does when the customer paid but you never got the callback. Done when: those tests pass and the note exists.

---

## Week 3: Reliable events, realtime, React storefront, second provider (Days 15 to 21)

Read this week: Spring Modulith events chapter (https://docs.spring.io/spring-modulith/reference/events.html). Spring Framework WebSocket (https://docs.spring.io/spring-framework/reference/web/websocket.html) and async requests with SseEmitter (https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-async.html). Stripe webhooks (https://docs.stripe.com/webhooks).

**Day 15: Reliable events**

- Add the event publication registry (JDBC variant) so events are saved in the same transaction as your business change.
- Switch cross-module listeners to the application module listener style (asynchronous, own transaction).
- Make a listener fail on purpose and watch the incomplete publication get retried. Done when: killing the app mid-flow and restarting does not lose an event.

**Day 16: Notification module plus SSE**

- Listens to payment and order events. Exposes a server-sent events stream for one order's status.
- Authorise the stream: only that order's owner (or staff of that tenant) may subscribe. Done when: a browser tab receives status changes with no polling.

**Day 17: React storefront scaffold**

- Create the React app: product list, product page, cart in client state, tenant chosen by a config value or URL slug.
- Generate the same OpenAPI client approach as Angular. Done when: a customer can browse a tenant's products and fill a cart.

**Day 18: Checkout and live payment status**

- Checkout form (phone number), call place-order, then open the SSE stream and show: waiting for PIN, paid, failed, and 'checking' (unknown) states.
- Never tell the customer a payment failed when the real state is unknown. Done when: you can pay from the storefront and watch the status change live.

**Day 19: Admin live feed (Stretch)**

- Add a STOMP-over-WebSocket feed of new orders to the Angular admin; authenticate during the handshake.
- If you are behind, skip and reuse SSE. Done when: a new order appears on the admin screen without refresh.

**Day 20: Second provider**

- Add a card provider behind the same interface (Stripe test mode works for a portfolio: payment intent plus signed webhook).
- Stripe availability for Kenyan businesses is limited, so for a real client check a Kenya-supported gateway too. The point here is proving your interface held. Done when: you can switch a tenant between M-Pesa and card without changing the ordering module at all.

**Day 21: Buffer plus reconciliation and timeouts**

- Scheduled job: find payments pending too long, query the provider, resolve them.
- Add timeouts and retries on outbound provider calls (a circuit breaker library is a good learning piece). Done when: a payment stuck in pending resolves itself.

---

## Week 4: AI, hardening, deployment, showcase (Days 22 to 30)

Read this week: Spring AI reference (https://docs.spring.io/spring-ai/reference/), RAG tutorial (https://sohamkamani.com/java/spring-ai-rag-application), Dan Vega's RAG guide (https://www.danvega.dev/blog/getting-started-with-spring-ai-rag), Render docs (https://render.com/docs), Vercel docs (https://vercel.com/docs).

**Day 22: Assistant part 1 (ingestion)**

- Enable the vector store on Postgres. Decide what is searchable: product descriptions, shop policies, FAQs.
- Ingest in pieces: split text into chunks, attach tenant id as metadata, store embeddings. Done when: documents for tenant A are stored with their tenant id.

**Day 23: Assistant part 2 (retrieval and chat)**

- Chat endpoint: take question, search with a tenant filter, build a prompt from the top matches, call the model, return answer plus the sources used.
- Add a hard limit on question length and a per-user rate limit. Done when: tenant A's assistant can never quote tenant B's documents (write that test).

**Day 24: Assistant UI plus evaluation**

- Add a chat panel to the storefront. Write 15 test questions with expected answers and score your assistant on them. Done when: you have a score you can quote in your README.

**Day 25: Hardening and observability**

- Rate limiting on login and payment endpoints, input validation review, CORS locked to your real domains, secrets only in environment variables.
- Structured logs with a correlation id and tenant id, metrics via Actuator and Micrometer, an audit log of sensitive actions. Done when: you can trace one order through the logs by its correlation id.

**Day 26: Containers and CI**

- Multi-stage Dockerfile for the backend. Full local Compose (app, Postgres, optional Redis).
- CI pipeline: build, run tests (Testcontainers), build image. Done when: pushing to main runs the pipeline green.

**Day 27: Deploy**

- Backend plus Postgres on Render; Angular and React as two Vercel projects. Set environment variables, CORS, and the public callback URL in Daraja.
- Watch for the Render database URL format (Spring needs the JDBC form). Done when: the live storefront completes a sandbox M-Pesa payment.

**Day 28: Buffer and live testing**

- Test the live system end to end. Fix cold-start and timeout surprises (free tiers sleep). Done when: the live demo works three times in a row.

**Day 29: Showcase**

- README with architecture diagram (use your generated module diagrams), decisions log, screenshots, a 3-minute demo video, and a short 'what I would do next' section. Done when: a stranger can understand the project in five minutes.

**Day 30: Review and publish**

- Clean the repo, remove dead code, write the portfolio and LinkedIn post, and list what you learned and what you will study next. Done when: the repo is public and the write-up is posted.

---

## Learning resources by topic

- Spring Modulith: https://docs.spring.io/spring-modulith/reference/ and the source repo with examples (https://github.com/spring-projects/spring-modulith). The repo's example folders cover the event publication registry and the outbox pattern.
- Security: https://docs.spring.io/spring-security/reference/
- Testcontainers guides: https://testcontainers.com/guides/
- M-Pesa: https://developer.safaricom.co.ke
- Payments (card): https://docs.stripe.com/payments/payment-intents
- Angular: https://angular.dev. React: https://react.dev
- Rate limiting: https://github.com/bucket4j/bucket4j. Resilience: https://resilience4j.readme.io/. Metrics: https://micrometer.io/docs
- Videos: search YouTube for Spring Modulith talks by Oliver Drotbohm (the project lead), Dan Vega and Josh Long for Spring AI, and Java Brains for Spring fundamentals. I could not verify individual video links, so search by those names and pick the newest ones.
