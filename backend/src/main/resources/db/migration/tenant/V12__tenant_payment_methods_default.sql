-- Issue "Second payment provider (card)": which ways of paying each shop offers now comes from
-- tenant.tenant_payment_providers (created in V10, unused until now).
--
-- Every shop that already exists keeps working the way it did: the fake provider, standing in for
-- M-Pesa until M-Pesa is connected, is turned on. New shops get the same row when they sign up.

INSERT INTO tenant.tenant_payment_providers (id, tenant_id, provider, enabled, display_name)
SELECT gen_random_uuid(), t.id, 'FAKE', true, 'M-Pesa (test)'
FROM tenant.tenants t
WHERE NOT EXISTS (
    SELECT 1 FROM tenant.tenant_payment_providers p WHERE p.tenant_id = t.id AND p.provider = 'FAKE'
);
