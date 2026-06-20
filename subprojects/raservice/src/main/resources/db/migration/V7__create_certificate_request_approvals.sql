-- =============================================================================
-- V7 — Create certificate_request_approvals table
-- DB      : MariaDB 10.6+
-- Module  : raservice
-- Purpose : Records approval/rejection decisions for certificate requests.
--           Each row is one decision by one approver.
-- =============================================================================

CREATE TABLE IF NOT EXISTS certificate_request_approvals
(
    id                  BIGINT          NOT NULL AUTO_INCREMENT,

    -- FK to the request being approved/rejected
    request_id          BIGINT          NOT NULL,

    -- FK to the user who made the decision
    approver_id         BIGINT          NOT NULL,

    -- APPROVED or REJECTED
    decision            VARCHAR(20)     NOT NULL,

    -- Optional comment from the approver
    comment             VARCHAR(1000)   NULL,

    -- Audit columns
    created_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by          VARCHAR(100)    NOT NULL DEFAULT 'system',
    updated_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                                 ON UPDATE CURRENT_TIMESTAMP(6),
    updated_by          VARCHAR(100)    NOT NULL DEFAULT 'system',

    PRIMARY KEY (id),
    CONSTRAINT fk_approval_request  FOREIGN KEY (request_id)
        REFERENCES certificate_requests (id) ON DELETE CASCADE,
    CONSTRAINT fk_approval_approver FOREIGN KEY (approver_id)
        REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT chk_approval_decision CHECK (decision IN ('APPROVED', 'REJECTED'))

) ENGINE  = InnoDB
  DEFAULT CHARSET  = utf8mb4
  COLLATE          = utf8mb4_unicode_ci
  COMMENT          = 'Approval/rejection decisions for certificate requests';

CREATE INDEX idx_approval_request   ON certificate_request_approvals (request_id);
CREATE INDEX idx_approval_approver  ON certificate_request_approvals (approver_id);
CREATE INDEX idx_approval_created   ON certificate_request_approvals (created_at);
