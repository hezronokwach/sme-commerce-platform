# SME Commerce Platform: Database Design

Oct 26, 2026 · @destroyer

One PostgreSQL database with a separate schema for each module, 22 tables in all. Every table that holds a shop's data carries that shop's id. Tables only point at each other inside their own module; across modules they store the other record's id and nothing more.

## Conventions (apply to every table)

| Topic | Rule | Example |
| --- | --- | --- |
| Schemas | One schema per module, named after it | catalog.products, ordering.orders |
| Primary keys | id, a UUID generated in Java before saving (time-ordered UUID v7 if you add a small library) | 0192f3a4-... |
| Shop column | tenant\_id (UUID, not null) on every table holding a shop's data, children included | ordering.order\_lines.tenant\_id |
| Money | Whole cents as bigint, plus a 3-letter currency code. Never decimals or floats. | KES 50.00 is stored as 5000, currency KES |
| Time | timestamptz, stored in UTC; the frontends show Nairobi time | created\_at, updated\_at |
| Status values | varchar with a CHECK constraint listing the allowed values | status IN ('ACTIVE','DRAFT','ARCHIVED') |
| Optimistic locking | version (int) on rows that people or jobs update at the same time | products, stock\_items, orders, payments |
| Naming | snake\_case, plural table names, foreign keys end in \_id, indexes named ix\_table\_columns, unique constraints uq\_table\_columns | uq\_products\_tenant\_slug |
| Deleting | Business records are never hard-deleted. They get a status (ARCHIVED, CANCELLED) instead. | A product with orders is archived |
| Cross-module links | Store the other module's id as a plain column. No foreign key across schemas. | ordering.order\_lines.product\_id |

Types used below: uuid, text, varchar(n), int, bigint, boolean, timestamptz, jsonb, bytea (encrypted values) and vector (pgvector). "PK" means primary key, "FK" means foreign key inside the same module, "null" means the column may be empty.

## Tenant and identity tables

### tenant.tenants: one row per shop

| Column | Type | Rules |
| --- | --- | --- |
| id | uuid | PK. This is the tenant\_id every other table uses. |
| slug | varchar(50) | Unique. Lowercase letters, digits, hyphens. The shop's web name in /s/mamamboga. |
| name | varchar(120) | Not null. Shown on the storefront. |
| status | varchar(20) | ACTIVE or SUSPENDED. Suspended shops' storefronts show "shop not found". |
| plan | varchar(20) | FREE or STANDARD, for your subscription billing later. |
| currency | char(3) | Default KES. |
| contact\_phone, contact\_email | varchar | Shown on the storefront footer. |
| created\_at, updated\_at | timestamptz |  |

Index: uq\_tenants\_slug (slug). Reserve slugs such as admin, api, www, s, login so no shop can take them.

### tenant.tenant\_branding: one row per shop

| Column | Type | Rules |
| --- | --- | --- |
| tenant\_id | uuid | PK and FK to tenants.id |
| primary\_color, accent\_color | char(7) | Hex such as #2E7D32. Check the format. |
| logo\_url | text | null until uploaded |
| tagline | varchar(160) | null |
| updated\_at | timestamptz |  |

### tenant.tenant\_payment\_providers: which payment methods a shop has switched on

| Column | Type | Rules |
| --- | --- | --- |
| id | uuid | PK |
| tenant\_id | uuid | FK to tenants.id |
| provider | varchar(20) | MPESA, CARD or FAKE |
| enabled | boolean |  |
| display\_name | varchar(60) | What the customer sees, for example "M-Pesa" |
| public\_config | jsonb | Non-secret values: shortcode, till or paybill type, account reference prefix |
| secret\_config | bytea | Encrypted consumer key, secret, passkey or card keys. Never logged, never returned by any API. |
| created\_at, updated\_at | timestamptz |  |

Unique: uq\_tenant\_payment\_providers\_tenant\_provider (tenant\_id, provider).

### identity.staff\_users: shop owners and staff who use the admin

