-- =============================================================================
-- V10 — Create csr_request_transitions table
-- DB      : MariaDB 10.6+
-- Module  : raservice
-- Purpose : Immutable transition history for every CSR request status change.
--           One row per transition — insert only, never updated or deleted.
-- =============================================================================

CREATE TABLE IF NOT EXISTS csr_request_transitions
(
    id                  BIGINT          NOT NULL AUTO_INCREMENT,

    request_id          BIGINT          NOT NULL,
    from_status         VARCHAR(20)     NULL,
    to_status           VARCHAR(20)     NOT NULL,
    changed_by_id       BIGINT          NOT NULL,
    changed_by_role     VARCHAR(20)     NOT NULL,
    remarks             VARCHAR(2000)   NULL,
    created_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    CONSTRAINT fk_transition_request FOREIGN KEY (request_id)
        REFERENCES csr_requests (id) ON DELETE CASCADE,
    CONSTRAINT fk_transition_user    FOREIGN KEY (changed_by_id)
        REFERENCES users (id) ON DELETE RESTRICT

) ENGINE  = InnoDB
  DEFAULT CHARSET  = utf8mb4
  COLLATE          = utf8mb4_unicode_ci
  COMMENT          = 'Immutable transition history for CSR request status changes';

CREATE INDEX idx_transition_request ON csr_request_transitions (request_id);
CREATE INDEX idx_transition_created ON csr_request_transitions (created_at);
