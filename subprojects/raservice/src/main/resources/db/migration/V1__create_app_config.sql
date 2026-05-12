-- =============================================================================
-- V1 — Create app_config table
-- DB      : MariaDB 10.6+
-- Module  : raservice
-- Purpose : Stores all runtime application configuration as key-value pairs.
--           Values are JSON-serialised DTOs. Loaded into ConfigBean cache on
--           ApplicationReadyEvent — no DB calls at request time.
-- =============================================================================

CREATE TABLE IF NOT EXISTS app_config
(
    -- Primary key
    id              BIGINT          NOT NULL AUTO_INCREMENT,

    -- Config key — unique logical name used as the cache key (e.g. isSecLdap)
    config_key      VARCHAR(100)    NOT NULL,

    -- Discriminator — tells ConfigDtoRegistry which DTO class to deserialise
    -- config_value into (e.g. LDAP, MAIL, CA_SERVER)
    config_type     VARCHAR(50)     NOT NULL,

    -- JSON-serialised DTO payload (e.g. LdapConfigDto as JSON)
    config_value    TEXT            NOT NULL,

    -- Human-readable description of what this config controls
    description     VARCHAR(500)    NULL,

    -- false = excluded from cache without deleting the row
    is_active       BOOLEAN         NOT NULL DEFAULT TRUE,

    -- Audit columns — auto-populated by BaseAuditEntity + AuditorAwareImpl
    -- DATETIME(6) = microsecond precision (matches Instant from BaseAuditEntity)
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by      VARCHAR(100)    NOT NULL DEFAULT 'system',
    updated_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                             ON UPDATE CURRENT_TIMESTAMP(6),
    updated_by      VARCHAR(100)    NOT NULL DEFAULT 'system',

    -- Constraints
    PRIMARY KEY (id),
    CONSTRAINT uq_app_config_key UNIQUE (config_key)

) ENGINE  = InnoDB
  DEFAULT CHARSET  = utf8mb4
  COLLATE          = utf8mb4_unicode_ci
  COMMENT          = 'Runtime application configuration — loaded into ConfigBean cache on startup';

-- Indexes
CREATE INDEX idx_app_config_type     ON app_config (config_type);
CREATE INDEX idx_app_config_active   ON app_config (is_active);

-- =============================================================================
-- Seed data — initial configuration entries
-- Add production values here before first deployment.
-- config_value must be valid JSON matching the DTO for the given config_type.
-- =============================================================================

INSERT INTO app_config (config_key, config_type, config_value, description, is_active, created_by, updated_by)
VALUES (
    'isSecLdap',
    'LDAP',
    '{"host":"ldap.pki.internal","port":636,"baseDn":"DC=pki,DC=internal","bindDn":"CN=svc-pki-bind,OU=ServiceAccounts,DC=pki,DC=internal","bindPassword":"change-me","useSsl":true,"connectionTimeoutMs":5000,"readTimeoutMs":10000}',
    'Active Directory LDAP connectivity settings',
    TRUE,
    'system',
    'system'
);