| Column | Type | Rules |
| --- | --- | --- |
| id | uuid | PK |
| tenant\_id | uuid | Not null. The shop they work for. |
| email | varchar(254) | Unique across the whole platform, stored lowercase, so login needs only email and password. |
| full\_name | varchar(120) |  |
| password\_hash | varchar(100) | bcrypt or argon2 hash, never the password |
| role | varchar(20) | OWNER or STAFF |
| status | varchar(20) | ACTIVE, INVITED or DISABLED |
| failed\_logins | int | For lockout after repeated failures |
| last\_login\_at | timestamptz | null |
| created\_at, updated\_at | timestamptz |  |

Indexes: uq\_staff\_users\_email (email), ix\_staff\_users\_tenant (tenant\_id).

### identity.refresh\_tokens: one row per issued refresh token

| Column | Type | Rules |
| --- | --- | --- |
| id | uuid | PK |
| tenant\_id | uuid |  |
| subject\_type | varchar(10) | STAFF or CUSTOMER |
| subject\_id | uuid | The staff user or customer it belongs to |
| token\_hash | char(64) | SHA-256 of the token. Unique. The raw token is only ever in the browser. |
| family\_id | uuid | All tokens from one login share this, so a reused token can revoke the whole family |
| expires\_at | timestamptz |  |
| used\_at, revoked\_at | timestamptz | null until used or revoked |
| created\_at | timestamptz |  |

Indexes: uq\_refresh\_tokens\_hash (token\_hash), ix\_refresh\_tokens\_family (family\_id).

### identity.customers: a shop's customers, guests included

| Column | Type | Rules |
| --- | --- | --- |
| id | uuid | PK |
| tenant\_id | uuid | Not null. A customer belongs to one shop. |
| phone | varchar(15) | Stored in 2547XXXXXXXX form |
| full\_name | varchar(120) |  |
| email | varchar(254) | null |
| password\_hash | varchar(100) | null for guests. Filled when they create an account (W9). |
| created\_at, updated\_at | timestamptz |  |

Unique: uq\_customers\_tenant\_phone (tenant\_id, phone). The same phone at two shops gives two separate rows.

## Catalog and inventory tables

### catalog.categories

| Column | Type | Rules |
| --- | --- | --- |
| id | uuid | PK |
| tenant\_id | uuid |  |
| name | varchar(80) |  |
| slug | varchar(80) | Unique within the shop |
| sort\_order | int | Order on the storefront menu |
| created\_at, updated\_at | timestamptz |  |

Unique: uq\_categories\_tenant\_slug (tenant\_id, slug).

### catalog.products

| Column | Type | Rules |
| --- | --- | --- |
| id | uuid | PK |
| tenant\_id | uuid |  |
| category\_id | uuid | FK to categories.id, null allowed |
| name | varchar(120) |  |
| slug | varchar(140) | Unique within the shop, used in the product page address |
| description | text | Also fed to the assistant |
| price\_minor | bigint | Cents. Check price\_minor >= 0. |
| currency | char(3) | Copied from the shop at creation |
| image\_url | text | null |
| status | varchar(20) | DRAFT, ACTIVE or ARCHIVED. Only ACTIVE shows on the storefront. |
| version | int | Optimistic locking |
| created\_at, updated\_at | timestamptz |  |

Indexes: uq\_products\_tenant\_slug (tenant\_id, slug); ix\_products\_tenant\_status (tenant\_id, status) for the storefront list; ix\_products\_tenant\_category (tenant\_id, category\_id).

### inventory.stock\_items: how many of each product exist

| Column | Type | Rules |
| --- | --- | --- |
| product\_id | uuid | PK. The catalog product's id, no FK across schemas. |
| tenant\_id | uuid |  |
| on\_hand | int | Physically in the shop. Check on\_hand >= 0. |
| reserved | int | Held for unpaid orders. Check 0 <= reserved <= on\_hand. |
| low\_stock\_threshold | int | Default 5. Triggers StockLow. |
| version | int | Optimistic locking: two checkouts can't both take the last item |
| updated\_at | timestamptz |  |

Available to sell = on\_hand minus reserved. It is calculated, not stored, so it can never drift.

### inventory.reservations: holds for unpaid orders

