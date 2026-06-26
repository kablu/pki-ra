-- =============================================================================
-- V12 — Create workflow_config table + seed default values
-- DB      : MariaDB 10.6+
-- Module  : raservice
-- Purpose : Dedicated configuration table for the CSR approval workflow.
--           Separate from app_config (which stores LDAP, mail, etc.).
--           Read by WorkflowConfigService, hot-reloadable.
-- =============================================================================

CREATE TABLE IF NOT EXISTS workflow_config
(
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    config_key      VARCHAR(100)    NOT NULL,
    config_value    VARCHAR(500)    NOT NULL,
    description     VARCHAR(500)    NULL,
    is_active       BOOLEAN         NOT NULL DEFAULT TRUE,

    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by      VARCHAR(100)    NOT NULL DEFAULT 'system',
    updated_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                             ON UPDATE CURRENT_TIMESTAMP(6),
    updated_by      VARCHAR(100)    NOT NULL DEFAULT 'system',

    PRIMARY KEY (id),
    CONSTRAINT uq_workflow_config_key UNIQUE (config_key)

) ENGINE  = InnoDB
  DEFAULT CHARSET  = utf8mb4
  COLLATE          = utf8mb4_unicode_ci
  COMMENT          = 'Approval workflow configuration — configurable Maker-Checker settings';

CREATE INDEX idx_wf_config_active ON workflow_config (is_active);

-- =============================================================================
-- Seed — default workflow configuration
-- =============================================================================

INSERT INTO workflow_config (config_key, config_value, description, is_active, created_by, updated_by) VALUES
('approval_mode',             'DUAL',        'SINGLE = 1 operator, DUAL = Maker + Checker',          TRUE, 'system', 'system'),
('assignment_mode',           'HYBRID',      'SELF_PICKUP / ADMIN_ASSIGN / HYBRID',                   TRUE, 'system', 'system'),
('auto_assignment_strategy',  'NONE',        'NONE / ROUND_ROBIN / LEAST_LOADED',                     TRUE, 'system', 'system'),
('max_pending_per_operator',  '10',          'Maximum CSR requests an operator can have in progress',  TRUE, 'system', 'system'),
('maker_can_return_to_admin', 'true',        'Allow Maker to return request to Admin',                 TRUE, 'system', 'system'),
('require_remarks',           'true',        'Require remarks when reviewing/approving/rejecting',     TRUE, 'system', 'system');
