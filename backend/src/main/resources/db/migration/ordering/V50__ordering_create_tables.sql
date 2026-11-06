-- Ordering module: orders, their lines, every status change, and each shop's order-number counter.
-- customer_id and product_id come from the identity and catalog modules: plain columns, no foreign
-- keys across schemas.

CREATE TABLE ordering.orders (
    id                uuid         NOT NULL,
    tenant_id         uuid         NOT NULL,
    -- Readable, such as MM-000123. Unique within the shop.
    order_number      varchar(20)  NOT NULL,
    customer_id       uuid         NOT NULL,
    -- Copied at order time, so the order stays correct even if the customer later edits their details.
    customer_name     varchar(120) NOT NULL,
    customer_phone    varchar(15)  NOT NULL,
    status            varchar(20)  NOT NULL DEFAULT 'CREATED',
    payment_method    varchar(20)  NOT NULL,
    -- Cents. Total equals subtotal for now; delivery fees come later.
    subtotal_minor    bigint       NOT NULL,
    total_minor       bigint       NOT NULL,
    currency          char(3)      NOT NULL,
    -- SHA-256 of the private order-link token, so a guest can view their order without logging in.
    -- Only the hash is stored; the token itself is given to the customer once.
    access_token_hash char(64)     NOT NULL,
    -- Payment deadline, 15 minutes after placing. Null while the order is still CREATED.
    expires_at        timestamptz,
    -- Optimistic locking: a payment and a cancel arriving together can't both win.
    version           int          NOT NULL DEFAULT 0,
    created_at        timestamptz  NOT NULL DEFAULT now(),
    updated_at        timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT pk_orders PRIMARY KEY (id),
    CONSTRAINT uq_orders_tenant_number UNIQUE (tenant_id, order_number),
    -- Lets lines and history point at (tenant_id, id), so they always belong to the same shop's order.
    CONSTRAINT uq_orders_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_orders_access_token_hash UNIQUE (access_token_hash),
    CONSTRAINT ck_orders_status CHECK (status IN ('CREATED', 'PENDING_PAYMENT', 'PAID', 'FULFILLED', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT ck_orders_payment_method CHECK (payment_method IN ('MPESA', 'CARD', 'FAKE')),
    CONSTRAINT ck_orders_phone_format CHECK (customer_phone ~ '^2547[0-9]{8}$'),
    CONSTRAINT ck_orders_subtotal CHECK (subtotal_minor >= 0),
    CONSTRAINT ck_orders_total CHECK (total_minor >= 0),
    CONSTRAINT ck_orders_currency CHECK (currency ~ '^[A-Z]{3}$')
);

-- The admin's order list: one shop's orders by status, newest first.
CREATE INDEX ix_orders_tenant_status_created ON ordering.orders (tenant_id, status, created_at);
CREATE INDEX ix_orders_tenant_customer ON ordering.orders (tenant_id, customer_id);
-- The expiry job: PENDING_PAYMENT orders whose deadline has passed.
CREATE INDEX ix_orders_status_expires ON ordering.orders (status, expires_at);

CREATE TABLE ordering.order_lines (
    id               uuid         NOT NULL,
    tenant_id        uuid         NOT NULL,
    order_id         uuid         NOT NULL,
    product_id       uuid         NOT NULL,
    -- Snapshot at purchase: a later rename or price change never alters a past order.
    product_name     varchar(120) NOT NULL,
    unit_price_minor bigint       NOT NULL,
    quantity         int          NOT NULL,
    line_total_minor bigint       NOT NULL,
    CONSTRAINT pk_order_lines PRIMARY KEY (id),
    CONSTRAINT fk_order_lines_order FOREIGN KEY (tenant_id, order_id) REFERENCES ordering.orders (tenant_id, id),
    CONSTRAINT ck_order_lines_unit_price CHECK (unit_price_minor >= 0),
    CONSTRAINT ck_order_lines_quantity CHECK (quantity > 0),
    -- The line total can never disagree with price times quantity.
    CONSTRAINT ck_order_lines_total CHECK (line_total_minor = unit_price_minor * quantity)
);

CREATE INDEX ix_order_lines_order ON ordering.order_lines (order_id);

CREATE TABLE ordering.order_status_history (
    id          uuid         NOT NULL,
    tenant_id   uuid         NOT NULL,
    order_id    uuid         NOT NULL,
    -- Null for the first row (the order was just created).
    from_status varchar(20),
    to_status   varchar(20)  NOT NULL,
    -- For example "Payment succeeded, receipt QJK3...".
    reason      varchar(200),
    actor_type  varchar(10)  NOT NULL,
    -- The staff user or customer who caused it; null for SYSTEM.
    actor_id    uuid,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT pk_order_status_history PRIMARY KEY (id),
    CONSTRAINT fk_order_status_history_order FOREIGN KEY (tenant_id, order_id) REFERENCES ordering.orders (tenant_id, id),
    CONSTRAINT ck_order_status_history_to CHECK (to_status IN ('CREATED', 'PENDING_PAYMENT', 'PAID', 'FULFILLED', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT ck_order_status_history_actor CHECK (actor_type IN ('SYSTEM', 'STAFF', 'CUSTOMER'))
);

-- An order's history, oldest first.
CREATE INDEX ix_order_status_history_order_created ON ordering.order_status_history (order_id, created_at);

CREATE TABLE ordering.order_number_counters (
    tenant_id  uuid       NOT NULL,
    -- Taken from the shop's web name: mama-mboga gives MM.
    prefix     varchar(6) NOT NULL,
    -- The number the next order gets. Placing an order locks this row, takes it and adds 1.
    next_value bigint     NOT NULL DEFAULT 1,
    CONSTRAINT pk_order_number_counters PRIMARY KEY (tenant_id),
    CONSTRAINT ck_order_number_counters_prefix CHECK (prefix ~ '^[A-Z0-9]{1,6}$'),
    CONSTRAINT ck_order_number_counters_next CHECK (next_value >= 1)
);