| Column | Type | Rules |
| --- | --- | --- |
| id | uuid | PK |
| tenant\_id | uuid |  |
| order\_id | uuid | The ordering module's order id |
| product\_id | uuid | FK to stock\_items.product\_id |
| quantity | int | Check quantity > 0 |
| status | varchar(20) | HELD, CONFIRMED or RELEASED |
| expires\_at | timestamptz | Created at plus 15 minutes |
| created\_at, updated\_at | timestamptz |  |

Indexes: uq\_reservations\_order\_product (order\_id, product\_id), which makes "reserve twice" impossible; ix\_reservations\_status\_expires (status, expires\_at) for the cleanup job.

### inventory.stock\_movements: the stock history, never edited

| Column | Type | Rules |
| --- | --- | --- |
| id | uuid | PK |
| tenant\_id | uuid |  |
| product\_id | uuid |  |
| change | int | +40 for a restock, -2 for a sale |
| reason | varchar(20) | RESTOCK, SALE, ADJUSTMENT, RETURN |
| reference | varchar(60) | Order number or note |
| actor\_id | uuid | Staff user, or null for the system |
| created\_at | timestamptz |  |

Index: ix\_stock\_movements\_tenant\_product\_created (tenant\_id, product\_id, created\_at). Rows are only ever inserted. on\_hand should always equal the sum of a product's movements, which makes a good test.

## Ordering and payments tables

### ordering.orders

| Column | Type | Rules |
| --- | --- | --- |
| id | uuid | PK |
| tenant\_id | uuid |  |
| order\_number | varchar(20) | Readable, such as MM-000123. Unique within the shop. |
| customer\_id | uuid | identity.customers id |
| customer\_name, customer\_phone | varchar | Copied at order time, so the order stays correct even if the customer later edits their details |
| status | varchar(20) | CREATED, PENDING\_PAYMENT, PAID, FULFILLED, CANCELLED, EXPIRED |
| payment\_method | varchar(20) | MPESA, CARD or FAKE |
| subtotal\_minor, total\_minor | bigint | Cents. Total equals subtotal for now; delivery fees later. |
| currency | char(3) |  |
| access\_token\_hash | char(64) | SHA-256 of the private order-link token, so guests can view their order without logging in |
| expires\_at | timestamptz | Payment deadline, 15 minutes after placing |
| version | int |  |
| created\_at, updated\_at | timestamptz |  |

Indexes: uq\_orders\_tenant\_number (tenant\_id, order\_number); ix\_orders\_tenant\_status\_created (tenant\_id, status, created\_at) for the admin list; ix\_orders\_tenant\_customer (tenant\_id, customer\_id); ix\_orders\_status\_expires (status, expires\_at) for the expiry job.

### ordering.order\_lines

| Column | Type | Rules |
| --- | --- | --- |
| id | uuid | PK |
| tenant\_id | uuid |  |
| order\_id | uuid | FK to orders.id |
| product\_id | uuid | catalog product id, no FK |
| product\_name | varchar(120) | Snapshot at purchase |
| unit\_price\_minor | bigint | Snapshot at purchase |
| quantity | int | Check > 0 |
| line\_total\_minor | bigint | unit price times quantity |

### ordering.order\_status\_history

| Column | Type | Rules |
| --- | --- | --- |
| id | uuid | PK |
| tenant\_id | uuid |  |
| order\_id | uuid | FK to orders.id |
| from\_status, to\_status | varchar(20) | from\_status null for the first row |
| reason | varchar(200) | "Payment succeeded, receipt QJK3..." |
| actor\_type | varchar(10) | SYSTEM, STAFF or CUSTOMER |
| actor\_id | uuid | null for SYSTEM |
| created\_at | timestamptz |  |

### ordering.order\_number\_counters

| Column | Type | Rules |
| --- | --- | --- |
| tenant\_id | uuid | PK |
| prefix | varchar(6) | Taken from the shop name, such as MM |
| next\_value | bigint | Starts at 1 |

Placing an order locks this one row, takes next\_value, and adds 1, all inside the order's transaction. Each shop's numbers run 1, 2, 3 with no gaps between shops.

### payments.payments: one row per payment attempt

