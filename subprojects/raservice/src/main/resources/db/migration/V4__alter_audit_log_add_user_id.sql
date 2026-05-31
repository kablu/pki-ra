-- =============================================================================
-- V4 — Add user_id column to audit_log
-- DB      : MariaDB 10.6+
-- Module  : raservice
-- Purpose : Enriches every audit entry with the numeric user ID of the actor,
--           enabling fast user-based filtering (WHERE user_id = ?) without
--           a full-text scan on the username string.
--
-- Backward compatibility:
--   Column is NULLABLE — existing rows keep NULL (no data migration needed).
--   No FK constraint here — users table is created by a separate branch/PR.
--   FK constraint will be added once user-management migration is merged.
--   AuditLogService gracefully falls back to NULL if users table is absent.
-- =============================================================================

ALTER TABLE audit_log
    ADD COLUMN user_id BIGINT NULL AFTER username;

-- Supports: WHERE user_id = ? (filter by user) and JOIN to users table later
CREATE INDEX idx_audit_log_user_id ON audit_log (user_id);
