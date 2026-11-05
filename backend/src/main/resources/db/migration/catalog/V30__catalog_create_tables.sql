-- Catalog module: categories and products (what a shop sells and for how much).
-- Ids are UUIDs generated in Java before saving, so no column defaults on id.

CREATE TABLE catalog.categories (
    id         uuid        NOT NULL,
    tenant_id  uuid        NOT NULL,
    name       varchar(80) NOT NULL,
    slug       varchar(80) NOT NULL,
    sort_order int         NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_categories PRIMARY KEY (id),
    -- The same web name may exist in two shops, never twice in one. Also serves as the tenant_id index.
    CONSTRAINT uq_categories_tenant_slug UNIQUE (tenant_id, slug),
    -- Lets products point at (tenant_id, id) below, so a product can only use its own shop's category.
    CONSTRAINT uq_categories_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT ck_categories_slug_format CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$')
);

CREATE TABLE catalog.products (
    id          uuid         NOT NULL,
    tenant_id   uuid         NOT NULL,
    category_id uuid,
    name        varchar(120) NOT NULL,
    slug        varchar(140) NOT NULL,
    description text,
    -- Money: whole minor units (KES 50.00 is 5000) plus the currency. Never decimals or floats.
    price_minor bigint       NOT NULL,
    currency    char(3)      NOT NULL,
    image_url   text,
    status      varchar(20)  NOT NULL DEFAULT 'ACTIVE',
    -- Optimistic locking: every update adds 1; an update based on an older version is refused.
    version     int          NOT NULL DEFAULT 0,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT pk_products PRIMARY KEY (id),
    CONSTRAINT uq_products_tenant_slug UNIQUE (tenant_id, slug),
    -- Both columns, not just category_id: the category must exist AND belong to the same shop.
    -- When category_id is null the check is skipped, so uncategorised products are fine.
    CONSTRAINT fk_products_category FOREIGN KEY (tenant_id, category_id)
        REFERENCES catalog.categories (tenant_id, id),
    CONSTRAINT ck_products_slug_format CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    CONSTRAINT ck_products_price_not_negative CHECK (price_minor >= 0),
    CONSTRAINT ck_products_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_products_status CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED'))
);

-- Storefront list: one shop's ACTIVE products.
CREATE INDEX ix_products_tenant_status ON catalog.products (tenant_id, status);
-- Products of one category, and the "is this category still used?" check before deleting it.
CREATE INDEX ix_products_tenant_category ON catalog.products (tenant_id, category_id);
