-- =============================================================================
-- V1 — Create app_config table
-- DB      : MariaDB 10.6+
-- Module  : raservice
-- Purpose : Stores runtime application configuration as flat key-value rows.
--           Each row holds one field value (plain string — no JSON).
--           Rows are grouped by config_type and assembled into a typed DTO
--           by ConfigBean on ApplicationReadyEvent.
-- =============================================================================

CREATE TABLE IF NOT EXISTS app_config
(
    -- Primary key
    id              BIGINT          NOT NULL AUTO_INCREMENT,

    -- Field name within the config type (e.g. host, port, baseDn)
    -- Unique per config_type — same key can exist across different types
    config_key      VARCHAR(100)    NOT NULL,

    -- Config group / type (e.g. LDAP, MAIL, CA_SERVER)
    -- All rows with the same config_type form one DTO in ConfigBean cache
    config_type     VARCHAR(50)     NOT NULL,

    -- Plain string value — no JSON, no nesting (e.g. ldap.pki.internal, 636, true)
    config_value    VARCHAR(500)    NOT NULL,

    -- Human-readable label for this field
    description     VARCHAR(500)    NULL,

    -- false = excluded from cache without deleting the row
    is_active       BOOLEAN         NOT NULL DEFAULT TRUE,

    -- Audit columns — auto-populated by BaseAuditEntity + AuditorAwareImpl
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by      VARCHAR(100)    NOT NULL DEFAULT 'system',
    updated_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                             ON UPDATE CURRENT_TIMESTAMP(6),
    updated_by      VARCHAR(100)    NOT NULL DEFAULT 'system',

    -- Constraints
    PRIMARY KEY (id),
    CONSTRAINT uq_app_config_key_type UNIQUE (config_key, config_type)

) ENGINE  = InnoDB
  DEFAULT CHARSET  = utf8mb4
  COLLATE          = utf8mb4_unicode_ci
  COMMENT          = 'Flat key-value config store — grouped by config_type into DTOs at startup';

-- Indexes
CREATE INDEX idx_app_config_type   ON app_config (config_type);
CREATE INDEX idx_app_config_active ON app_config (is_active);

-- =============================================================================
-- Seed data — LDAP config (one row per field)
-- Update values before first production deployment.
-- =============================================================================

INSERT INTO app_config (config_key, config_type, config_value, description, is_active, created_by, updated_by) VALUES
('host',                'LDAP', 'ldap.pki.internal',                                     'LDAP server hostname',          TRUE, 'system', 'system'),
('port',                'LDAP', '636',                                                   'LDAP port (636 = LDAPS)',        TRUE, 'system', 'system'),
('baseDn',              'LDAP', 'DC=pki,DC=internal',                                    'Base DN for user search',        TRUE, 'system', 'system'),
('bindDn',              'LDAP', 'CN=svc-pki-bind,OU=ServiceAccounts,DC=pki,DC=internal', 'Service account DN',             TRUE, 'system', 'system'),
('bindPassword',        'LDAP', 'change-me',                                             'Service account password',       TRUE, 'system', 'system'),
('useSsl',              'LDAP', 'true',                                                  'Enable LDAPS (SSL)',              TRUE, 'system', 'system'),
('connectionTimeoutMs', 'LDAP', '5000',                                                  'Connection timeout in ms',       TRUE, 'system', 'system'),
('readTimeoutMs',       'LDAP', '10000',                                                 'Read timeout in ms',             TRUE, 'system', 'system');
