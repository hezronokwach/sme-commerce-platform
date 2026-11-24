-- Finds a phone's recent orders in a shop quickly, for the per-phone limit on placing orders.
CREATE INDEX ix_orders_tenant_phone_created ON ordering.orders (tenant_id, customer_phone, created_at);
