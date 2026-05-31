-- =============================================================================
-- V2 — Create users table
-- DB      : MariaDB 10.6+
-- Module  : raservice
-- Purpose : Master user registry. Stores AD profile data and account status.
--           Authentication is handled externally by AD/LDAP — no passwords stored.
--           Rows are auto-created on first AD login via UserManagementService.
-- =============================================================================

CREATE TABLE IF NOT EXISTS users
(
    id              BIGINT          NOT NULL AUTO_INCREMENT,

    -- AD sAMAccountName — unique, never null, synced from LDAP on login
    username        VARCHAR(100)    NOT NULL,

    email           VARCHAR(200)    NULL,
    full_name       VARCHAR(200)    NULL,
    display_name    VARCHAR(200)    NULL,

    -- FALSE = soft-deleted, excluded from all active queries
    is_active       BOOLEAN         NOT NULL DEFAULT TRUE,

    -- Set by AuditLogService on successful LOGIN action
    last_login_at   DATETIME(6)     NULL,

    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by      VARCHAR(100)    NOT NULL DEFAULT 'system',
    updated_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                             ON UPDATE CURRENT_TIMESTAMP(6),
    updated_by      VARCHAR(100)    NOT NULL DEFAULT 'system',

    PRIMARY KEY (id),
    CONSTRAINT uq_users_username UNIQUE (username)

) ENGINE = InnoDB
  DEFAULT CHARSET  = utf8mb4
  COLLATE          = utf8mb4_unicode_ci
  COMMENT          = 'User master — AD profile + active status. No passwords stored.';

-- Indexes
CREATE INDEX idx_users_is_active     ON users (is_active);
CREATE INDEX idx_users_last_login_at ON users (last_login_at);

-- =============================================================================
-- Seed — built-in system users
-- =============================================================================
INSERT INTO users (username, full_name, display_name, is_active, created_by, updated_by) VALUES
    ('system', 'System / Scheduler', 'System',         TRUE, 'system', 'system'),
    ('admin',  'PKI RA Admin',       'Administrator',  TRUE, 'system', 'system');
