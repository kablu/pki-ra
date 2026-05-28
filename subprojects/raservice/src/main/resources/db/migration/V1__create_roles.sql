-- =============================================================================
-- V1 — Create roles table
-- DB      : MariaDB 10.6+
-- Module  : raservice
-- Purpose : Master list of all defined roles in the PKI-RA system.
--           Each role defines a named permission set.
--           Users are linked to roles via user_roles junction table.
-- =============================================================================

CREATE TABLE IF NOT EXISTS roles
(
    id          BIGINT          NOT NULL AUTO_INCREMENT,
    role_name   VARCHAR(50)     NOT NULL,
    description VARCHAR(500)    NULL,
    is_active   BOOLEAN         NOT NULL DEFAULT TRUE,

    created_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by  VARCHAR(100)    NOT NULL DEFAULT 'system',
    updated_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                         ON UPDATE CURRENT_TIMESTAMP(6),
    updated_by  VARCHAR(100)    NOT NULL DEFAULT 'system',

    PRIMARY KEY (id),
    CONSTRAINT uq_roles_role_name UNIQUE (role_name)

) ENGINE = InnoDB
  DEFAULT CHARSET  = utf8mb4
  COLLATE          = utf8mb4_unicode_ci
  COMMENT          = 'Master list of all defined roles in the PKI-RA system';

-- Indexes
CREATE INDEX idx_roles_is_active ON roles (is_active);

-- =============================================================================
-- Seed — 4 built-in roles
-- =============================================================================
INSERT INTO roles (role_name, description, is_active, created_by, updated_by) VALUES
    ('ROLE_ADMIN',    'Full system access — user management, config refresh, all certificate operations', TRUE, 'system', 'system'),
    ('ROLE_OPERATOR', 'Certificate operations — request, approve, revoke',                               TRUE, 'system', 'system'),
    ('ROLE_AUDITOR',  'Read-only access to audit logs and reports',                                      TRUE, 'system', 'system'),
    ('ROLE_VIEWER',   'Read-only access to configs and system status',                                   TRUE, 'system', 'system');
