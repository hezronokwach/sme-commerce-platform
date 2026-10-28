-- Tenant module: shops, their branding and the payment methods they have switched on.
-- Ids are UUIDs generated in Java before saving, so no column defaults on id.

CREATE TABLE tenant.tenants (
    id            uuid         NOT NULL,
    slug          varchar(50)  NOT NULL,
    name          varchar(120) NOT NULL,
    status        varchar(20)  NOT NULL DEFAULT 'ACTIVE',
    plan          varchar(20)  NOT NULL DEFAULT 'FREE',
    currency      char(3)      NOT NULL DEFAULT 'KES',
    contact_phone varchar(15),
    contact_email varchar(254),
    created_at    timestamptz  NOT NULL DEFAULT now(),
    updated_at    timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT pk_tenants PRIMARY KEY (id),
    CONSTRAINT uq_tenants_slug UNIQUE (slug),
    CONSTRAINT ck_tenants_slug_format CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    -- Reserved so no shop can take a path the platform uses.
    CONSTRAINT ck_tenants_slug_not_reserved CHECK (slug NOT IN ('admin', 'api', 'www', 's', 'login')),
    CONSTRAINT ck_tenants_status CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    CONSTRAINT ck_tenants_plan CHECK (plan IN ('FREE', 'STANDARD')),
    CONSTRAINT ck_tenants_currency CHECK (currency ~ '^[A-Z]{3}$')
);

CREATE TABLE tenant.tenant_branding (
    tenant_id     uuid         NOT NULL,
    primary_color char(7),
    accent_color  char(7),
    logo_url      text,
    tagline       varchar(160),
    updated_at    timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT pk_tenant_branding PRIMARY KEY (tenant_id),
    CONSTRAINT fk_tenant_branding_tenant FOREIGN KEY (tenant_id) REFERENCES tenant.tenants (id),
    CONSTRAINT ck_tenant_branding_primary_color CHECK (primary_color ~ '^#[0-9A-Fa-f]{6}$'),
    CONSTRAINT ck_tenant_branding_accent_color CHECK (accent_color ~ '^#[0-9A-Fa-f]{6}$')
);

CREATE TABLE tenant.tenant_payment_providers (
    id            uuid        NOT NULL,
    tenant_id     uuid        NOT NULL,
    provider      varchar(20) NOT NULL,
    enabled       boolean     NOT NULL DEFAULT false,
    display_name  varchar(60) NOT NULL,
    public_config jsonb       NOT NULL DEFAULT '{}'::jsonb,
    -- Encrypted in Java with a key from the environment. Never logged, never returned by an API.
    secret_config bytea,
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_tenant_payment_providers PRIMARY KEY (id),
    CONSTRAINT fk_tenant_payment_providers_tenant FOREIGN KEY (tenant_id) REFERENCES tenant.tenants (id),
    -- Also serves as the tenant_id index (tenant_id is the leading column).
    CONSTRAINT uq_tenant_payment_providers_tenant_provider UNIQUE (tenant_id, provider),
    CONSTRAINT ck_tenant_payment_providers_provider CHECK (provider IN ('MPESA', 'CARD', 'FAKE'))
);
