-- Platform baseline: extensions and one schema per module.
-- Each module's tables live in its own schema so a cross-module join stands out in review.
-- Cross-schema foreign keys are not allowed; modules store each other's ids as plain columns.

-- pgvector, used by the assistant module for embeddings (needs the pgvector image).
CREATE EXTENSION IF NOT EXISTS vector;

CREATE SCHEMA IF NOT EXISTS tenant;
CREATE SCHEMA IF NOT EXISTS identity;
CREATE SCHEMA IF NOT EXISTS catalog;
CREATE SCHEMA IF NOT EXISTS inventory;
CREATE SCHEMA IF NOT EXISTS ordering;
CREATE SCHEMA IF NOT EXISTS payments;
CREATE SCHEMA IF NOT EXISTS notification;
CREATE SCHEMA IF NOT EXISTS assistant;
CREATE SCHEMA IF NOT EXISTS audit;
