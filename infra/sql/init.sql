-- Runs once when the Postgres data volume is first initialized.
-- The application schema itself is owned by Flyway
-- (api/src/main/resources/db/migration/V1__init.sql).
CREATE EXTENSION IF NOT EXISTS pgcrypto;
