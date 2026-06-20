-- =============================================================================
-- V8 — Add FK from certificate_requests.certificate_id → certificates.id
-- DB      : MariaDB 10.6+
-- Purpose : Now that both tables exist, add the foreign key constraint.
-- =============================================================================

ALTER TABLE certificate_requests
    ADD CONSTRAINT fk_cert_req_certificate
        FOREIGN KEY (certificate_id) REFERENCES certificates (id) ON DELETE SET NULL;
