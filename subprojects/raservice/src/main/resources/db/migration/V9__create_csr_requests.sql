-- =============================================================================
-- V9 — Create csr_requests table
-- DB      : MariaDB 10.6+
-- Module  : raservice
-- Purpose : Stores all CSR submissions with full lifecycle tracking.
--           Separate from certificate_requests (Phase 1 table).
--           Supports configurable Maker-Checker approval workflow.
-- =============================================================================

CREATE TABLE IF NOT EXISTS csr_requests
(
    -- Primary key
    id                         BIGINT          NOT NULL AUTO_INCREMENT,

    -- Unique identifiers
    request_id                 VARCHAR(50)     NOT NULL,
    client_txn_id              VARCHAR(100)    NOT NULL,

    -- CSR data
    csr_pem                    TEXT            NOT NULL,
    subject_dn                 VARCHAR(500)    NOT NULL,
    key_algorithm              VARCHAR(20)     NOT NULL,
    key_size                   INT             NOT NULL,
    signature_algorithm        VARCHAR(50)     NOT NULL,
    subject_alt_names          VARCHAR(2000)   NULL,
    csr_hash                   VARCHAR(64)     NOT NULL,

    -- Profile
    csr_profile                VARCHAR(50)     NOT NULL,
    template_id                BIGINT          NULL,

    -- Requestor info
    requestor_name             VARCHAR(200)    NOT NULL,
    requestor_email            VARCHAR(200)    NOT NULL,
    requestor_department       VARCHAR(200)    NULL,
    requestor_phone            VARCHAR(50)     NULL,
    requestor_user_id          BIGINT          NULL,

    -- Request details
    requested_validity_days    INT             NULL,
    purpose                    VARCHAR(1000)   NULL,
    priority                   VARCHAR(10)     NOT NULL DEFAULT 'NORMAL',
    additional_attributes      JSON            NULL,

    -- Transition status
    status                     VARCHAR(20)     NOT NULL DEFAULT 'RECEIVED',
    status_reason              VARCHAR(1000)   NULL,

    -- Validation result
    validation_passed          BOOLEAN         NULL,
    validation_warnings        JSON            NULL,
    validation_flags           JSON            NULL,

    -- Approval tracking
    assigned_to_id             BIGINT          NULL,
    assigned_at                DATETIME(6)     NULL,
    maker_remarks              VARCHAR(2000)   NULL,
    maker_reviewed_at          DATETIME(6)     NULL,
    checker_id                 BIGINT          NULL,
    checker_decision           VARCHAR(20)     NULL,
    checker_remarks            VARCHAR(2000)   NULL,
    checker_decided_at         DATETIME(6)     NULL,
    closed_at                  DATETIME(6)     NULL,
    closed_by_id               BIGINT          NULL,
    closed_reason              VARCHAR(2000)   NULL,
    approval_mode_at_pickup    VARCHAR(10)     NULL,

    -- Certificate link
    certificate_id             BIGINT          NULL,

    -- Audit columns
    created_at                 DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by                 VARCHAR(100)    NOT NULL DEFAULT 'system',
    updated_at                 DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                                        ON UPDATE CURRENT_TIMESTAMP(6),
    updated_by                 VARCHAR(100)    NOT NULL DEFAULT 'system',

    -- Constraints
    PRIMARY KEY (id),
    CONSTRAINT uq_csr_request_id    UNIQUE (request_id),
    CONSTRAINT uq_csr_client_txn_id UNIQUE (client_txn_id),
    CONSTRAINT fk_csr_requestor_user FOREIGN KEY (requestor_user_id)
        REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_csr_assigned_to    FOREIGN KEY (assigned_to_id)
        REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_csr_checker        FOREIGN KEY (checker_id)
        REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_csr_closed_by      FOREIGN KEY (closed_by_id)
        REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT chk_csr_status CHECK (status IN (
        'RECEIVED', 'VALIDATED', 'VALIDATION_FAILED', 'SUBMITTED',
        'IN_REVIEW', 'REVIEWED', 'APPROVED', 'REJECTED',
        'RETURNED', 'CLOSED', 'ISSUED', 'FAILED'
    )),
    CONSTRAINT chk_csr_priority CHECK (priority IN ('NORMAL', 'HIGH', 'URGENT')),
    CONSTRAINT chk_csr_profile CHECK (csr_profile IN (
        'TLS_SERVER', 'TLS_CLIENT', 'CODE_SIGNING', 'SMIME', 'DOCUMENT_SIGNING'
    ))

) ENGINE  = InnoDB
  DEFAULT CHARSET  = utf8mb4
  COLLATE          = utf8mb4_unicode_ci
  COMMENT          = 'CSR requests with configurable Maker-Checker approval workflow';

-- Indexes
CREATE INDEX idx_csr_status        ON csr_requests (status);
CREATE INDEX idx_csr_profile       ON csr_requests (csr_profile);
CREATE INDEX idx_csr_priority      ON csr_requests (priority);
CREATE INDEX idx_csr_subject       ON csr_requests (subject_dn(255));
CREATE INDEX idx_csr_hash          ON csr_requests (csr_hash);
CREATE INDEX idx_csr_requestor     ON csr_requests (requestor_email);
CREATE INDEX idx_csr_created       ON csr_requests (created_at);
CREATE INDEX idx_csr_assigned      ON csr_requests (assigned_to_id);
