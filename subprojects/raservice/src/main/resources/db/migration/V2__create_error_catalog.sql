-- =============================================================================
-- V2 — Create error_catalog table
-- DB      : MariaDB 10.6+
-- Purpose : Stores all application error codes and messages.
--           internal_code  = used inside the application (service/exception layer)
--           external_code  = exposed to API consumers / UI clients
-- =============================================================================

CREATE TABLE IF NOT EXISTS error_catalog
(
    id              BIGINT          NOT NULL AUTO_INCREMENT,

    -- Internal error code (unique) — used inside application code
    -- Format: <MODULE>_<CATEGORY>_<NNN>  e.g. PKI_CERT_001
    internal_code   VARCHAR(50)     NOT NULL,

    -- External error code (unique) — shown to API consumers / end users
    -- Format: ERR-<NNN>  e.g. ERR-001
    external_code   VARCHAR(50)     NOT NULL,

    -- Short user-facing message
    message         VARCHAR(500)    NOT NULL,

    -- Internal developer/support description (not exposed to users)
    description     VARCHAR(1000)   NULL,

    -- Logical grouping: CERTIFICATE, AUTH, VALIDATION, NETWORK, SYSTEM
    category        VARCHAR(50)     NOT NULL,

    -- Severity: INFO, WARNING, ERROR, CRITICAL
    severity        VARCHAR(20)     NOT NULL,

    -- HTTP status code to return for this error (e.g. 400, 401, 403, 404, 500)
    http_status     INT             NOT NULL DEFAULT 500,

    -- true = client may safely retry the failed operation
    is_retryable    BOOLEAN         NOT NULL DEFAULT FALSE,

    -- false = excluded from cache without deleting the row
    is_active       BOOLEAN         NOT NULL DEFAULT TRUE,

    -- Audit columns
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by      VARCHAR(100)    NOT NULL DEFAULT 'system',
    updated_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                             ON UPDATE CURRENT_TIMESTAMP(6),
    updated_by      VARCHAR(100)    NOT NULL DEFAULT 'system',

    PRIMARY KEY (id),
    CONSTRAINT uq_error_internal_code UNIQUE (internal_code),
    CONSTRAINT uq_error_external_code UNIQUE (external_code)

) ENGINE  = InnoDB
  DEFAULT CHARSET  = utf8mb4
  COLLATE          = utf8mb4_unicode_ci
  COMMENT          = 'Error code and message catalog — cached by ErrorCatalogBean on startup';

CREATE INDEX idx_error_catalog_category ON error_catalog (category);
CREATE INDEX idx_error_catalog_severity ON error_catalog (severity);
CREATE INDEX idx_error_catalog_active   ON error_catalog (is_active);

-- =============================================================================
-- Seed data — PKI-RA error codes
-- =============================================================================

INSERT INTO error_catalog
    (internal_code, external_code, message, description, category, severity, http_status, is_retryable, is_active, created_by, updated_by)
VALUES

-- CERTIFICATE errors
('PKI_CERT_001', 'ERR-001', 'Certificate not found.',
 'The requested certificate does not exist in the system.',
 'CERTIFICATE', 'ERROR', 404, FALSE, TRUE, 'system', 'system'),

('PKI_CERT_002', 'ERR-002', 'Certificate has expired.',
 'The certificate validity period has passed.',
 'CERTIFICATE', 'ERROR', 400, FALSE, TRUE, 'system', 'system'),

('PKI_CERT_003', 'ERR-003', 'Certificate revocation failed.',
 'Unable to revoke the certificate. Check CA connectivity.',
 'CERTIFICATE', 'CRITICAL', 500, TRUE, TRUE, 'system', 'system'),

('PKI_CERT_004', 'ERR-004', 'Certificate already revoked.',
 'The certificate was previously revoked and cannot be revoked again.',
 'CERTIFICATE', 'WARNING', 409, FALSE, TRUE, 'system', 'system'),

('PKI_CERT_005', 'ERR-005', 'Certificate generation failed.',
 'CA server returned an error while generating the certificate.',
 'CERTIFICATE', 'CRITICAL', 500, TRUE, TRUE, 'system', 'system'),

-- AUTH errors
('PKI_AUTH_001', 'ERR-101', 'Authentication failed. Invalid credentials.',
 'AD authentication rejected the provided credentials.',
 'AUTH', 'ERROR', 401, FALSE, TRUE, 'system', 'system'),

('PKI_AUTH_002', 'ERR-102', 'Access denied. Insufficient permissions.',
 'User does not have the required role to perform this action.',
 'AUTH', 'ERROR', 403, FALSE, TRUE, 'system', 'system'),

('PKI_AUTH_003', 'ERR-103', 'Session expired. Please login again.',
 'The user session token has expired.',
 'AUTH', 'WARNING', 401, FALSE, TRUE, 'system', 'system'),

-- VALIDATION errors
('PKI_VAL_001', 'ERR-201', 'Invalid request. Required field missing.',
 'One or more mandatory request fields were not provided.',
 'VALIDATION', 'WARNING', 400, FALSE, TRUE, 'system', 'system'),

('PKI_VAL_002', 'ERR-202', 'Invalid certificate request format.',
 'The CSR or request payload does not conform to the expected format.',
 'VALIDATION', 'WARNING', 400, FALSE, TRUE, 'system', 'system'),

-- NETWORK errors
('PKI_NET_001', 'ERR-301', 'CA server is unreachable.',
 'Connection to the Certificate Authority timed out or was refused.',
 'NETWORK', 'CRITICAL', 503, TRUE, TRUE, 'system', 'system'),

('PKI_NET_002', 'ERR-302', 'LDAP server is unreachable.',
 'Connection to the AD/LDAP server timed out or was refused.',
 'NETWORK', 'CRITICAL', 503, TRUE, TRUE, 'system', 'system'),

-- SYSTEM errors
('PKI_SYS_001', 'ERR-901', 'An unexpected system error occurred.',
 'An unhandled exception occurred. Check application logs.',
 'SYSTEM', 'CRITICAL', 500, FALSE, TRUE, 'system', 'system'),

('PKI_SYS_002', 'ERR-902', 'Service temporarily unavailable.',
 'The service is under maintenance or overloaded.',
 'SYSTEM', 'ERROR', 503, TRUE, TRUE, 'system', 'system');
