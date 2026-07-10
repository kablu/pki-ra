-- =============================================================================
-- V5 — Create certificate_requests table
-- DB      : MariaDB 10.6+
-- Module  : raservice
-- Purpose : Stores CSR submissions and tracks their lifecycle from PENDING
--           through approval/rejection to issuance or failure.
-- =============================================================================

CREATE TABLE IF NOT EXISTS certificate_requests
(
    id                  BIGINT          NOT NULL AUTO_INCREMENT,

    -- PKCS#10 CSR in PEM format
    csr_pem             TEXT            NOT NULL,

    -- Parsed subject DN from the CSR (e.g. CN=example.com,O=Acme,C=IN)
    subject_dn          VARCHAR(500)    NOT NULL,

    -- Key algorithm and size extracted from CSR public key
    key_algorithm       VARCHAR(20)     NOT NULL,
    key_size            INT             NOT NULL,

    -- Subject Alternative Names (comma-separated, extracted from CSR)
    subject_alt_names   VARCHAR(2000)   NULL,

    -- Requested validity in days (NULL = use template/system default)
    requested_validity_days INT         NULL,

    -- Request lifecycle status
    status              VARCHAR(20)     NOT NULL DEFAULT 'PENDING',

    -- Rejection or failure reason
    status_reason       VARCHAR(1000)   NULL,

    -- FK to users table — who submitted this request
    requester_id        BIGINT          NOT NULL,

    -- FK to certificates table — populated after successful issuance
    certificate_id      BIGINT          NULL,

    -- Audit columns
    created_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by          VARCHAR(100)    NOT NULL DEFAULT 'system',
    updated_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                                 ON UPDATE CURRENT_TIMESTAMP(6),
    updated_by          VARCHAR(100)    NOT NULL DEFAULT 'system',

    PRIMARY KEY (id),
    CONSTRAINT fk_cert_req_requester FOREIGN KEY (requester_id)
        REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT chk_cert_req_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'ISSUED', 'FAILED'))

) ENGINE  = InnoDB
  DEFAULT CHARSET  = utf8mb4
  COLLATE          = utf8mb4_unicode_ci
  COMMENT          = 'Certificate signing requests — tracks CSR lifecycle from submission to issuance';

CREATE INDEX idx_cert_req_status       ON certificate_requests (status);
CREATE INDEX idx_cert_req_requester    ON certificate_requests (requester_id);
CREATE INDEX idx_cert_req_subject      ON certificate_requests (subject_dn(255));
CREATE INDEX idx_cert_req_created      ON certificate_requests (created_at);
