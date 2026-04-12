-- =============================================================================
-- V1 — Create employee table
-- Flyway migration — runs automatically on application startup
-- =============================================================================

CREATE TABLE IF NOT EXISTS employee
(
    -- Primary key
    id              BIGSERIAL       PRIMARY KEY,

    -- Personal information
    first_name      VARCHAR(100)    NOT NULL,
    last_name       VARCHAR(100)    NOT NULL,
    email           VARCHAR(255)    NOT NULL,
    phone           VARCHAR(20),

    -- Employment information
    department      VARCHAR(100)    NOT NULL,
    designation     VARCHAR(100)    NOT NULL,
    salary          NUMERIC(12, 2)  NOT NULL CHECK (salary > 0),
    status          VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE'
                                    CHECK (status IN ('ACTIVE', 'INACTIVE', 'ON_LEAVE')),

    -- Audit columns (BaseAuditEntity se map honge)
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(255),
    last_modified_by VARCHAR(255),

    -- Constraints
    CONSTRAINT uq_employee_email UNIQUE (email)
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_employee_department ON employee (department);
CREATE INDEX IF NOT EXISTS idx_employee_status     ON employee (status);
CREATE INDEX IF NOT EXISTS idx_employee_last_name  ON employee (last_name);

-- Comments
COMMENT ON TABLE  employee                IS 'PKI-RA employee master data';
COMMENT ON COLUMN employee.status         IS 'ACTIVE | INACTIVE | ON_LEAVE';
COMMENT ON COLUMN employee.salary         IS 'Monthly salary in INR';
