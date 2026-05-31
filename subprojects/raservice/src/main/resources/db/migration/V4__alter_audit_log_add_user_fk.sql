-- =============================================================================
-- V4 — Add user_id FK to audit_log
-- DB      : MariaDB 10.6+
-- Module  : raservice
-- Purpose : Links each audit entry to the users table so queries can filter
--           by userId (not just username string).
--           NULL for 'system' / scheduler entries — no row in users for those.
--           ON DELETE SET NULL — audit trail survives user deletion.
-- =============================================================================

ALTER TABLE audit_log
    ADD COLUMN user_id BIGINT NULL AFTER username;

ALTER TABLE audit_log
    ADD CONSTRAINT fk_audit_log_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE SET NULL;

CREATE INDEX idx_audit_log_user_id ON audit_log (user_id);
