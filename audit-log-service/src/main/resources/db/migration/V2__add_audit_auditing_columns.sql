-- V2__add_missing_columns.sql

-- AuditEvent JPA auditing columns
ALTER TABLE audit_events
    ADD COLUMN created_by VARCHAR(255) DEFAULT 'system';

ALTER TABLE audit_events
    ADD COLUMN updated_by VARCHAR(255) DEFAULT 'system';

-- User entity compatibility for older schemas
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS password VARCHAR(255);

UPDATE users
SET password_hash = password
WHERE password_hash IS NULL AND password IS NOT NULL;

UPDATE users
SET password = password_hash
WHERE password IS NULL AND password_hash IS NOT NULL;