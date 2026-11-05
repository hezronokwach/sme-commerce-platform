-- Spring Modulith's event publication registry (spring-modulith-starter-jpa).
--
-- When a module publishes an event that another module listens to with @ApplicationModuleListener,
-- Modulith first writes one row here per listener, in the same transaction as the publisher's own
-- changes. The row is marked completed once the listener has finished. A row that never completes
-- (the app crashed, or the listener threw) stays here, so the event isn't silently lost: the
-- "Reliable events" issue adds retrying them.
--
-- Columns match Modulith 2.1's JpaEventPublication entity (Spring's naming turns publicationDate into
-- publication_date). It lives in the default (public) schema: it belongs to the platform, not to a module.

CREATE TABLE event_publication (
    id                     uuid        NOT NULL,
    -- Which listener method should handle the event.
    listener_id            text        NOT NULL,
    -- The event's Java class, e.g. com.hezron.smecommerce.catalog.ProductCreatedEvent.
    event_type             text        NOT NULL,
    -- The event itself as JSON, so it can be delivered again later.
    serialized_event       text        NOT NULL,
    publication_date       timestamptz NOT NULL,
    -- Null until the listener has finished successfully.
    completion_date        timestamptz,
    status                 text,
    completion_attempts    int         NOT NULL DEFAULT 0,
    last_resubmission_date timestamptz,
    CONSTRAINT pk_event_publication PRIMARY KEY (id)
);

-- Finding the unfinished publications (completion_date is null) quickly.
CREATE INDEX ix_event_publication_completion_date ON event_publication (completion_date);
