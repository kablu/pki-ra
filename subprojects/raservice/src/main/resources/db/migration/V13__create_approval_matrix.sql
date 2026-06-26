-- =============================================================================
-- V13 — Create approval_matrix table
-- DB      : MariaDB 10.6+
-- Module  : raservice
-- Purpose : Per-profile approval configuration. Admin can override the global
--           workflow_config for specific CSR profiles. If no override exists
--           for a profile, the global config applies.
-- =============================================================================

CREATE TABLE IF NOT EXISTS approval_matrix
(
    id                          BIGINT          NOT NULL AUTO_INCREMENT,

    -- Which profile this rule applies to (NULL = global default)
    csr_profile                 VARCHAR(50)     NULL,

    -- Approval mode for this profile
    approval_mode               VARCHAR(10)     NOT NULL DEFAULT 'DUAL',

    -- Roles allowed to act as Maker
    maker_role                  VARCHAR(50)     NOT NULL DEFAULT 'ROLE_OPERATOR',

    -- Roles allowed to act as Checker
    checker_role                VARCHAR(50)     NOT NULL DEFAULT 'ROLE_OPERATOR',

    -- Can Admin also act as Maker/Checker?
    admin_can_be_maker          BOOLEAN         NOT NULL DEFAULT FALSE,
    admin_can_be_checker        BOOLEAN         NOT NULL DEFAULT FALSE,

    -- Can Checker return to Admin? (in addition to Maker)
    checker_can_return          BOOLEAN         NOT NULL DEFAULT FALSE,

    -- Minimum remarks length (0 = no minimum, just non-blank if required)
    min_remarks_length          INT             NOT NULL DEFAULT 0,

    -- Auto-approve for this profile? (skip manual approval entirely)
    auto_approve                BOOLEAN         NOT NULL DEFAULT FALSE,

    -- Priority override: URGENT requests in this profile auto-escalate?
    urgent_auto_escalate        BOOLEAN         NOT NULL DEFAULT FALSE,

    -- Max pending requests per operator for this profile
    max_pending_per_operator    INT             NOT NULL DEFAULT 10,

    -- Is this rule active?
    is_active                   BOOLEAN         NOT NULL DEFAULT TRUE,

    -- Audit
    created_at                  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by                  VARCHAR(100)    NOT NULL DEFAULT 'system',
    updated_at                  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                                         ON UPDATE CURRENT_TIMESTAMP(6),
    updated_by                  VARCHAR(100)    NOT NULL DEFAULT 'system',

    PRIMARY KEY (id),
    CONSTRAINT uq_matrix_profile UNIQUE (csr_profile),
    CONSTRAINT chk_matrix_mode CHECK (approval_mode IN ('SINGLE', 'DUAL'))

) ENGINE  = InnoDB
  DEFAULT CHARSET  = utf8mb4
  COLLATE          = utf8mb4_unicode_ci
  COMMENT          = 'Per-profile approval matrix — Admin configurable, nothing hardcoded';

-- =============================================================================
-- Seed — default matrix rules (Admin can change anytime)
-- =============================================================================

-- Global default (csr_profile = NULL → applies when no profile-specific rule exists)
INSERT INTO approval_matrix
    (csr_profile, approval_mode, maker_role, checker_role,
     admin_can_be_maker, admin_can_be_checker, checker_can_return,
     min_remarks_length, auto_approve, urgent_auto_escalate,
     max_pending_per_operator, is_active, created_by, updated_by)
VALUES
    (NULL,              'DUAL',   'ROLE_OPERATOR', 'ROLE_OPERATOR',
     FALSE, FALSE, FALSE, 0, FALSE, FALSE, 10, TRUE, 'system', 'system'),

    ('TLS_SERVER',      'SINGLE', 'ROLE_OPERATOR', 'ROLE_OPERATOR',
     TRUE,  FALSE, FALSE, 0, FALSE, FALSE, 15, TRUE, 'system', 'system'),

    ('TLS_CLIENT',      'SINGLE', 'ROLE_OPERATOR', 'ROLE_OPERATOR',
     TRUE,  FALSE, FALSE, 0, FALSE, FALSE, 15, TRUE, 'system', 'system'),

    ('CODE_SIGNING',    'DUAL',   'ROLE_OPERATOR', 'ROLE_OPERATOR',
     FALSE, FALSE, FALSE, 10, FALSE, FALSE, 5,  TRUE, 'system', 'system'),

    ('SMIME',           'SINGLE', 'ROLE_OPERATOR', 'ROLE_OPERATOR',
     TRUE,  FALSE, FALSE, 0, FALSE, FALSE, 20, TRUE, 'system', 'system'),

    ('DOCUMENT_SIGNING','DUAL',   'ROLE_OPERATOR', 'ROLE_OPERATOR',
     FALSE, FALSE, FALSE, 5, FALSE, FALSE, 10, TRUE, 'system', 'system');
