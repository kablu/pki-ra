-- =============================================================================
-- V14 — Add async CA integration columns to csr_requests
-- DB      : MariaDB 10.6+
-- Module  : raservice
-- Purpose : Support async flow: RA sends CSR to external CA (WLCA),
--           CA processes and POSTs certificate back via postBackUrl.
--           New status SENT_TO_CA added between APPROVED and ISSUED.
-- =============================================================================

ALTER TABLE csr_requests
    ADD COLUMN ca_transaction_id       VARCHAR(100)   NULL AFTER approval_mode_at_pickup,
    ADD COLUMN sent_to_ca_at           DATETIME(6)    NULL AFTER ca_transaction_id,
    ADD COLUMN post_back_url           VARCHAR(500)   NULL AFTER sent_to_ca_at,
    ADD COLUMN ca_response_received_at DATETIME(6)    NULL AFTER post_back_url;

-- Update status CHECK constraint to include SENT_TO_CA
ALTER TABLE csr_requests
    DROP CONSTRAINT chk_csr_status;

ALTER TABLE csr_requests
    ADD CONSTRAINT chk_csr_status CHECK (status IN (
        'RECEIVED', 'VALIDATED', 'VALIDATION_FAILED', 'SUBMITTED',
        'IN_REVIEW', 'REVIEWED', 'APPROVED', 'REJECTED',
        'RETURNED', 'CLOSED', 'SENT_TO_CA', 'ISSUED', 'FAILED'
    ));

CREATE INDEX idx_csr_ca_txn ON csr_requests (ca_transaction_id);
