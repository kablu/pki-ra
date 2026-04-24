-- =============================================================================
-- V1 — Create employee table
-- DB      : MariaDB 10.6+
-- Flyway migration — runs automatically on application startup
-- =============================================================================

CREATE TABLE IF NOT EXISTS employee
(
    -- Primary key — MariaDB: BIGINT AUTO_INCREMENT (PostgreSQL BIGSERIAL nahi)
    id              BIGINT          NOT NULL AUTO_INCREMENT,

    -- Personal information
    first_name      VARCHAR(100)    NOT NULL,
    last_name       VARCHAR(100)    NOT NULL,
    email           VARCHAR(255)    NOT NULL,
    phone           VARCHAR(20)     NULL,

    -- Employment information
    department      VARCHAR(100)    NOT NULL,
    designation     VARCHAR(100)    NOT NULL,

    -- MariaDB: DECIMAL (PostgreSQL NUMERIC ki jagah — same precision/scale)
    salary          DECIMAL(12, 2)  NOT NULL CHECK (salary > 0),

    -- MariaDB: ENUM type for status (PostgreSQL CHECK constraint ki jagah)
    status          ENUM('ACTIVE', 'INACTIVE', 'ON_LEAVE')
                                    NOT NULL DEFAULT 'ACTIVE',

    -- Audit columns (BaseAuditEntity se map honge)
    -- MariaDB: DATETIME(6) — microsecond precision (PostgreSQL TIMESTAMPTZ ki jagah)
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                             ON UPDATE CURRENT_TIMESTAMP(6),
    created_by      VARCHAR(255)    NULL,
    last_modified_by VARCHAR(255)   NULL,

    -- Constraints
    PRIMARY KEY (id),
    CONSTRAINT uq_employee_email UNIQUE (email)

) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'PKI-RA employee master data';

-- Indexes
CREATE INDEX idx_employee_department ON employee (department);
CREATE INDEX idx_employee_status     ON employee (status);
CREATE INDEX idx_employee_last_name  ON employee (last_name);