| Column | Type | Rules |
| --- | --- | --- |
| id | uuid | PK |
| tenant\_id | uuid |  |
| order\_id | uuid | ordering order id. An order can have several attempts if earlier ones failed. |
| provider | varchar(20) | MPESA, CARD or FAKE |
| amount\_minor | bigint | Cents |
| currency | char(3) |  |
| payer\_phone | varchar(15) | For M-Pesa |
| status | varchar(20) | INITIATED, PENDING, SUCCEEDED, FAILED, TIMED\_OUT, UNKNOWN |
| provider\_reference | varchar(80) | Safaricom CheckoutRequestID or the card payment id. null until the provider accepts. |
| merchant\_request\_id | varchar(80) | Safaricom's second id, kept for support queries |
| receipt\_number | varchar(30) | M-Pesa receipt on success |
| result\_code | varchar(10) | Provider's code, such as 0, 1032 or 2001 |
| failure\_reason | varchar(200) | Friendly reason |
| check\_attempts | int | How many times reconciliation asked |
| next\_check\_at | timestamptz | When reconciliation should ask next |
| version | int |  |
| created\_at, updated\_at, completed\_at | timestamptz |  |

Indexes: uq\_payments\_provider\_reference (provider, provider\_reference), which is what makes callbacks safe to receive twice; a partial unique index uq\_payments\_one\_active (order\_id) WHERE status IN ('INITIATED','PENDING','UNKNOWN'), so an order can never have two live payments at once; ix\_payments\_status\_next\_check (status, next\_check\_at) for reconciliation.

### payments.callback\_log: every incoming callback, untouched

| Column | Type | Rules |
| --- | --- | --- |
| id | uuid | PK |
| provider | varchar(20) |  |
| provider\_reference | varchar(80) | null if it couldn't be read |
| raw\_body | jsonb | Exactly what arrived |
| outcome | varchar(30) | APPLIED, DUPLICATE, UNKNOWN\_REFERENCE, AMOUNT\_MISMATCH, INVALID |
| received\_at | timestamptz |  |

This table has no tenant\_id: a callback arrives before you know which shop it belongs to. Only payments code ever reads it. It is your best friend when debugging.

## Notification, assistant, audit and event tables

### notification.stream\_tickets: short-lived passes for live status

| Column | Type | Rules |
| --- | --- | --- |
| ticket\_hash | char(64) | PK. SHA-256 of the ticket. |
| tenant\_id | uuid |  |
| order\_id | uuid | The only order this ticket can watch |
| expires\_at | timestamptz | 60 seconds after issue |
| used\_at | timestamptz | Set on first use; a ticket works once |

Why it exists: the browser's built-in live-stream feature can't send a login token, so the storefront swaps its private order link for a one-time ticket and opens the stream with it. Delete rows older than a day with a daily job.

### assistant.documents: what each shop has given its assistant

| Column | Type | Rules |
| --- | --- | --- |
| id | uuid | PK |
| tenant\_id | uuid |  |
| title | varchar(160) | "Delivery policy" |
| source\_type | varchar(20) | UPLOAD, PRODUCT or POLICY |
| source\_id | uuid | Product id when source\_type is PRODUCT, else null |
| content\_hash | char(64) | Skip re-indexing when the text hasn't changed |
| status | varchar(20) | PROCESSING, READY or FAILED |
| chunk\_count | int |  |
| error | varchar(300) | null unless FAILED |
| created\_at, updated\_at | timestamptz |  |

Unique: uq\_documents\_tenant\_source (tenant\_id, source\_type, source\_id) for product documents, so each product has one entry.

### assistant.vector\_store: the searchable chunks

Spring AI's pgvector support can create this table for you; keep its default shape: id (uuid), content (text), metadata (json), embedding (vector, size set by your embedding model). Put tenant\_id and document\_id inside metadata, and always search with a tenant\_id filter. Add an HNSW index on embedding (Spring AI can create it) so searches stay fast.

Requires the pgvector extension: CREATE EXTENSION vector, in your first migration.

### assistant.usage\_daily: AI cost control

| Column | Type | Rules |
| --- | --- | --- |
| tenant\_id | uuid | PK part 1 |
| day | date | PK part 2 |
| questions | int |  |
| input\_tokens, output\_tokens | bigint | From the model's response |

Before answering, check today's row against the shop's daily limit.

### audit.audit\_log: who changed what

