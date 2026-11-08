-- Payments module: one row per payment attempt, and every callback exactly as it arrived.
-- order_id comes from the ordering module: a plain column, no foreign key across schemas.

CREATE TABLE payments.payments (
    id                  uuid         NOT NULL,
    tenant_id           uuid         NOT NULL,
    -- An order can have several attempts if earlier ones failed (but only one live one, see below).
    order_id            uuid         NOT NULL,
    provider            varchar(20)  NOT NULL,
    -- Cents, copied from the order when the payment is requested.
    amount_minor        bigint       NOT NULL,
    currency            char(3)      NOT NULL,
    -- 2547XXXXXXXX. Never write it to the logs in full.
    payer_phone         varchar(15)  NOT NULL,
    status              varchar(20)  NOT NULL DEFAULT 'INITIATED',
    -- The provider's id for this attempt (M-Pesa CheckoutRequestID, card payment id, FAKE-...).
    -- Null until the provider accepts the request.
    provider_reference  varchar(80),
    -- Safaricom's second id, kept for support queries.
    merchant_request_id varchar(80),
    -- The provider's receipt on success, e.g. an M-Pesa receipt number.
    receipt_number      varchar(30),
    -- The provider's own result code, such as 0, 1032 or 2001.
    result_code         varchar(10),
    -- Words for people ("You cancelled the payment on your phone").
    failure_reason      varchar(200),
    -- For the reconciliation job (later issue): how often it asked the provider, and when to ask next.
    check_attempts      int          NOT NULL DEFAULT 0,
    next_check_at       timestamptz,
    -- Optimistic locking, on top of the row lock taken while applying a result.
    version             int          NOT NULL DEFAULT 0,
    created_at          timestamptz  NOT NULL DEFAULT now(),
    updated_at          timestamptz  NOT NULL DEFAULT now(),
    completed_at        timestamptz,
    CONSTRAINT pk_payments PRIMARY KEY (id),
    CONSTRAINT ck_payments_provider CHECK (provider IN ('MPESA', 'CARD', 'FAKE')),
    CONSTRAINT ck_payments_status CHECK (status IN ('INITIATED', 'PENDING', 'SUCCEEDED', 'FAILED', 'TIMED_OUT', 'UNKNOWN')),
    CONSTRAINT ck_payments_amount CHECK (amount_minor > 0),
    CONSTRAINT ck_payments_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_payments_phone_format CHECK (payer_phone ~ '^2547[0-9]{8}$'),
    CONSTRAINT ck_payments_check_attempts CHECK (check_attempts >= 0)
);

-- A provider reference belongs to one payment only. This is what makes a callback that arrives
-- twice harmless: both copies find the same single payment. (NULLs don't clash, so many payments can
-- wait for their reference at once.)
CREATE UNIQUE INDEX uq_payments_provider_reference ON payments.payments (provider, provider_reference);

-- At most one live payment per order: two "Pay" clicks can never start two charges. A partial index
-- only covers the rows matching its WHERE, so finished attempts (FAILED, SUCCEEDED) don't count.
CREATE UNIQUE INDEX uq_payments_one_active ON payments.payments (order_id)
    WHERE status IN ('INITIATED', 'PENDING', 'UNKNOWN');

-- The reconciliation job (later issue): which payments are due for a status check.
CREATE INDEX ix_payments_status_next_check ON payments.payments (status, next_check_at);
-- Looking up an order's attempts.
CREATE INDEX ix_payments_tenant_order ON payments.payments (tenant_id, order_id);

-- Every callback, stored untouched before anything else happens, so a wrong one can be investigated.
-- No tenant_id: a callback arrives before we know which shop it belongs to. Only payments reads it.
CREATE TABLE payments.callback_log (
    id                 uuid         NOT NULL,
    provider           varchar(20)  NOT NULL,
    -- Null if the body couldn't be read at all.
    provider_reference varchar(80),
    raw_body           jsonb        NOT NULL,
    -- What we did with it.
    outcome            varchar(30)  NOT NULL,
    received_at        timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT pk_callback_log PRIMARY KEY (id),
    CONSTRAINT ck_callback_log_provider CHECK (provider IN ('MPESA', 'CARD', 'FAKE')),
    CONSTRAINT ck_callback_log_outcome CHECK (outcome IN ('APPLIED', 'DUPLICATE', 'UNKNOWN_REFERENCE', 'AMOUNT_MISMATCH', 'INVALID'))
);

CREATE INDEX ix_callback_log_reference ON payments.callback_log (provider, provider_reference);
