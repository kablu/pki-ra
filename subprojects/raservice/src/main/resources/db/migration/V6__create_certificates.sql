-- =============================================================================
-- V6 — Create certificates table
-- DB      : MariaDB 10.6+
-- Module  : raservice
-- Purpose : Stores issued X.509 certificates with metadata. Each row represents
--           one end-entity certificate issued by the CA.
-- =============================================================================

CREATE TABLE IF NOT EXISTS certificates
(
    id                  BIGINT          NOT NULL AUTO_INCREMENT,

    -- Unique serial number (hex uppercase, e.g. 4F2A1B...)
    serial_number       VARCHAR(64)     NOT NULL,

    -- Subject and Issuer Distinguished Names
    subject_dn          VARCHAR(500)    NOT NULL,
    issuer_dn           VARCHAR(500)    NOT NULL,

    -- Validity period
    not_before          DATETIME(6)     NOT NULL,
    not_after           DATETIME(6)     NOT NULL,

    -- Full PEM-encoded certificate
    certificate_pem     TEXT            NOT NULL,

    -- Full PEM chain (end-entity + intermediate + root)
    certificate_chain   TEXT            NOT NULL,

    -- Key and signature metadata
    key_algorithm       VARCHAR(20)     NOT NULL,
    key_size            INT             NOT NULL,
    signature_algorithm VARCHAR(50)     NOT NULL,

    -- Certificate status
    status              VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',

    -- SHA-256 fingerprint of the certificate (for quick lookup)
    fingerprint_sha256  VARCHAR(64)     NOT NULL,

    -- FK to the request that produced this certificate
    request_id          BIGINT          NULL,

    -- Audit columns
    created_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by          VARCHAR(100)    NOT NULL DEFAULT 'system',
    updated_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                                 ON UPDATE CURRENT_TIMESTAMP(6),
    updated_by          VARCHAR(100)    NOT NULL DEFAULT 'system',

    PRIMARY KEY (id),
    CONSTRAINT uq_cert_serial       UNIQUE (serial_number),
    CONSTRAINT uq_cert_fingerprint  UNIQUE (fingerprint_sha256),
    CONSTRAINT fk_cert_request      FOREIGN KEY (request_id)
        REFERENCES certificate_requests (id) ON DELETE SET NULL,
    CONSTRAINT chk_cert_status      CHECK (status IN ('ACTIVE', 'REVOKED', 'EXPIRED', 'SUSPENDED'))

) ENGINE  = InnoDB
  DEFAULT CHARSET  = utf8mb4
  COLLATE          = utf8mb4_unicode_ci
  COMMENT          = 'Issued X.509 certificates — serial registry and lifecycle tracking';

CREATE INDEX idx_cert_status      ON certificates (status);
CREATE INDEX idx_cert_subject     ON certificates (subject_dn(255));
CREATE INDEX idx_cert_not_after   ON certificates (not_after);
CREATE INDEX idx_cert_request     ON certificates (request_id);