| Column | Type | Rules |
| --- | --- | --- |
| id | uuid | PK |
| tenant\_id | uuid |  |
| actor\_type | varchar(10) | STAFF, CUSTOMER or SYSTEM |
| actor\_id | uuid | null for SYSTEM |
| action | varchar(60) | PRODUCT\_PRICE\_CHANGED, STAFF\_INVITED, PAYMENT\_SETTINGS\_UPDATED |
| entity\_type, entity\_id | varchar(40), uuid | What was changed |
| details | jsonb | Before and after values. Never secrets. |
| ip\_address | varchar(45) |  |
| created\_at | timestamptz |  |

Index: ix\_audit\_log\_tenant\_created (tenant\_id, created\_at). Insert only. Audit isn't a business module; it's a small shared service any module can call.

### event\_publication: managed by Spring Modulith

Created by the Modulith JDBC event registry when you enable it (you can let it create its own schema or add it to a migration). Each row is one event waiting for one listener: the listener's name, the event's type and content, when it was published, and when it completed. Rows with no completion date are unfinished work. Don't write to this table yourself; just watch it.

With this, the design has 22 tables of your own plus Modulith's event table.

## How the tables connect

Inside a module, tables are joined with real foreign keys. Between modules, a table only stores the other record's id, and the code fetches anything else through that module's public API.

```
TENANT (tenant_id is in every row below)
  tenants 1--1 tenant_branding
  tenants 1--* tenant_payment_providers

IDENTITY
  staff_users 1--* refresh_tokens   (subject_id, STAFF)
  customers   1--* refresh_tokens   (subject_id, CUSTOMER)

CATALOG
  categories 1--* products

INVENTORY
  stock_items 1--* reservations
  stock_items 1--* stock_movements

ORDERING
  orders 1--* order_lines
  orders 1--* order_status_history
  order_number_counters: one row per shop

PAYMENTS
  payments, callback_log (linked by provider_reference)

CROSS-MODULE LINKS (id only, no foreign key)
  stock_items.product_id      -> catalog.products.id
  reservations.order_id       -> ordering.orders.id
  order_lines.product_id      -> catalog.products.id
  orders.customer_id          -> identity.customers.id
  payments.order_id           -> ordering.orders.id
  stream_tickets.order_id     -> ordering.orders.id
  documents.source_id         -> catalog.products.id
  every tenant_id             -> tenant.tenants.id
```

What happens to the cross-module links if a record disappears? Nothing is ever hard-deleted (products are archived, orders cancelled), so the ids stay valid forever. That is one of the reasons for the no-delete rule.

## Decisions explained

