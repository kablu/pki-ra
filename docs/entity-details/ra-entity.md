# PKI-RA Database Schema — Complete Entity Design

> **Database:** MariaDB / InnoDB  
> **Character Set:** utf8mb4 · **Collation:** utf8mb4_unicode_ci  
> **Branch:** feature/employee-jpa-crud  
> **Last Updated:** 2026-04-20

---

## Table of Contents

1. [High-Level Architecture Overview](#1-high-level-architecture-overview)
2. [ER Diagram (Textual)](#2-er-diagram-textual)
3. [Core Schema — SQL CREATE TABLE](#3-core-schema--sql-create-table)
   - 3.1 [Foundation / Identity Layer](#31--foundation--identity-layer)
   - 3.2 [RBAC Layer](#32--rbac-layer)
   - 3.3 [Approval Workflow Layer](#33--approval-workflow-layer)
   - 3.4 [Certificate Layer](#34--certificate-layer)
   - 3.5 [Audit & Notification Layer](#35--audit--notification-layer)
4. [Indexes](#4-indexes)
5. [Sample Queries — Core Schema](#5-sample-queries--core-schema)
6. [UUID vs AUTO_INCREMENT Strategy](#6-uuid-vs-auto_increment-strategy)
7. [Partitioning Strategy for audit_logs](#7-partitioning-strategy-for-audit_logs)
8. [Cascading Rules Summary](#8-cascading-rules-summary)
9. [Seed Data — System Roles & Permissions](#9-seed-data--system-roles--permissions)
10. [Active Directory Authentication Tables](#10-active-directory-authentication-tables)
    - 10.1 [AD Authentication Flow](#101-ad-authentication-flow-end-entity)
    - 10.2 [ad_configurations](#102-table--ad_configurations)
    - 10.3 [ad_user_mappings](#103-table--ad_user_mappings)
    - 10.4 [ad_group_role_mappings](#104-table--ad_group_role_mappings)
    - 10.5 [ad_auth_sessions](#105-table--ad_auth_sessions)
    - 10.6 [ad_auth_attempts](#106-table--ad_auth_attempts)
    - 10.7 [AD Indexes & Sample Queries](#107-ad-indexes--sample-queries)
11. [request_details Table](#11-request_details-table)
    - 11.1 [Design Rationale](#111-design-rationale)
    - 11.2 [SQL CREATE TABLE](#112-sql-create-table)
    - 11.3 [Indexes & Sample Queries](#113-indexes--sample-queries)
12. [log_request_details Table](#12-log_request_details-table)
    - 12.1 [Design Rationale](#121-design-rationale)
    - 12.2 [SQL CREATE TABLE](#122-sql-create-table)
    - 12.3 [Indexes & Sample Queries](#123-indexes--sample-queries)
13. [Complete Relationship Summary](#13-complete-relationship-summary)

---

## 1. High-Level Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                        PKI-RA DATABASE                          │
├──────────────────┬──────────────────┬───────────────────────────┤
│  IDENTITY LAYER  │  CERTIFICATE LAYER│     GOVERNANCE LAYER      │
│                  │                   │                           │
│ organizations    │ certificates      │ roles                     │
│ users            │ certificate_      │ permissions               │
│ identity_        │   requests        │ role_permissions          │
│   documents      │ certificate_      │ user_roles                │
│                  │   status_history  │ approval_workflows        │
│                  │ revocation_list   │ approval_steps            │
│                  │ key_metadata      │ approval_step_actions     │
│                  │                   │                           │
├──────────────────┴──────────────────┴───────────────────────────┤
│                      AUDIT LAYER                                │
│              audit_logs  │  notifications                       │
├─────────────────────────────────────────────────────────────────┤
│                  AD AUTHENTICATION LAYER                        │
│  ad_configurations  │  ad_user_mappings  │  ad_group_role_      │
│  ad_auth_sessions   │  ad_auth_attempts  │    mappings          │
├─────────────────────────────────────────────────────────────────┤
│                   REQUEST TRACKING LAYER                        │
│              request_details  │  log_request_details            │
└─────────────────────────────────────────────────────────────────┘
```

**Design Decisions:**

| Decision | Choice | Reason |
|---|---|---|
| Primary keys | UUID `CHAR(36)` for entities | Prevents enumeration attacks |
| High-volume tables | `BIGINT AUTO_INCREMENT` | Optimal append-only insert performance |
| Engine | InnoDB throughout | ACID compliance + FK enforcement |
| Soft deletes | `is_deleted` + `deleted_at` | Preserve referential history |
| Normalization | 3NF | Certificate status is a history table, not a mutable column |
| Private keys | Never stored | Only CSR and public key PEM stored |

---

## 2. ER Diagram (Textual)

```
organizations (1) ──< (M) users
organizations (1) ──< (M) certificate_requests
organizations (1) ──< (M) ad_configurations
organizations (1) ──< (M) request_details

users (1) ──< (M) user_roles >── (M) roles
roles (1) ──< (M) role_permissions >── (M) permissions

users (1) ──< (M) identity_documents
users (1) ──< (M) certificate_requests
users (1) ──< (1) ad_user_mappings
users (1) ──< (M) ad_auth_sessions
users (1) ──< (M) request_details

certificate_requests (1) ──< (M) approval_step_actions
certificate_requests (1) ──< (1) certificates
certificate_requests (1) ──< (1) key_metadata

approval_workflows (1) ──< (M) approval_steps
certificate_requests (M) >── (1) approval_workflows

certificates (1) ──< (M) certificate_status_history
certificates (1) ──< (M) revocation_list

ad_configurations (1) ──< (M) ad_user_mappings
ad_configurations (1) ──< (M) ad_group_role_mappings >── (1) roles
ad_configurations (1) ──< (M) ad_auth_attempts
ad_auth_sessions  (1) ──< (M) ad_auth_attempts
ad_auth_sessions  (1) ──< (M) request_details

request_details (1) ──< (M) log_request_details
request_details (1) ──> (1) certificate_requests

audit_logs (M) >── (1) users
notifications (M) >── (1) users
```

---

## 3. Core Schema — SQL CREATE TABLE

### 3.1 Foundation / Identity Layer

```sql
CREATE TABLE organizations (
    id            CHAR(36)     NOT NULL DEFAULT (UUID()),
    name          VARCHAR(255) NOT NULL,
    domain        VARCHAR(255) NOT NULL,
    country_code  CHAR(2)      NOT NULL,
    org_type      ENUM('ENTERPRISE','GOVERNMENT','INDIVIDUAL','CA_PARTNER') NOT NULL DEFAULT 'ENTERPRISE',
    status        ENUM('ACTIVE','SUSPENDED','PENDING_VERIFICATION') NOT NULL DEFAULT 'PENDING_VERIFICATION',
    contact_email VARCHAR(320) NOT NULL,
    contact_phone VARCHAR(32)      NULL,
    address_line1 VARCHAR(255)     NULL,
    city          VARCHAR(100)     NULL,
    state         VARCHAR(100)     NULL,
    postal_code   VARCHAR(20)      NULL,
    metadata      JSON             NULL,
    is_deleted    TINYINT(1)   NOT NULL DEFAULT 0,
    deleted_at    DATETIME         NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_organizations        PRIMARY KEY (id),
    CONSTRAINT uq_organizations_domain UNIQUE (domain),
    CONSTRAINT chk_org_deleted         CHECK (
        (is_deleted = 0 AND deleted_at IS NULL) OR (is_deleted = 1 AND deleted_at IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE users (
    id                 CHAR(36)     NOT NULL DEFAULT (UUID()),
    organization_id    CHAR(36)         NULL,
    username           VARCHAR(100) NOT NULL,
    email              VARCHAR(320) NOT NULL,
    email_verified     TINYINT(1)   NOT NULL DEFAULT 0,
    password_hash      VARCHAR(255) NOT NULL,
    full_name          VARCHAR(255) NOT NULL,
    phone              VARCHAR(32)      NULL,
    phone_verified     TINYINT(1)   NOT NULL DEFAULT 0,
    user_type          ENUM('END_USER','RA_OPERATOR','RA_ADMIN','SYSTEM') NOT NULL DEFAULT 'END_USER',
    status             ENUM('ACTIVE','INACTIVE','LOCKED','PENDING_VERIFICATION') NOT NULL DEFAULT 'PENDING_VERIFICATION',
    failed_login_count SMALLINT     NOT NULL DEFAULT 0,
    locked_until       DATETIME         NULL,
    last_login_at      DATETIME         NULL,
    last_login_ip      VARCHAR(45)      NULL,
    totp_secret_enc    VARCHAR(512)     NULL,
    totp_enabled       TINYINT(1)   NOT NULL DEFAULT 0,
    is_deleted         TINYINT(1)   NOT NULL DEFAULT 0,
    deleted_at         DATETIME         NULL,
    created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_users                PRIMARY KEY (id),
    CONSTRAINT uq_users_email          UNIQUE (email),
    CONSTRAINT uq_users_username       UNIQUE (username),
    CONSTRAINT fk_users_org            FOREIGN KEY (organization_id)
        REFERENCES organizations(id) ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT chk_users_failed_logins CHECK (failed_login_count >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE identity_documents (
    id                CHAR(36)     NOT NULL DEFAULT (UUID()),
    user_id           CHAR(36)     NOT NULL,
    doc_type          ENUM('PASSPORT','NATIONAL_ID','DRIVERS_LICENSE','COMPANY_REG','TAX_ID','OTHER') NOT NULL,
    doc_number_hash   VARCHAR(255) NOT NULL,
    issuing_country   CHAR(2)      NOT NULL,
    issuing_authority VARCHAR(255)     NULL,
    issue_date        DATE             NULL,
    expiry_date       DATE             NULL,
    verified_status   ENUM('PENDING','VERIFIED','REJECTED','EXPIRED') NOT NULL DEFAULT 'PENDING',
    verified_by       CHAR(36)         NULL,
    verified_at       DATETIME         NULL,
    rejection_reason  TEXT             NULL,
    storage_ref       VARCHAR(512)     NULL,
    is_deleted        TINYINT(1)   NOT NULL DEFAULT 0,
    deleted_at        DATETIME         NULL,
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_identity_docs PRIMARY KEY (id),
    CONSTRAINT fk_idoc_user     FOREIGN KEY (user_id)
        REFERENCES users(id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_idoc_verifier FOREIGN KEY (verified_by)
        REFERENCES users(id) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### 3.2 RBAC Layer

```sql
CREATE TABLE roles (
    id          CHAR(36)     NOT NULL DEFAULT (UUID()),
    name        VARCHAR(100) NOT NULL,
    description TEXT             NULL,
    scope       ENUM('GLOBAL','ORGANIZATION') NOT NULL DEFAULT 'ORGANIZATION',
    is_system   TINYINT(1)   NOT NULL DEFAULT 0,
    is_deleted  TINYINT(1)   NOT NULL DEFAULT 0,
    deleted_at  DATETIME         NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_roles      PRIMARY KEY (id),
    CONSTRAINT uq_roles_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE permissions (
    id          CHAR(36)     NOT NULL DEFAULT (UUID()),
    code        VARCHAR(100) NOT NULL,
    module      VARCHAR(50)  NOT NULL,
    action      VARCHAR(50)  NOT NULL,
    description TEXT             NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_permissions      PRIMARY KEY (id),
    CONSTRAINT uq_permissions_code UNIQUE (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE role_permissions (
    role_id       CHAR(36) NOT NULL,
    permission_id CHAR(36) NOT NULL,
    granted_by    CHAR(36)     NULL,
    granted_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_role_permissions PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_rp_role          FOREIGN KEY (role_id)   REFERENCES roles(id)       ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_rp_permission    FOREIGN KEY (permission_id) REFERENCES permissions(id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_rp_grantor       FOREIGN KEY (granted_by)    REFERENCES users(id)       ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE user_roles (
    user_id         CHAR(36) NOT NULL,
    role_id         CHAR(36) NOT NULL,
    organization_id CHAR(36)     NULL,
    assigned_by     CHAR(36)     NULL,
    assigned_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at      DATETIME     NULL,
    CONSTRAINT pk_user_roles  PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_ur_user     FOREIGN KEY (user_id)         REFERENCES users(id)          ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_ur_role     FOREIGN KEY (role_id)         REFERENCES roles(id)          ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_ur_org      FOREIGN KEY (organization_id) REFERENCES organizations(id)  ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_ur_assigner FOREIGN KEY (assigned_by)     REFERENCES users(id)          ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### 3.3 Approval Workflow Layer

```sql
CREATE TABLE approval_workflows (
    id              CHAR(36)     NOT NULL DEFAULT (UUID()),
    name            VARCHAR(255) NOT NULL,
    cert_type       ENUM('SSL_TLS','CODE_SIGNING','CLIENT_AUTH','EMAIL','DOCUMENT_SIGNING','ROOT_CA','INTERMEDIATE_CA') NOT NULL,
    organization_id CHAR(36)         NULL,
    total_steps     TINYINT      NOT NULL DEFAULT 1,
    is_active       TINYINT(1)   NOT NULL DEFAULT 1,
    created_by      CHAR(36)         NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_approval_workflows PRIMARY KEY (id),
    CONSTRAINT fk_aw_org             FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT fk_aw_creator         FOREIGN KEY (created_by)      REFERENCES users(id)          ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT chk_aw_steps          CHECK (total_steps BETWEEN 1 AND 10)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE approval_steps (
    id               CHAR(36)     NOT NULL DEFAULT (UUID()),
    workflow_id      CHAR(36)     NOT NULL,
    step_order       TINYINT      NOT NULL,
    step_name        VARCHAR(100) NOT NULL,
    required_role_id CHAR(36)         NULL,
    min_approvers    TINYINT      NOT NULL DEFAULT 1,
    is_mandatory     TINYINT(1)   NOT NULL DEFAULT 1,
    timeout_hours    SMALLINT         NULL,
    CONSTRAINT pk_approval_steps PRIMARY KEY (id),
    CONSTRAINT uq_step_order     UNIQUE (workflow_id, step_order),
    CONSTRAINT fk_as_workflow    FOREIGN KEY (workflow_id)      REFERENCES approval_workflows(id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_as_role        FOREIGN KEY (required_role_id) REFERENCES roles(id)              ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT chk_min_approvers CHECK (min_approvers >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### 3.4 Certificate Layer

```sql
CREATE TABLE certificate_requests (
    id                 CHAR(36)      NOT NULL DEFAULT (UUID()),
    organization_id    CHAR(36)          NULL,
    requester_id       CHAR(36)      NOT NULL,
    workflow_id        CHAR(36)          NULL,
    cert_type          ENUM('SSL_TLS','CODE_SIGNING','CLIENT_AUTH','EMAIL','DOCUMENT_SIGNING','ROOT_CA','INTERMEDIATE_CA') NOT NULL,
    common_name        VARCHAR(255)  NOT NULL,
    subject_alt_names  JSON              NULL,
    subject_dn         VARCHAR(1024) NOT NULL,
    key_algorithm      ENUM('RSA_2048','RSA_4096','ECDSA_P256','ECDSA_P384','ED25519') NOT NULL DEFAULT 'RSA_2048',
    validity_days      SMALLINT      NOT NULL DEFAULT 365,
    csr_pem            TEXT          NOT NULL,
    csr_hash           VARCHAR(255)  NOT NULL,
    status             ENUM('DRAFT','SUBMITTED','PENDING_VERIFICATION','IN_APPROVAL','APPROVED','REJECTED','ISSUED','CANCELLED') NOT NULL DEFAULT 'DRAFT',
    current_step       TINYINT           NULL,
    priority           ENUM('LOW','NORMAL','HIGH','URGENT') NOT NULL DEFAULT 'NORMAL',
    rejection_reason   TEXT              NULL,
    submitted_at       DATETIME          NULL,
    approved_at        DATETIME          NULL,
    issued_at          DATETIME          NULL,
    expires_request_at DATETIME          NULL,
    version            SMALLINT      NOT NULL DEFAULT 1,
    parent_request_id  CHAR(36)          NULL,
    is_deleted         TINYINT(1)    NOT NULL DEFAULT 0,
    deleted_at         DATETIME          NULL,
    created_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_cert_requests PRIMARY KEY (id),
    CONSTRAINT fk_cr_org        FOREIGN KEY (organization_id)   REFERENCES organizations(id)     ON DELETE SET NULL  ON UPDATE CASCADE,
    CONSTRAINT fk_cr_requester  FOREIGN KEY (requester_id)      REFERENCES users(id)             ON DELETE RESTRICT  ON UPDATE CASCADE,
    CONSTRAINT fk_cr_workflow   FOREIGN KEY (workflow_id)       REFERENCES approval_workflows(id) ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT fk_cr_parent     FOREIGN KEY (parent_request_id) REFERENCES certificate_requests(id) ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT chk_cr_validity  CHECK (validity_days BETWEEN 1 AND 3650)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE approval_step_actions (
    id         CHAR(36) NOT NULL DEFAULT (UUID()),
    request_id CHAR(36) NOT NULL,
    step_id    CHAR(36) NOT NULL,
    actor_id   CHAR(36) NOT NULL,
    action     ENUM('APPROVED','REJECTED','RETURNED','ESCALATED','DELEGATED') NOT NULL,
    comments   TEXT         NULL,
    acted_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_approval_actions PRIMARY KEY (id),
    CONSTRAINT fk_aa_request       FOREIGN KEY (request_id) REFERENCES certificate_requests(id) ON DELETE CASCADE  ON UPDATE CASCADE,
    CONSTRAINT fk_aa_step          FOREIGN KEY (step_id)    REFERENCES approval_steps(id)       ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_aa_actor         FOREIGN KEY (actor_id)   REFERENCES users(id)                ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE key_metadata (
    id              CHAR(36)     NOT NULL DEFAULT (UUID()),
    request_id      CHAR(36)     NOT NULL,
    key_algorithm   ENUM('RSA_2048','RSA_4096','ECDSA_P256','ECDSA_P384','ED25519') NOT NULL,
    key_size_bits   SMALLINT         NULL,
    public_key_hash VARCHAR(255) NOT NULL,
    public_key_pem  TEXT         NOT NULL,
    key_usage       JSON             NULL,
    hsm_key_id      VARCHAR(255)     NULL,
    generated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_key_metadata   PRIMARY KEY (id),
    CONSTRAINT uq_km_request     UNIQUE (request_id),
    CONSTRAINT uq_km_pubkey_hash UNIQUE (public_key_hash),
    CONSTRAINT fk_km_request     FOREIGN KEY (request_id) REFERENCES certificate_requests(id) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE certificates (
    id               CHAR(36)      NOT NULL DEFAULT (UUID()),
    request_id       CHAR(36)      NOT NULL,
    serial_number    VARCHAR(128)  NOT NULL,
    issuer_dn        VARCHAR(1024) NOT NULL,
    subject_dn       VARCHAR(1024) NOT NULL,
    common_name      VARCHAR(255)  NOT NULL,
    cert_type        ENUM('SSL_TLS','CODE_SIGNING','CLIENT_AUTH','EMAIL','DOCUMENT_SIGNING','ROOT_CA','INTERMEDIATE_CA') NOT NULL,
    certificate_pem  TEXT          NOT NULL,
    certificate_hash VARCHAR(255)  NOT NULL,
    thumbprint_sha1  VARCHAR(40)       NULL,
    not_before       DATETIME      NOT NULL,
    not_after        DATETIME      NOT NULL,
    issued_by        CHAR(36)          NULL,
    ca_name          VARCHAR(255)      NULL,
    ocsp_url         VARCHAR(512)      NULL,
    crl_dp_url       VARCHAR(512)      NULL,
    status           ENUM('ACTIVE','EXPIRED','REVOKED','SUSPENDED','SUPERSEDED') NOT NULL DEFAULT 'ACTIVE',
    version          SMALLINT      NOT NULL DEFAULT 1,
    renewal_of       CHAR(36)          NULL,
    metadata         JSON              NULL,
    is_deleted       TINYINT(1)    NOT NULL DEFAULT 0,
    deleted_at       DATETIME          NULL,
    created_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_certificates  PRIMARY KEY (id),
    CONSTRAINT uq_cert_serial   UNIQUE (serial_number),
    CONSTRAINT uq_cert_hash     UNIQUE (certificate_hash),
    CONSTRAINT fk_cert_request  FOREIGN KEY (request_id) REFERENCES certificate_requests(id) ON DELETE RESTRICT  ON UPDATE CASCADE,
    CONSTRAINT fk_cert_issuer   FOREIGN KEY (issued_by)  REFERENCES users(id)                ON DELETE SET NULL  ON UPDATE CASCADE,
    CONSTRAINT fk_cert_renewal  FOREIGN KEY (renewal_of) REFERENCES certificates(id)         ON DELETE SET NULL  ON UPDATE CASCADE,
    CONSTRAINT chk_cert_dates   CHECK (not_after > not_before)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE certificate_status_history (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    certificate_id CHAR(36)        NOT NULL,
    old_status     VARCHAR(20)         NULL,
    new_status     VARCHAR(20)     NOT NULL,
    changed_by     CHAR(36)            NULL,
    reason         TEXT                NULL,
    changed_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_cert_status_hist PRIMARY KEY (id),
    CONSTRAINT fk_csh_certificate  FOREIGN KEY (certificate_id) REFERENCES certificates(id) ON DELETE CASCADE  ON UPDATE CASCADE,
    CONSTRAINT fk_csh_actor        FOREIGN KEY (changed_by)     REFERENCES users(id)        ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE revocation_list (
    id                CHAR(36)       NOT NULL DEFAULT (UUID()),
    certificate_id    CHAR(36)       NOT NULL,
    serial_number     VARCHAR(128)   NOT NULL,
    revocation_reason ENUM('UNSPECIFIED','KEY_COMPROMISE','CA_COMPROMISE','AFFILIATION_CHANGED',
                           'SUPERSEDED','CESSATION_OF_OPERATION','CERTIFICATE_HOLD',
                           'REMOVE_FROM_CRL','PRIVILEGE_WITHDRAWN','AA_COMPROMISE')
                                     NOT NULL DEFAULT 'UNSPECIFIED',
    revoked_by        CHAR(36)           NULL,
    revoked_at        DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    hold_instruction  VARCHAR(100)       NULL,
    invalidity_date   DATETIME           NULL,
    crl_sequence      BIGINT UNSIGNED    NULL,
    CONSTRAINT pk_revocation_list PRIMARY KEY (id),
    CONSTRAINT uq_rl_certificate  UNIQUE (certificate_id),
    CONSTRAINT fk_rl_certificate  FOREIGN KEY (certificate_id) REFERENCES certificates(id) ON DELETE RESTRICT  ON UPDATE CASCADE,
    CONSTRAINT fk_rl_revoker      FOREIGN KEY (revoked_by)     REFERENCES users(id)        ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### 3.5 Audit & Notification Layer

```sql
CREATE TABLE audit_logs (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    event_time       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actor_id         CHAR(36)            NULL,
    actor_ip         VARCHAR(45)         NULL,
    actor_user_agent VARCHAR(512)        NULL,
    organization_id  CHAR(36)            NULL,
    event_type       VARCHAR(100)    NOT NULL,
    event_category   ENUM('AUTH','USER','CERTIFICATE','APPROVAL','ADMIN','SYSTEM','SECURITY') NOT NULL,
    severity         ENUM('INFO','WARNING','ERROR','CRITICAL') NOT NULL DEFAULT 'INFO',
    resource_type    VARCHAR(50)         NULL,
    resource_id      VARCHAR(36)         NULL,
    action_detail    JSON                NULL,
    outcome          ENUM('SUCCESS','FAILURE','PARTIAL') NOT NULL DEFAULT 'SUCCESS',
    session_id       VARCHAR(128)        NULL,
    correlation_id   VARCHAR(128)        NULL,
    CONSTRAINT pk_audit_logs PRIMARY KEY (id, event_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  PARTITION BY RANGE (YEAR(event_time) * 100 + MONTH(event_time)) (
    PARTITION p202601 VALUES LESS THAN (202602),
    PARTITION p202602 VALUES LESS THAN (202603),
    PARTITION p202603 VALUES LESS THAN (202604),
    PARTITION p202604 VALUES LESS THAN (202605),
    PARTITION p202605 VALUES LESS THAN (202606),
    PARTITION p202606 VALUES LESS THAN (202607),
    PARTITION p202607 VALUES LESS THAN (202608),
    PARTITION p202608 VALUES LESS THAN (202609),
    PARTITION p202609 VALUES LESS THAN (202610),
    PARTITION p202610 VALUES LESS THAN (202611),
    PARTITION p202611 VALUES LESS THAN (202612),
    PARTITION p202612 VALUES LESS THAN (202701),
    PARTITION p_future VALUES LESS THAN MAXVALUE
);

CREATE TABLE notifications (
    id             CHAR(36)     NOT NULL DEFAULT (UUID()),
    user_id        CHAR(36)     NOT NULL,
    type           ENUM('CERT_EXPIRY_REMINDER','REQUEST_APPROVED','REQUEST_REJECTED',
                        'ACTION_REQUIRED','CERT_REVOKED','ACCOUNT_LOCKED','SYSTEM_ALERT') NOT NULL,
    title          VARCHAR(255) NOT NULL,
    body           TEXT         NOT NULL,
    reference_id   CHAR(36)         NULL,
    reference_type VARCHAR(50)      NULL,
    channel        ENUM('EMAIL','SMS','IN_APP','WEBHOOK') NOT NULL DEFAULT 'IN_APP',
    is_read        TINYINT(1)   NOT NULL DEFAULT 0,
    sent_at        DATETIME         NULL,
    read_at        DATETIME         NULL,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_notifications PRIMARY KEY (id),
    CONSTRAINT fk_notif_user    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

---

## 4. Indexes

```sql
CREATE INDEX idx_users_org          ON users (organization_id);
CREATE INDEX idx_users_type_status  ON users (user_type, status);
CREATE INDEX idx_users_last_login   ON users (last_login_at);
CREATE INDEX idx_idoc_user          ON identity_documents (user_id);
CREATE INDEX idx_idoc_expiry        ON identity_documents (expiry_date);
CREATE INDEX idx_cr_requester       ON certificate_requests (requester_id);
CREATE INDEX idx_cr_type_status     ON certificate_requests (cert_type, status);
CREATE INDEX idx_cr_submitted       ON certificate_requests (submitted_at);
CREATE INDEX idx_cert_not_after     ON certificates (not_after);
CREATE INDEX idx_cert_type_status   ON certificates (cert_type, status);
CREATE INDEX idx_cert_expiry_active ON certificates (status, not_after, organization_id);
CREATE INDEX idx_rl_serial          ON revocation_list (serial_number);
CREATE INDEX idx_rl_revoked_at      ON revocation_list (revoked_at);
CREATE INDEX idx_al_actor           ON audit_logs (actor_id, event_time);
CREATE INDEX idx_al_resource        ON audit_logs (resource_type, resource_id);
CREATE INDEX idx_al_category        ON audit_logs (event_category, event_time);
CREATE INDEX idx_ur_role            ON user_roles (role_id);
CREATE INDEX idx_ur_expires         ON user_roles (expires_at);
CREATE INDEX idx_notif_user_unread  ON notifications (user_id, is_read, created_at);
```

---

## 5. Sample Queries

```sql
-- Active certificates expiring within 30 days
SELECT c.serial_number, c.common_name, c.cert_type, c.not_after,
       DATEDIFF(c.not_after, NOW()) AS days_remaining, u.email
FROM certificates c
JOIN certificate_requests cr ON cr.id = c.request_id
JOIN users u ON u.id = cr.requester_id
WHERE c.status = 'ACTIVE' AND c.is_deleted = 0
  AND c.not_after BETWEEN NOW() AND DATE_ADD(NOW(), INTERVAL 30 DAY)
ORDER BY c.not_after ASC;

-- Revoke a certificate (transactional)
START TRANSACTION;
  INSERT INTO revocation_list (id, certificate_id, serial_number, revocation_reason, revoked_by)
  SELECT UUID(), c.id, c.serial_number, 'KEY_COMPROMISE', @operator_id
  FROM certificates c WHERE c.id = @cert_id AND c.status = 'ACTIVE';

  UPDATE certificates SET status = 'REVOKED' WHERE id = @cert_id AND status = 'ACTIVE';

  INSERT INTO certificate_status_history (certificate_id, old_status, new_status, changed_by)
  VALUES (@cert_id, 'ACTIVE', 'REVOKED', @operator_id);

  INSERT INTO audit_logs (actor_id, event_type, event_category, severity, resource_type, resource_id, outcome)
  VALUES (@operator_id, 'CERT_REVOKED', 'CERTIFICATE', 'CRITICAL', 'certificates', @cert_id, 'SUCCESS');
COMMIT;

-- Check user permission
SELECT COUNT(*) AS has_permission
FROM users u
JOIN user_roles ur      ON ur.user_id = u.id AND (ur.expires_at IS NULL OR ur.expires_at > NOW())
JOIN role_permissions rp ON rp.role_id = ur.role_id
JOIN permissions p       ON p.id = rp.permission_id AND p.code = @permission_code
WHERE u.id = @user_id AND u.status = 'ACTIVE' AND u.is_deleted = 0;
```

---

## 6. UUID vs AUTO_INCREMENT Strategy

| Concern | UUID CHAR(36) | BIGINT AUTO_INCREMENT |
|---|---|---|
| Enumeration attack | Safe — unpredictable | Exposed — sequential |
| Insert performance | Slight overhead | Optimal append-only |
| Replication safety | Globally unique | Conflicts on merge |
| **Recommendation** | All entity PKs | audit_logs, status_history, log_request_details |

---

## 7. Cascading Rules Summary

| Relationship | ON DELETE | ON UPDATE |
|---|---|---|
| users → organizations | SET NULL | CASCADE |
| certificate_requests → users | RESTRICT | CASCADE |
| certificates → certificate_requests | RESTRICT | CASCADE |
| revocation_list → certificates | RESTRICT | CASCADE |
| user_roles → users | CASCADE | CASCADE |
| audit_logs actor_id → users | SET NULL | CASCADE |
| log_request_details → request_details | RESTRICT | CASCADE |

---

## 8. Seed Data

```sql
INSERT INTO roles (id, name, scope, is_system) VALUES
  (UUID(), 'SUPER_ADMIN', 'GLOBAL', 1),
  (UUID(), 'RA_ADMIN',    'ORGANIZATION', 1),
  (UUID(), 'RA_OPERATOR', 'ORGANIZATION', 1),
  (UUID(), 'END_USER',    'ORGANIZATION', 1),
  (UUID(), 'AUDITOR',     'GLOBAL', 1);

INSERT INTO permissions (id, code, module, action) VALUES
  (UUID(), 'CERT_REQUEST_CREATE',  'CERTIFICATES', 'CREATE'),
  (UUID(), 'CERT_REQUEST_APPROVE', 'CERTIFICATES', 'APPROVE'),
  (UUID(), 'CERT_REVOKE',          'CERTIFICATES', 'REVOKE'),
  (UUID(), 'CERT_SUSPEND',         'CERTIFICATES', 'SUSPEND'),
  (UUID(), 'AUDIT_READ',           'AUDIT',        'READ'),
  (UUID(), 'KYC_VERIFY',           'IDENTITY',     'VERIFY'),
  (UUID(), 'ROLE_ASSIGN',          'RBAC',         'ASSIGN'),
  (UUID(), 'USER_LOCK',            'USERS',        'LOCK'),
  (UUID(), 'WORKFLOW_MANAGE',      'WORKFLOWS',    'MANAGE'),
  (UUID(), 'ORG_MANAGE',           'ORGANIZATIONS','MANAGE');
```

---

## 10. Active Directory Authentication Tables

### 10.1 AD Authentication Flow

```
End Entity submits credentials (UPN + password)
        |
        v
Spring Security -> ActiveDirectoryLdapAuthenticationProvider
        |  (adDomain -> ad_configurations.ad_domain)
        |  (adUrl    -> ad_configurations.ldap_url)
        |  (adRootDn -> ad_configurations.root_dn)
        |
        +--[FAIL]--> ad_auth_attempts (outcome=FAILURE) --> lockout check
        |
        +--[SUCCESS]--> fetch AD groups from DC
                |
                +--> ad_group_role_mappings  -> resolve local RBAC roles
                +--> ad_user_mappings        -> JIT-provision users row if new
                +--> ad_auth_sessions        -> create session row
                +--> ad_auth_attempts        -> outcome=SUCCESS
```

### 10.2 ad_configurations

```sql
CREATE TABLE ad_configurations (
    id                    CHAR(36)          NOT NULL DEFAULT (UUID()),
    organization_id       CHAR(36)          NOT NULL,
    config_name           VARCHAR(100)      NOT NULL,
    ad_domain             VARCHAR(255)      NOT NULL,
    ldap_url              VARCHAR(512)      NOT NULL,
    ldap_port             SMALLINT UNSIGNED NOT NULL DEFAULT 389,
    use_ssl               TINYINT(1)        NOT NULL DEFAULT 0,
    use_start_tls         TINYINT(1)        NOT NULL DEFAULT 0,
    root_dn               VARCHAR(512)      NOT NULL,
    user_search_base      VARCHAR(512)          NULL,
    user_search_filter    VARCHAR(512)          NULL DEFAULT '(&(objectClass=user)(sAMAccountName={0}))',
    group_search_base     VARCHAR(512)          NULL,
    bind_dn               VARCHAR(512)          NULL,
    bind_secret_ref       VARCHAR(512)          NULL COMMENT 'Vault key for bind password -- never store password here',
    connection_timeout_ms INT UNSIGNED      NOT NULL DEFAULT 5000,
    read_timeout_ms       INT UNSIGNED      NOT NULL DEFAULT 10000,
    referral_handling     ENUM('FOLLOW','IGNORE','THROW') NOT NULL DEFAULT 'FOLLOW',
    priority              TINYINT           NOT NULL DEFAULT 1,
    is_active             TINYINT(1)        NOT NULL DEFAULT 1,
    last_health_check     DATETIME              NULL,
    health_status         ENUM('UNKNOWN','REACHABLE','UNREACHABLE','AUTH_FAILED') NOT NULL DEFAULT 'UNKNOWN',
    is_deleted            TINYINT(1)        NOT NULL DEFAULT 0,
    deleted_at            DATETIME              NULL,
    created_by            CHAR(36)              NULL,
    created_at            DATETIME          NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME          NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_ad_configurations   PRIMARY KEY (id),
    CONSTRAINT uq_ad_cfg_org_name     UNIQUE (organization_id, config_name),
    CONSTRAINT fk_adcfg_org           FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_adcfg_creator       FOREIGN KEY (created_by)      REFERENCES users(id)          ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT chk_adcfg_port         CHECK (ldap_port BETWEEN 1 AND 65535),
    CONSTRAINT chk_adcfg_ssl_tls      CHECK (NOT (use_ssl = 1 AND use_start_tls = 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### 10.3 ad_user_mappings

```sql
CREATE TABLE ad_user_mappings (
    id                  CHAR(36)      NOT NULL DEFAULT (UUID()),
    user_id             CHAR(36)      NOT NULL,
    ad_config_id        CHAR(36)      NOT NULL,
    sam_account_name    VARCHAR(256)  NOT NULL,
    user_principal_name VARCHAR(320)  NOT NULL,
    distinguished_name  VARCHAR(1024) NOT NULL,
    object_guid         CHAR(36)      NOT NULL COMMENT 'Stable across AD renames',
    object_sid          VARCHAR(256)      NULL,
    ad_display_name     VARCHAR(255)      NULL,
    ad_email            VARCHAR(320)      NULL,
    ad_department       VARCHAR(255)      NULL,
    ad_job_title        VARCHAR(255)      NULL,
    ad_account_disabled TINYINT(1)    NOT NULL DEFAULT 0,
    ad_account_locked   TINYINT(1)    NOT NULL DEFAULT 0,
    ad_password_expired TINYINT(1)    NOT NULL DEFAULT 0,
    provisioning_type   ENUM('JIT','PRE_PROVISIONED','SYNC') NOT NULL DEFAULT 'JIT',
    first_seen_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    last_sync_at        DATETIME          NULL,
    is_active           TINYINT(1)    NOT NULL DEFAULT 1,
    deactivated_at      DATETIME          NULL,
    deactivated_reason  VARCHAR(255)      NULL,
    CONSTRAINT pk_ad_user_mappings  PRIMARY KEY (id),
    CONSTRAINT uq_admap_user        UNIQUE (user_id),
    CONSTRAINT uq_admap_guid        UNIQUE (object_guid),
    CONSTRAINT uq_admap_upn_cfg     UNIQUE (ad_config_id, user_principal_name),
    CONSTRAINT fk_admap_user        FOREIGN KEY (user_id)      REFERENCES users(id)             ON DELETE CASCADE  ON UPDATE CASCADE,
    CONSTRAINT fk_admap_config      FOREIGN KEY (ad_config_id) REFERENCES ad_configurations(id) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### 10.4 ad_group_role_mappings

```sql
CREATE TABLE ad_group_role_mappings (
    id              CHAR(36)      NOT NULL DEFAULT (UUID()),
    ad_config_id    CHAR(36)      NOT NULL,
    organization_id CHAR(36)          NULL,
    ad_group_dn     VARCHAR(1024) NOT NULL,
    ad_group_name   VARCHAR(256)  NOT NULL,
    ad_group_sid    VARCHAR(256)      NULL,
    role_id         CHAR(36)      NOT NULL,
    grant_type      ENUM('DIRECT','NESTED') NOT NULL DEFAULT 'DIRECT',
    is_active       TINYINT(1)    NOT NULL DEFAULT 1,
    mapped_by       CHAR(36)          NULL,
    mapped_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_ad_group_role_mappings PRIMARY KEY (id),
    CONSTRAINT uq_adgrp_dn_role          UNIQUE (ad_config_id, ad_group_dn, role_id),
    CONSTRAINT fk_adgrp_config           FOREIGN KEY (ad_config_id)    REFERENCES ad_configurations(id) ON DELETE CASCADE  ON UPDATE CASCADE,
    CONSTRAINT fk_adgrp_org              FOREIGN KEY (organization_id)  REFERENCES organizations(id)    ON DELETE CASCADE  ON UPDATE CASCADE,
    CONSTRAINT fk_adgrp_role             FOREIGN KEY (role_id)          REFERENCES roles(id)            ON DELETE CASCADE  ON UPDATE CASCADE,
    CONSTRAINT fk_adgrp_mapper           FOREIGN KEY (mapped_by)        REFERENCES users(id)            ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### 10.5 ad_auth_sessions

```sql
CREATE TABLE ad_auth_sessions (
    id                 CHAR(36)     NOT NULL DEFAULT (UUID()),
    user_id            CHAR(36)     NOT NULL,
    ad_mapping_id      CHAR(36)     NOT NULL,
    session_token_hash VARCHAR(255) NOT NULL COMMENT 'SHA-256(JSESSIONID) -- raw token never stored',
    login_ip           VARCHAR(45)  NOT NULL,
    login_user_agent   VARCHAR(512)     NULL,
    login_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ad_groups_snapshot JSON             NULL,
    roles_granted      JSON             NULL,
    status             ENUM('ACTIVE','EXPIRED','LOGGED_OUT','REVOKED','SUPERSEDED') NOT NULL DEFAULT 'ACTIVE',
    expires_at         DATETIME     NOT NULL,
    last_activity_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    logout_at          DATETIME         NULL,
    logout_reason      VARCHAR(255)     NULL,
    revoked_by         CHAR(36)         NULL,
    CONSTRAINT pk_ad_auth_sessions PRIMARY KEY (id),
    CONSTRAINT uq_ads_token_hash   UNIQUE (session_token_hash),
    CONSTRAINT fk_ads_user         FOREIGN KEY (user_id)       REFERENCES users(id)            ON DELETE CASCADE  ON UPDATE CASCADE,
    CONSTRAINT fk_ads_mapping      FOREIGN KEY (ad_mapping_id) REFERENCES ad_user_mappings(id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_ads_revoker      FOREIGN KEY (revoked_by)    REFERENCES users(id)            ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT chk_ads_expiry      CHECK (expires_at > login_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### 10.6 ad_auth_attempts

```sql
CREATE TABLE ad_auth_attempts (
    id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    attempted_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    attempted_username VARCHAR(320)    NOT NULL,
    ad_config_id       CHAR(36)            NULL,
    user_id            CHAR(36)            NULL,
    client_ip          VARCHAR(45)     NOT NULL,
    client_user_agent  VARCHAR(512)        NULL,
    outcome            ENUM('SUCCESS','BAD_CREDENTIALS','ACCOUNT_DISABLED','ACCOUNT_LOCKED',
                            'PASSWORD_EXPIRED','ACCOUNT_NOT_FOUND','AD_UNREACHABLE',
                            'AD_BIND_FAILED','SESSION_LIMIT_EXCEEDED','UNKNOWN_ERROR') NOT NULL,
    failure_detail     VARCHAR(512)        NULL,
    session_id         CHAR(36)            NULL,
    is_suspicious      TINYINT(1)      NOT NULL DEFAULT 0,
    CONSTRAINT pk_ad_auth_attempts  PRIMARY KEY (id, attempted_at),
    CONSTRAINT fk_adattempt_config  FOREIGN KEY (ad_config_id) REFERENCES ad_configurations(id) ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT fk_adattempt_user    FOREIGN KEY (user_id)      REFERENCES users(id)             ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT fk_adattempt_session FOREIGN KEY (session_id)   REFERENCES ad_auth_sessions(id)  ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  PARTITION BY RANGE (YEAR(attempted_at) * 100 + MONTH(attempted_at)) (
    PARTITION p202601 VALUES LESS THAN (202602), PARTITION p202602 VALUES LESS THAN (202603),
    PARTITION p202603 VALUES LESS THAN (202604), PARTITION p202604 VALUES LESS THAN (202605),
    PARTITION p202605 VALUES LESS THAN (202606), PARTITION p202606 VALUES LESS THAN (202607),
    PARTITION p202607 VALUES LESS THAN (202608), PARTITION p202608 VALUES LESS THAN (202609),
    PARTITION p202609 VALUES LESS THAN (202610), PARTITION p202610 VALUES LESS THAN (202611),
    PARTITION p202611 VALUES LESS THAN (202612), PARTITION p202612 VALUES LESS THAN (202701),
    PARTITION p_future VALUES LESS THAN MAXVALUE
);
```

### 10.7 AD Indexes & Sample Queries

```sql
CREATE INDEX idx_adcfg_org_active   ON ad_configurations   (organization_id, is_active, priority);
CREATE INDEX idx_admap_sam          ON ad_user_mappings    (sam_account_name);
CREATE INDEX idx_adgrp_config       ON ad_group_role_mappings (ad_config_id, is_active);
CREATE INDEX idx_ads_user_status    ON ad_auth_sessions    (user_id, status);
CREATE INDEX idx_ads_status_expiry  ON ad_auth_sessions    (status, expires_at);
CREATE INDEX idx_adatt_ip_time      ON ad_auth_attempts    (client_ip, attempted_at, outcome);
CREATE INDEX idx_adatt_user_time    ON ad_auth_attempts    (attempted_username, attempted_at, outcome);

-- Brute-force check: failed attempts from IP in last 15 min
SELECT COUNT(*) AS failure_count FROM ad_auth_attempts
WHERE client_ip = ? AND outcome != 'SUCCESS'
  AND attempted_at >= DATE_SUB(NOW(), INTERVAL 15 MINUTE);

-- Resolve AD groups to local roles at login
SELECT DISTINCT r.id, r.name FROM ad_group_role_mappings grm
JOIN roles r ON r.id = grm.role_id
WHERE grm.ad_config_id = ? AND grm.is_active = 1 AND r.is_deleted = 0
  AND grm.ad_group_dn IN ('CN=PKI-Users,OU=Groups,DC=corp,DC=example,DC=com');

-- Force-revoke all sessions for a user
UPDATE ad_auth_sessions SET status = 'REVOKED', logout_at = NOW(),
       logout_reason = 'Admin forced logout', revoked_by = ?
WHERE  user_id = ? AND status = 'ACTIVE';
```

| Security Concern | How Handled |
|---|---|
| Bind password | Never stored — `bind_secret_ref` resolves from Vault/AWS SM at runtime |
| JSESSIONID | Stored as SHA-256 hash only |
| AD identity stability | `object_guid` used as anchor — survives username/UPN renames |
| Brute-force | Index on `(client_ip, attempted_at, outcome)` for sub-ms lockout checks |
| Nested groups | `grant_type = NESTED` signals app to walk `memberOf` tree |

---

## 11. request_details Table

### 11.1 Design Rationale

```
End Entity POST /api/v1/certificate-request
Header: X-EE-Transaction-ID: EE-20260420-a3f8c2d1   <- client idempotency key

        request_id      = UUID()              <- server-generated PK
        ee_transaction_id = EE-20260420-...   <- UNIQUE, prevents duplicates on retry

Decoupled from certificate_requests:
  captures raw request even if rejected before workflow starts.
After workflow approval: certificate_request_id FK is populated.
```

### 11.2 SQL CREATE TABLE

```sql
CREATE TABLE request_details (

    request_id              CHAR(36)     NOT NULL DEFAULT (UUID())
                            COMMENT 'Server-generated UUID -- never client-controlled',
    ee_transaction_id       VARCHAR(128) NOT NULL
                            COMMENT 'Client idempotency key -- duplicates rejected HTTP 409',
    user_id                 CHAR(36)     NOT NULL,
    organization_id         CHAR(36)         NULL,
    ad_session_id           CHAR(36)         NULL,
    request_type            ENUM('CERTIFICATE_ISSUANCE','CERTIFICATE_RENEWAL',
                                 'CERTIFICATE_REVOCATION','CERTIFICATE_SUSPENSION',
                                 'CERTIFICATE_REACTIVATION','KEY_UPDATE',
                                 'PROFILE_UPDATE','IDENTITY_VERIFICATION') NOT NULL,
    request_source          ENUM('WEB_PORTAL','REST_API','MOBILE_APP','BATCH_UPLOAD','SYSTEM')
                                         NOT NULL DEFAULT 'WEB_PORTAL',
    api_version             VARCHAR(10)      NULL,
    status                  ENUM('RECEIVED','VALIDATED','DUPLICATE','REJECTED',
                                 'PROCESSING','COMPLETED','FAILED') NOT NULL DEFAULT 'RECEIVED',
    request_payload         JSON             NULL,
    payload_hash            VARCHAR(255)     NULL COMMENT 'SHA-256 of normalised payload',
    certificate_request_id  CHAR(36)         NULL COMMENT 'Populated after workflow creates cert request',
    client_ip               VARCHAR(45)  NOT NULL,
    client_user_agent       VARCHAR(512)     NULL,
    forwarded_for           VARCHAR(512)     NULL,
    tls_version             VARCHAR(10)      NULL,
    tls_cipher_suite        VARCHAR(128)     NULL,
    correlation_id          VARCHAR(128)     NULL,
    validation_errors       JSON             NULL,
    rejection_reason        VARCHAR(512)     NULL,
    received_at             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
                            COMMENT 'Server timestamp -- not client-supplied',
    validated_at            DATETIME         NULL,
    processing_started_at   DATETIME         NULL,
    completed_at            DATETIME         NULL,
    updated_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted              TINYINT(1)   NOT NULL DEFAULT 0,
    deleted_at              DATETIME         NULL,

    CONSTRAINT pk_request_details          PRIMARY KEY (request_id),
    CONSTRAINT uq_rd_ee_transaction_id     UNIQUE (ee_transaction_id),
    CONSTRAINT uq_rd_org_payload_hash      UNIQUE (organization_id, payload_hash),
    CONSTRAINT fk_rd_user                  FOREIGN KEY (user_id)
        REFERENCES users(id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_rd_organization          FOREIGN KEY (organization_id)
        REFERENCES organizations(id) ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT fk_rd_ad_session            FOREIGN KEY (ad_session_id)
        REFERENCES ad_auth_sessions(id) ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT fk_rd_certificate_request   FOREIGN KEY (certificate_request_id)
        REFERENCES certificate_requests(id) ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT chk_rd_deleted              CHECK (
        (is_deleted = 0 AND deleted_at IS NULL) OR (is_deleted = 1 AND deleted_at IS NOT NULL)),
    CONSTRAINT chk_rd_completed            CHECK (completed_at IS NULL OR completed_at >= received_at),
    CONSTRAINT chk_rd_processing           CHECK (processing_started_at IS NULL OR processing_started_at >= received_at)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### 11.3 Indexes & Sample Queries

```sql
CREATE INDEX idx_rd_user_status_time ON request_details (user_id, status, received_at);
CREATE INDEX idx_rd_org_type_status  ON request_details (organization_id, request_type, status);
CREATE INDEX idx_rd_cert_request     ON request_details (certificate_request_id);
CREATE INDEX idx_rd_ad_session       ON request_details (ad_session_id, received_at);
CREATE INDEX idx_rd_client_ip_time   ON request_details (client_ip, received_at);
CREATE INDEX idx_rd_correlation      ON request_details (correlation_id);

-- Idempotency check
SELECT request_id, status, received_at
FROM   request_details WHERE ee_transaction_id = ? LIMIT 1;
-- Row exists  -> HTTP 200 with existing request_id (idempotent)
-- No row      -> proceed with INSERT

-- Insert new request
INSERT INTO request_details (
    request_id, ee_transaction_id, user_id, organization_id, ad_session_id,
    request_type, request_source, api_version, status, request_payload,
    payload_hash, client_ip, client_user_agent, tls_version, correlation_id, received_at
) VALUES (UUID(), ?, ?, ?, ?, ?, ?, ?, 'RECEIVED', ?, SHA2(?,256), ?, ?, ?, ?, NOW());

-- Link to cert request after workflow
UPDATE request_details
SET    certificate_request_id = ?, status = 'PROCESSING', processing_started_at = NOW()
WHERE  request_id = ? AND status = 'VALIDATED';
```

**ee_transaction_id format:** `EE-{YYYYMMDD}-{UUIDv4-short}`  e.g. `EE-20260420-a3f8c2d1`

---

## 12. log_request_details Table

### 12.1 Design Rationale

Append-only status history for every `request_details` row.
One row inserted each time `request_details.status` changes. Never updated.

```
RECEIVED --> VALIDATED --> PROCESSING --> COMPLETED
    |              |              |
    |              |              +--------> FAILED
    |              |
    +--> REJECTED  +--> DUPLICATE
```

### 12.2 SQL CREATE TABLE

```sql
CREATE TABLE log_request_details (

    log_id                     BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    event_time                 DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    request_id                 CHAR(36)        NOT NULL COMMENT 'FK to request_details.request_id',
    previous_status            ENUM('RECEIVED','VALIDATED','DUPLICATE','REJECTED',
                                    'PROCESSING','COMPLETED','FAILED') NULL
                               COMMENT 'NULL on the very first log entry',
    new_status                 ENUM('RECEIVED','VALIDATED','DUPLICATE','REJECTED',
                                    'PROCESSING','COMPLETED','FAILED') NOT NULL,
    time_in_previous_status_ms BIGINT UNSIGNED     NULL COMMENT 'ms in previous_status; for SLA reporting',
    triggered_by               ENUM('SYSTEM','END_ENTITY','RA_OPERATOR','RA_ADMIN','API_GATEWAY')
                                                NOT NULL DEFAULT 'SYSTEM',
    actor_user_id              CHAR(36)            NULL COMMENT 'NULL for SYSTEM events',
    actor_ip                   VARCHAR(45)         NULL,
    event_type                 VARCHAR(100)    NOT NULL
                               COMMENT 'e.g. PAYLOAD_VALIDATION_PASSED, WORKFLOW_TRIGGERED',
    event_category             ENUM('INTAKE','VALIDATION','AUTHENTICATION','AUTHORIZATION',
                                    'WORKFLOW','PROCESSING','COMPLETION','ERROR')
                                               NOT NULL DEFAULT 'INTAKE',
    severity                   ENUM('INFO','WARNING','ERROR','CRITICAL') NOT NULL DEFAULT 'INFO',
    event_message              VARCHAR(512)        NULL,
    event_detail               JSON                NULL COMMENT 'No PII or key material',
    correlation_id             VARCHAR(128)        NULL,

    CONSTRAINT pk_log_request_details PRIMARY KEY (log_id, event_time),

    CONSTRAINT fk_lrd_request   FOREIGN KEY (request_id)
        REFERENCES request_details(request_id) ON DELETE RESTRICT ON UPDATE CASCADE,

    CONSTRAINT fk_lrd_actor     FOREIGN KEY (actor_user_id)
        REFERENCES users(id) ON DELETE SET NULL ON UPDATE CASCADE,

    CONSTRAINT chk_lrd_status_changed CHECK (
        previous_status IS NULL OR previous_status != new_status),

    CONSTRAINT chk_lrd_system_actor CHECK (
        (triggered_by = 'SYSTEM' AND actor_user_id IS NULL) OR triggered_by != 'SYSTEM')

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  PARTITION BY RANGE (YEAR(event_time) * 100 + MONTH(event_time)) (
    PARTITION p202601 VALUES LESS THAN (202602), PARTITION p202602 VALUES LESS THAN (202603),
    PARTITION p202603 VALUES LESS THAN (202604), PARTITION p202604 VALUES LESS THAN (202605),
    PARTITION p202605 VALUES LESS THAN (202606), PARTITION p202606 VALUES LESS THAN (202607),
    PARTITION p202607 VALUES LESS THAN (202608), PARTITION p202608 VALUES LESS THAN (202609),
    PARTITION p202609 VALUES LESS THAN (202610), PARTITION p202610 VALUES LESS THAN (202611),
    PARTITION p202611 VALUES LESS THAN (202612), PARTITION p202612 VALUES LESS THAN (202701),
    PARTITION p_future VALUES LESS THAN MAXVALUE
);
```

### 12.3 Indexes & Sample Queries

```sql
CREATE INDEX idx_lrd_request_time    ON log_request_details (request_id, event_time ASC);
CREATE INDEX idx_lrd_new_status_time ON log_request_details (new_status, event_time);
CREATE INDEX idx_lrd_actor_time      ON log_request_details (actor_user_id, event_time);
CREATE INDEX idx_lrd_severity_time   ON log_request_details (severity, event_time);
CREATE INDEX idx_lrd_correlation     ON log_request_details (correlation_id, event_time);
CREATE INDEX idx_lrd_category_time   ON log_request_details (event_category, event_time);

-- Full status history for a request
SELECT lrd.log_id, lrd.previous_status, lrd.new_status, lrd.event_time,
       lrd.event_type, lrd.severity, lrd.event_message,
       lrd.time_in_previous_status_ms, lrd.triggered_by, u.username AS actor
FROM   log_request_details lrd
LEFT JOIN users u ON u.id = lrd.actor_user_id
WHERE  lrd.request_id = ? ORDER BY lrd.log_id ASC;

-- SLA: avg time RECEIVED->VALIDATED today
SELECT AVG(time_in_previous_status_ms) AS avg_ms,
       MAX(time_in_previous_status_ms) AS max_ms, COUNT(*) AS total
FROM   log_request_details
WHERE  previous_status = 'RECEIVED' AND new_status = 'VALIDATED'
  AND  event_time >= CURDATE();

-- Error dashboard: all FAILED in last 24h
SELECT lrd.request_id, rd.ee_transaction_id, rd.request_type,
       lrd.event_time, lrd.event_message
FROM   log_request_details lrd
JOIN   request_details rd ON rd.request_id = lrd.request_id
WHERE  lrd.new_status = 'FAILED'
  AND  lrd.event_time >= DATE_SUB(NOW(), INTERVAL 24 HOUR)
ORDER BY lrd.event_time DESC;

-- Insert on every status change
INSERT INTO log_request_details (
    request_id, previous_status, new_status, event_time,
    time_in_previous_status_ms, triggered_by, actor_user_id,
    actor_ip, event_type, event_category, severity, event_message,
    event_detail, correlation_id
) VALUES (?, ?, ?, NOW(), ?, ?, ?, ?, ?, ?, 'INFO', ?, ?, ?);
```

---

## 13. Complete Relationship Summary

```
organizations       (1) --< (M) users
organizations       (1) --< (M) certificate_requests
organizations       (1) --< (M) ad_configurations
organizations       (1) --< (M) request_details

users               (1) --< (M) user_roles         >-- (M) roles
roles               (1) --< (M) role_permissions   >-- (M) permissions
users               (1) --< (M) identity_documents
users               (1) --< (M) certificate_requests
users               (1) --< (1) ad_user_mappings     [JIT on first login]
users               (1) --< (M) ad_auth_sessions
users               (1) --< (M) request_details

certificate_requests (1) --< (M) approval_step_actions
certificate_requests (1) --< (1) certificates
certificate_requests (1) --< (1) key_metadata
approval_workflows   (1) --< (M) approval_steps
certificates         (1) --< (M) certificate_status_history
certificates         (1) --< (1) revocation_list

ad_configurations   (1) --< (M) ad_user_mappings
ad_configurations   (1) --< (M) ad_group_role_mappings >-- (1) roles
ad_configurations   (1) --< (M) ad_auth_attempts
ad_auth_sessions    (1) --< (M) ad_auth_attempts
ad_auth_sessions    (1) --< (M) request_details

request_details     (1) --< (M) log_request_details   [status history]
request_details     (1) -->  (1) certificate_requests  [after workflow]
```

### Table Inventory (24 tables)

| # | Table | Layer | PK Type | Partitioned |
|---|---|---|---|---|
| 1 | organizations | Identity | UUID | No |
| 2 | users | Identity | UUID | No |
| 3 | identity_documents | Identity | UUID | No |
| 4 | roles | RBAC | UUID | No |
| 5 | permissions | RBAC | UUID | No |
| 6 | role_permissions | RBAC | Composite | No |
| 7 | user_roles | RBAC | Composite | No |
| 8 | approval_workflows | Workflow | UUID | No |
| 9 | approval_steps | Workflow | UUID | No |
| 10 | approval_step_actions | Workflow | UUID | No |
| 11 | certificate_requests | Certificate | UUID | No |
| 12 | key_metadata | Certificate | UUID | No |
| 13 | certificates | Certificate | UUID | No |
| 14 | certificate_status_history | Certificate | BIGINT | No |
| 15 | revocation_list | Certificate | UUID | No |
| 16 | audit_logs | Audit | BIGINT | Yes (monthly) |
| 17 | notifications | Audit | UUID | No |
| 18 | ad_configurations | AD Auth | UUID | No |
| 19 | ad_user_mappings | AD Auth | UUID | No |
| 20 | ad_group_role_mappings | AD Auth | UUID | No |
| 21 | ad_auth_sessions | AD Auth | UUID | No |
| 22 | ad_auth_attempts | AD Auth | BIGINT | Yes (monthly) |
| 23 | request_details | Request Tracking | UUID | No |
| 24 | log_request_details | Request Tracking | BIGINT | Yes (monthly) |

---
*PKI-RA Project | MariaDB 10.10+ | InnoDB | utf8mb4_unicode_ci*
