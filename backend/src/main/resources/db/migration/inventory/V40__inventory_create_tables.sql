-- Inventory module: how many of each product exist, holds for unpaid orders, and the stock history.
-- product_id and order_id come from the catalog and ordering modules: plain columns, no foreign keys
-- across schemas.

CREATE TABLE inventory.stock_items (
    -- One row per product, so the catalog product's id is the primary key.
    product_id          uuid        NOT NULL,
    tenant_id           uuid        NOT NULL,
    -- Physically in the shop.
    on_hand             int         NOT NULL DEFAULT 0,
    -- Held for unpaid orders. Available to sell = on_hand - reserved: calculated, never stored.
    reserved            int         NOT NULL DEFAULT 0,
    -- When available drops to this or below, StockLow is announced.
    low_stock_threshold int         NOT NULL DEFAULT 5,
    -- Optimistic locking: two checkouts can't both take the last item.
    version             int         NOT NULL DEFAULT 0,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_stock_items PRIMARY KEY (product_id),
    -- Lets reservations point at (tenant_id, product_id), so a hold can only use its own shop's stock.
    CONSTRAINT uq_stock_items_tenant_product UNIQUE (tenant_id, product_id),
    CONSTRAINT ck_stock_items_on_hand CHECK (on_hand >= 0),
    -- The rule that makes overselling impossible, even if the Java code had a bug.
    CONSTRAINT ck_stock_items_reserved CHECK (reserved >= 0 AND reserved <= on_hand),
    CONSTRAINT ck_stock_items_threshold CHECK (low_stock_threshold >= 0)
);

CREATE TABLE inventory.reservations (
    id          uuid        NOT NULL,
    tenant_id   uuid        NOT NULL,
    order_id    uuid        NOT NULL,
    product_id  uuid        NOT NULL,
    quantity    int         NOT NULL,
    status      varchar(20) NOT NULL DEFAULT 'HELD',
    -- Placed at + 15 minutes. Ordering's expiry job releases holds whose payment never came.
    expires_at  timestamptz NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_reservations PRIMARY KEY (id),
    -- One hold per product per order: reserving the same order twice is impossible.
    CONSTRAINT uq_reservations_order_product UNIQUE (order_id, product_id),
    CONSTRAINT fk_reservations_stock_item FOREIGN KEY (tenant_id, product_id)
        REFERENCES inventory.stock_items (tenant_id, product_id),
    CONSTRAINT ck_reservations_quantity CHECK (quantity > 0),
    CONSTRAINT ck_reservations_status CHECK (status IN ('HELD', 'CONFIRMED', 'RELEASED'))
);

-- Finding holds that ran out (status HELD, expires_at in the past).
CREATE INDEX ix_reservations_status_expires ON inventory.reservations (status, expires_at);

CREATE TABLE inventory.stock_movements (
    id         uuid        NOT NULL,
    tenant_id  uuid        NOT NULL,
    product_id uuid        NOT NULL,
    -- +40 for a restock, -2 for a sale. Never zero.
    change     int         NOT NULL,
    reason     varchar(20) NOT NULL,
    -- Order number or the owner's note.
    reference  varchar(60),
    -- The staff user who made the change, or null when the system did (a sale).
    actor_id   uuid,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_stock_movements PRIMARY KEY (id),
    CONSTRAINT ck_stock_movements_change CHECK (change <> 0),
    CONSTRAINT ck_stock_movements_reason CHECK (reason IN ('RESTOCK', 'SALE', 'ADJUSTMENT', 'RETURN'))
);

-- A product's history, oldest first. Rows are only ever inserted, never updated or deleted, so
-- on_hand always equals the sum of a product's movements.
CREATE INDEX ix_stock_movements_tenant_product_created ON inventory.stock_movements (tenant_id, product_id, created_at);
