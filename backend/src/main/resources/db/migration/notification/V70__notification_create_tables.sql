-- Notification module: one-time passes for watching an order's status live.
--
-- Why: the browser's EventSource (the built-in live-stream feature) can't send an Authorization
-- header. So the page first asks for a ticket with an ordinary request that CAN prove who it is (the
-- private order link, or a staff login), then opens the stream with the ticket in the URL. The ticket
-- works once and only for 60 seconds, so one that ends up in a log or the browser history is useless.

CREATE TABLE notification.stream_tickets (
    -- SHA-256 of the ticket, as 64 hex characters. The ticket itself is never stored.
    ticket_hash char(64)    NOT NULL,
    tenant_id   uuid        NOT NULL,
    -- The only order this ticket can watch.
    order_id    uuid        NOT NULL,
    expires_at  timestamptz NOT NULL,
    -- Set when the stream is opened with it; a ticket works once.
    used_at     timestamptz,
    created_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_stream_tickets PRIMARY KEY (ticket_hash),
    CONSTRAINT ck_stream_tickets_hash CHECK (ticket_hash ~ '^[0-9a-f]{64}$')
);

-- The daily clean-up deletes old tickets by age.
CREATE INDEX ix_stream_tickets_expires ON notification.stream_tickets (expires_at);
