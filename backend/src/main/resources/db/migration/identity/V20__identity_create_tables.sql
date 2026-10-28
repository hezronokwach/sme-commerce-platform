-- Identity module: staff users, customers and refresh tokens.
-- tenant_id points at tenant.tenants but has no foreign key: no constraints across module schemas.

CREATE TABLE identity.staff_users (
    id            uuid         NOT NULL,
    tenant_id     uuid         NOT NULL,
    email         varchar(254) NOT NULL,
    full_name     varchar(120) NOT NULL,
    password_hash varchar(100) NOT NULL,
    role          varchar(20)  NOT NULL,
    status        varchar(20)  NOT NULL DEFAULT 'INVITED',
    failed_logins int          NOT NULL DEFAULT 0,
    last_login_at timestamptz,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    updated_at    timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT pk_staff_users PRIMARY KEY (id),
    -- Unique across the platform so login needs only email and password.
    CONSTRAINT uq_staff_users_email UNIQUE (email),
    CONSTRAINT ck_staff_users_email_lowercase CHECK (email = lower(email)),
    CONSTRAINT ck_staff_users_role CHECK (role IN ('OWNER', 'STAFF')),
    CONSTRAINT ck_staff_users_status CHECK (status IN ('ACTIVE', 'INVITED', 'DISABLED')),
    CONSTRAINT ck_staff_users_failed_logins CHECK (failed_logins >= 0)
);

CREATE INDEX ix_staff_users_tenant ON identity.staff_users (tenant_id);

CREATE TABLE identity.customers (
    id            uuid         NOT NULL,
    tenant_id     uuid         NOT NULL,
    phone         varchar(15)  NOT NULL,
    full_name     varchar(120) NOT NULL,
    email         varchar(254),
    -- null for guests; filled when they create an account.
    password_hash varchar(100),
    created_at    timestamptz  NOT NULL DEFAULT now(),
    updated_at    timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT pk_customers PRIMARY KEY (id),
    -- The same phone at two shops gives two rows. Also serves as the tenant_id index.
    CONSTRAINT uq_customers_tenant_phone UNIQUE (tenant_id, phone),
    CONSTRAINT ck_customers_phone_format CHECK (phone ~ '^2547[0-9]{8}$'),
    CONSTRAINT ck_customers_email_lowercase CHECK (email = lower(email))
);

CREATE TABLE identity.refresh_tokens (
    id           uuid        NOT NULL,
    tenant_id    uuid        NOT NULL,
    subject_type varchar(10) NOT NULL,
    subject_id   uuid        NOT NULL,
    -- SHA-256 hex of the token. The raw token only ever lives in the browser.
    token_hash   char(64)    NOT NULL,
    family_id    uuid        NOT NULL,
    expires_at   timestamptz NOT NULL,
    used_at      timestamptz,
    revoked_at   timestamptz,
    created_at   timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_refresh_tokens PRIMARY KEY (id),
    CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT ck_refresh_tokens_subject_type CHECK (subject_type IN ('STAFF', 'CUSTOMER'))
);

CREATE INDEX ix_refresh_tokens_family ON identity.refresh_tokens (family_id);