| Decision | Why | What it costs you | Alternative you could argue for |
| --- | --- | --- | --- |
| One database, one schema per module | Each module's tables are in their own space, so a lazy cross-module join stands out immediately. Still one database to run and back up. | More migration folders; Flyway must be told about every schema. | Separate databases per module, which is what microservices do. Too much to run for one person. |
| tenant\_id column on every shop table (shared tables, not a database per shop) | Thousands of small shops fit in one database. Adding a shop is one insert, not a new database. | One forgotten filter leaks data, so you need automatic filtering and cross-tenant tests. | A schema or database per shop: stronger walls, but migrations run once per shop and it gets slow and costly fast. |
| tenant\_id on child tables too (order\_lines, reservations) | Every query can filter by shop without joining the parent. Makes the automatic filter and future row-level security work the same everywhere. | One extra column and index per table. | Filtering children through their parent: less storage, more ways to get it wrong. |
| UUIDs, not counting numbers | Ids appear in links. A counting id (orders/124) lets anyone guess the next order. UUIDs can be created in Java before saving, which helps events. | Bigger indexes. Random UUIDs scatter inserts, which time-ordered v7 fixes. | bigint with a separate public code. |
| Readable order numbers alongside UUIDs | Customers and staff say "order MM-000123" on the phone, not a UUID. | A counter table with a row lock on each new order. | A random short code: no lock, but less friendly. |
| Money in whole cents (bigint) | Decimals in floating point give errors like 0.1 + 0.2 = 0.30000000000000004. Whole numbers never do. | Converting to shillings when displaying and when calling M-Pesa, which takes whole shillings. | numeric(12,2): also exact, but easier to mix with floats by mistake in Java. |
| Price and name copied into each order line | The order must show what the customer actually paid, even if the price changes tomorrow. | Duplicated data, on purpose. | Joining to the current product: wrong history the moment a price changes. |
| No foreign keys across modules | The database can't stop a module reaching into another's tables if they're tied together. Without the keys, each module could later move to its own database. | The database won't catch a wrong id across modules, so your code and events must. | Cross-schema foreign keys: safer data, but welds the modules together. |
| Never delete business records | Old orders keep pointing at real products and customers. History and audits stay complete. | Lists must filter by status; tables grow. | Hard delete with cascading: tidier tables, broken history. |
| Stock as on\_hand and reserved, plus a movement log | Holds for unpaid orders don't change real stock, and the log explains every number ("why do we have 37?"). | Two numbers to keep consistent; a version column to stop two checkouts overselling. | Only one stock number: simpler, but unpaid orders either oversell or wrongly reduce stock. |
| A unique index on the provider's payment reference | The database itself refuses a second payment with the same Safaricom id, so a doubled callback can't double-pay, even with two threads at once. | None worth mentioning. | Checking in code only: two simultaneous callbacks can both pass the check. |
| Partial unique index: one live payment per order | Stops two STK prompts for the same order when a customer double-taps Pay. | A retry must first finish the old attempt. | Locking the order row during payment start. |
| Raw callback log, separate from payments | When a payment looks wrong, you can see exactly what Safaricom sent. | Storage, so clean out rows older than 90 days. | Logging to files: lost on Render restarts. |
| Secrets encrypted in the database | A database leak alone doesn't reveal any shop's M-Pesa credentials. The key lives in an environment variable. | Key management: rotating the key means re-encrypting rows. | A secrets manager service: better, but more to set up. |
| Refresh tokens stored as hashes, grouped in families | A database leak doesn't hand out working tokens; a reused token reveals theft and kills the whole family. | A lookup on every refresh. | Stateless refresh tokens: no lookup, but no way to revoke them. |
| Staff email unique across the platform | Login needs only email and password; the shop comes from the user row. | A person can't use the same email to work at two shops. | Email unique per shop, with a shop picker at login. |
| Customers unique by phone within a shop | M-Pesa needs the phone anyway, so it's a natural key for guests; accounts can be added later without losing order history. | Two people sharing a phone share a customer record. | Customers only with accounts: more friction at checkout. |
| Vectors in Postgres (pgvector), not a separate vector database | One database to run; the shop filter works like any other query. | Fine for thousands of chunks; very large scale would need tuning. | A dedicated vector database: more to run and pay for. |

## Migration order and sample data

Create the tables in this order so nothing refers to a table that doesn't exist yet. Each module keeps its own Flyway folder; version numbers are shared across folders, so leave gaps between modules.

| Version | Module | Creates |
| --- | --- | --- |
| V1 | platform | Extensions (pgcrypto if wanted, vector), all schemas |
| V10 | tenant | tenants, tenant\_branding, tenant\_payment\_providers |
| V20 | identity | staff\_users, refresh\_tokens, customers |
| V30 | catalog | categories, products |
| V40 | inventory | stock\_items, reservations, stock\_movements |
| V50 | ordering | orders, order\_lines, order\_status\_history, order\_number\_counters |
| V60 | payments | payments, callback\_log |
| V70 | notification | stream\_tickets |
| V80 | assistant | documents, usage\_daily (vector\_store by Spring AI or here) |
| V90 | audit | audit\_log |
| V95 | Modulith | event\_publication, if you manage it yourself |

Rules: never edit a migration once it has run on Render; fix forward with a new version. Keep sample data out of these folders.

**Sample data for local development only.** Put it in a separate folder that only the local profile loads (for example db/seed-local), so it can never reach Render:

- Two shops: Mama Mboga (green, slug mamamboga) and Kamau Hardware (navy, slug kamauhardware).
- One OWNER and one STAFF user per shop, with known test passwords.
- 8 products per shop with stock, one of them at 1 item to test overselling.
- A FAKE payment provider enabled on both shops, M-Pesa sandbox on Mama Mboga.
- One delivery policy document per shop for the assistant.

Two shops from day one is deliberate: every screen and test you build is immediately checked against a second shop's data.
