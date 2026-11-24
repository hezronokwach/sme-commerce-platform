-- Who changed what, for sensitive actions. Rows are only ever added.
CREATE TABLE audit.audit_log (
    id          uuid         NOT NULL,
    tenant_id   uuid         NOT NULL,
    actor_type  varchar(10)  NOT NULL,
    -- The staff member's id; null when the system did it.
    actor_id    uuid,
    action      varchar(60)  NOT NULL,
    entity_type varchar(40)  NOT NULL,
    entity_id   uuid,
    -- Before and after values. Never secrets.
    details     jsonb        NOT NULL DEFAULT '{}',
    ip_address  varchar(45),
    created_at  timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT pk_audit_log PRIMARY KEY (id),
    CONSTRAINT ck_audit_log_actor_type CHECK (actor_type IN ('STAFF', 'CUSTOMER', 'SYSTEM'))
);

CREATE INDEX ix_audit_log_tenant_created ON audit.audit_log (tenant_id, created_at);
