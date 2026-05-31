-- =============================================================================
-- V3 — Create audit_log table
-- DB      : MariaDB 10.6+
-- Module  : raservice
-- Purpose : Immutable audit trail for every significant RA operation.
--           One row per action (cert request, approval, revocation,
--           config refresh, login, logout, etc.).
--           Written by AuditLogService with REQUIRES_NEW propagation —
--           audit rows survive even if the calling transaction rolls back.
-- =============================================================================

CREATE TABLE IF NOT EXISTS audit_log
(
    -- -------------------------------------------------------------------------
    -- Primary key
    -- -------------------------------------------------------------------------
    id              BIGINT          NOT NULL AUTO_INCREMENT,

    -- -------------------------------------------------------------------------
    -- Who performed the action
    -- AD sAMAccountName (e.g. john.doe) or 'system' for scheduled jobs.
    -- Never null — every audit entry must be attributed to an actor.
    -- -------------------------------------------------------------------------
    username        VARCHAR(100)    NOT NULL,

    -- -------------------------------------------------------------------------
    -- What action was performed
    -- Constant string, e.g. CONFIG_REFRESH, CERT_REQUEST, CERT_APPROVE,
    -- CERT_REVOKE, LOGIN, LOGOUT.
    -- Never null.
    -- -------------------------------------------------------------------------
    action          VARCHAR(100)    NOT NULL,

    -- -------------------------------------------------------------------------
    -- Which resource was affected (optional)
    -- e.g. certificate serial number, table name, job name.
    -- NULL for actions that have no specific resource (e.g. LOGIN).
    -- -------------------------------------------------------------------------
    resource_id     VARCHAR(200)    NULL,

    -- -------------------------------------------------------------------------
    -- Human-readable summary of what happened (optional)
    -- e.g. "12 active row(s) loaded into RA App Config cache"
    --      "Refresh failed: connection refused"
    -- NULL when no additional context is available.
    -- -------------------------------------------------------------------------
    description     VARCHAR(1000)   NULL,

    -- -------------------------------------------------------------------------
    -- Client IP address (optional)
    -- IPv4 or IPv6. NULL for batch / scheduler jobs that have no HTTP context.
    -- Supports X-Forwarded-For (proxy / load balancer environments).
    -- -------------------------------------------------------------------------
    ip_address      VARCHAR(50)     NULL,

    -- -------------------------------------------------------------------------
    -- Outcome of the action
    -- One of: SUCCESS | FAILURE
    -- Defaults to SUCCESS — callers set FAILURE explicitly on error.
    -- -------------------------------------------------------------------------
    outcome         VARCHAR(20)     NOT NULL DEFAULT 'SUCCESS',

    -- -------------------------------------------------------------------------
    -- When this entry was recorded (UTC)
    -- Set once at INSERT — never updated (immutable audit record).
    -- DATETIME(6) = microsecond precision for accurate ordering.
    -- -------------------------------------------------------------------------
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    -- -------------------------------------------------------------------------
    -- Constraints
    -- -------------------------------------------------------------------------
    PRIMARY KEY (id),

    CONSTRAINT chk_audit_log_outcome
        CHECK (outcome IN ('SUCCESS', 'FAILURE'))

) ENGINE  = InnoDB
  DEFAULT CHARSET  = utf8mb4
  COLLATE          = utf8mb4_unicode_ci
  COMMENT          = 'Immutable audit trail — one row per RA operation, written with REQUIRES_NEW';

-- =============================================================================
-- Indexes
-- Aligned with @Index annotations on AuditLog entity.
-- =============================================================================

-- Queries by actor: "What did john.doe do?"
CREATE INDEX idx_audit_log_username
    ON audit_log (username);

-- Queries by action type: "Every CONFIG_REFRESH ever triggered"
CREATE INDEX idx_audit_log_action
    ON audit_log (action);

-- Time-range queries and ORDER BY created_at DESC (default sort for all endpoints)
CREATE INDEX idx_audit_log_created_at
    ON audit_log (created_at);

-- Composite: "All failures for a specific user" — covers /user/{username}/failures
CREATE INDEX idx_audit_log_username_outcome
    ON audit_log (username, outcome);

-- Composite: "All entries for a specific resource" — covers resourceId filter
CREATE INDEX idx_audit_log_resource_id
    ON audit_log (resource_id);

-- =============================================================================
-- Seed data — initial system entries to confirm table is working
-- Inserted by 'system' actor, no IP (batch context).
-- =============================================================================

INSERT INTO audit_log (username, action, resource_id, description, ip_address, outcome, created_at)
VALUES
    ('system', 'SYSTEM_INIT', 'audit_log',
     'audit_log table created and verified by Flyway migration V3',
     NULL, 'SUCCESS', CURRENT_TIMESTAMP(6));
