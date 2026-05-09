# RA AD CSR Service — Database Design and ER Diagram

**Document type:** Database Design
**Module:** `pki-ra/subprojects/ra-ad-csr-service`

---

## 1. Overview

The `ra-ad-csr-service` manages five database tables. Three tables are owned by this service (`csr_submission`, `error_code_catalog`, `ra_config`) and two are shared infrastructure tables owned by the `:common` module (`audit_log`, `request_log`). All tables use Flyway versioned migrations and all domain tables extend `BaseAuditEntity` for automatic audit column population.

The database engine for development and testing is **H2 in-memory**. Production uses **MariaDB 10.11+**. Schema is identical across both engines because Flyway migrations use standard SQL with no engine-specific syntax.

---

## 2. Table Inventory

| Table | Owner | Purpose |
|---|---|---|
| `csr_submission` | `ra-ad-csr-service` | Records every submitted CSR with status and metadata |
| `error_code_catalog` | `ra-ad-csr-service` | Master list of all application error codes and messages |
| `ra_config` | `ra-ad-csr-service` | Runtime configuration key-value store |
| `audit_log` | `:common` | Persistent audit trail for all business events |
| `request_log` | `:common` | Inbound HTTP request log for diagnostics |

---

## 3. Table Descriptions

### 3.1 `csr_submission`

This is the primary business table. Every CSR submitted to the service produces exactly one row. The table records the full lifecycle of a CSR submission — from receipt through validation to final disposition.

| Column | Type | Constraint | Description |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | Internal surrogate key |
| `submission_id` | VARCHAR(36) | NOT NULL, UNIQUE | UUID assigned at submission time — exposed in API responses |
| `username` | VARCHAR(100) | NOT NULL | AD sAMAccountName extracted from request header |
| `display_name` | VARCHAR(200) | NOT NULL | AD display name from header |
| `email` | VARCHAR(200) | NOT NULL | AD email from header |
| `csr_pem` | TEXT | NOT NULL | Raw PEM-encoded PKCS#10 CSR |
| `csr_hash` | VARCHAR(64) | NOT NULL, UNIQUE | SHA-256 hex digest of the DER-encoded CSR — used for duplicate detection |
| `subject_cn` | VARCHAR(200) | NOT NULL | Common Name extracted from CSR subject |
| `signature_algorithm` | VARCHAR(100) | NOT NULL | Signature algorithm name from CSR (e.g. SHA256withRSA) |
| `key_algorithm` | VARCHAR(20) | NOT NULL | Public key algorithm (RSA or EC) |
| `key_size` | INT | NULL | RSA key size in bits; NULL for EC keys |
| `ec_curve` | VARCHAR(50) | NULL | EC named curve; NULL for RSA keys |
| `certificate_profile` | VARCHAR(50) | NOT NULL | Profile requested (DSC, TLS_CLIENT, etc.) |
| `status` | VARCHAR(20) | NOT NULL | PENDING, APPROVED, REJECTED, EXPIRED |
| `rejection_reason` | VARCHAR(500) | NULL | Populated when status = REJECTED |
| `source_ip` | VARCHAR(50) | NOT NULL | Remote IP of the proxy that forwarded the request |
| `expires_at` | TIMESTAMP | NULL | Optional expiry for pending submissions |
| `created_at` | TIMESTAMP | NOT NULL | Auto-populated on INSERT |
| `created_by` | VARCHAR(100) | NOT NULL | AD username from `AuditorAwareImpl` |
| `updated_at` | TIMESTAMP | NOT NULL | Auto-updated on every UPDATE |
| `updated_by` | VARCHAR(100) | NOT NULL | AD username of last modifier |

**Indexes:**

| Index Name | Columns | Type | Reason |
|---|---|---|---|
| `uq_csr_submission_id` | `submission_id` | UNIQUE | API lookup by submission ID |
| `uq_csr_hash` | `csr_hash` | UNIQUE | Duplicate detection |
| `idx_csr_username` | `username` | NON-UNIQUE | Query all submissions by a user |
| `idx_csr_status` | `status` | NON-UNIQUE | Filter by status in admin queries |
| `idx_csr_created_at` | `created_at` | NON-UNIQUE | Date-range queries and retention cleanup |

---

### 3.2 `error_code_catalog`

Stores all application error codes, their user-facing messages, HTTP status codes, and support metadata. Loaded entirely into memory on application startup. No record is ever deleted — inactive codes have `is_active = false`.

| Column | Type | Constraint | Description |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | Internal surrogate key |
| `error_code` | VARCHAR(20) | NOT NULL, UNIQUE | Short code (e.g. RA-CSR-001) |
| `error_message` | VARCHAR(500) | NOT NULL | User-facing error message |
| `http_status` | INT | NOT NULL | HTTP status code to return (400, 403, 409, etc.) |
| `category` | VARCHAR(50) | NOT NULL | Error category (CSR, HEADER, IDENTITY, SECURITY, DUPLICATE, SYSTEM) |
| `description` | VARCHAR(1000) | NULL | Internal explanation for developers and support |
| `is_active` | BOOLEAN | NOT NULL | Inactive codes are excluded from cache |
| `created_at` | TIMESTAMP | NOT NULL | Audit — inherited from BaseAuditEntity |
| `created_by` | VARCHAR(100) | NOT NULL | Audit — inherited from BaseAuditEntity |
| `updated_at` | TIMESTAMP | NOT NULL | Audit — inherited from BaseAuditEntity |
| `updated_by` | VARCHAR(100) | NOT NULL | Audit — inherited from BaseAuditEntity |

---

### 3.3 `ra_config`

Stores all runtime configuration parameters as key-value pairs. Loaded entirely into cache on startup. Admin API forces a cache refresh when values are changed in the database.

| Column | Type | Constraint | Description |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | Internal surrogate key |
| `config_key` | VARCHAR(100) | NOT NULL, UNIQUE | Dotted key (e.g. csr.max.size.bytes) |
| `config_value` | VARCHAR(1000) | NOT NULL | String value — caller casts to required type |
| `config_group` | VARCHAR(50) | NOT NULL | Logical group (CSR, SECURITY, AD, AUDIT, SYSTEM) |
| `description` | VARCHAR(500) | NULL | Human-readable explanation |
| `is_active` | BOOLEAN | NOT NULL | Inactive keys excluded from cache |
| `created_at` | TIMESTAMP | NOT NULL | Audit — inherited from BaseAuditEntity |
| `created_by` | VARCHAR(100) | NOT NULL | Audit — inherited from BaseAuditEntity |
| `updated_at` | TIMESTAMP | NOT NULL | Audit — inherited from BaseAuditEntity |
| `updated_by` | VARCHAR(100) | NOT NULL | Audit — inherited from BaseAuditEntity |

---

### 3.4 `audit_log`

Owned by `:common`. Written to by `AuditLogService` using `REQUIRES_NEW` transaction propagation, which guarantees the audit record is committed even if the main business transaction rolls back. This table is never read at request time — it is append-only during normal operation.

| Column | Type | Constraint | Description |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | Internal surrogate key |
| `username` | VARCHAR(100) | NOT NULL | AD sAMAccountName of the acting user |
| `action` | VARCHAR(100) | NOT NULL | Action constant (e.g. CSR_SUBMIT, CSR_SUBMIT_DUPLICATE) |
| `resource_id` | VARCHAR(200) | NULL | Identifier of the affected resource (submission_id) |
| `description` | VARCHAR(1000) | NULL | Human-readable event description |
| `ip_address` | VARCHAR(50) | NULL | Source proxy IP |
| `outcome` | VARCHAR(20) | NOT NULL | SUCCESS or FAILURE |
| `created_at` | TIMESTAMP | NOT NULL | Timestamp of the event |

---

### 3.5 `request_log`

Owned by `:common`. Optionally written by a request logging filter for every inbound HTTP request. Controlled by the `audit.request.log.enabled` config key. Used for diagnostics and traffic analysis — not for business audit purposes.

| Column | Type | Constraint | Description |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | Internal surrogate key |
| `req` | VARCHAR(500) | NOT NULL | Summary of the request (method + path + source IP) |
| `req_ts` | TIMESTAMP | NOT NULL | Timestamp when the request was received |

---

## 4. Entity Relationships

The following describes the logical relationships between tables:

- **`csr_submission` → `audit_log`** — One CSR submission produces one or more audit log entries (one for the initial attempt, additional ones for status changes). The relationship is by `username` and `resource_id` (submission_id) — there is no foreign key constraint, keeping the audit trail decoupled.

- **`error_code_catalog` → `csr_submission`** — No direct foreign key. Error codes are referenced by string constants in exception classes. The catalog is a lookup table only.

- **`ra_config`** — Standalone. No foreign key relationships. Consumed by services via the in-memory cache.

- **`request_log`** — Standalone. No foreign key relationships. Written independently per request.

---

## 5. Flyway Migration Order

Flyway applies migrations in version order. The following is the complete migration plan for Phase 1:

| Version | File | Description |
|---|---|---|
| V1 | `V1__create_schema.sql` | Creates `csr_submission`, `audit_log`, `request_log` tables and all indexes |
| V2 | `V2__seed_error_code_catalog.sql` | Creates `error_code_catalog` table and inserts all 25 error codes |
| V3 | `V3__seed_ra_config.sql` | Creates `ra_config` table and inserts all 27 default configuration entries |

**Rules:**
- Never modify a migration file that has already been applied to any environment
- New changes always go into a new versioned file (V4, V5, etc.)
- Repeatable migrations (prefix R__) are used only for views or stored procedures
- Flyway checksum validation will fail the application on startup if a previously applied migration is altered

---

## 6. Indexing Strategy

| Table | Priority Columns | Rationale |
|---|---|---|
| `csr_submission` | `submission_id`, `csr_hash` | Both are queried on every submission — unique index enforces constraint and speeds lookup |
| `csr_submission` | `username`, `status` | Admin and audit queries filter by these frequently |
| `audit_log` | `username`, `action`, `created_at` | Time-range audit queries and user-specific audit trails |
| `error_code_catalog` | `error_code` | Lookup by code after cache miss (rare) |
| `ra_config` | `config_key` | Unique index already covers lookup; cache means DB is rarely hit |

---

## 7. Data Retention

| Table | Retention Policy | Mechanism |
|---|---|---|
| `csr_submission` | Indefinite (Phase 1) | Future: archive to cold storage after 3 years |
| `audit_log` | Configurable via `audit.log.retain.days` (default 365) | Scheduled cleanup job — Phase 2 |
| `request_log` | 30 days recommended | Scheduled cleanup job — Phase 2 |
| `error_code_catalog` | Permanent (soft delete via `is_active`) | Never hard-deleted |
| `ra_config` | Permanent (soft delete via `is_active`) | Never hard-deleted |

---

## 8. Transaction Boundaries

| Operation | Transaction | Notes |
|---|---|---|
| CSR submission (happy path) | Single `@Transactional` | Wraps `csr_submission` INSERT |
| Audit log write | Separate `REQUIRES_NEW` | Always commits regardless of main TX outcome |
| Request log write | Separate `REQUIRES_NEW` | Decoupled from business TX |
| Config cache load | `@Transactional(readOnly = true)` | Read-only, no locks |
| Error code cache load | `@Transactional(readOnly = true)` | Read-only, no locks |

---

## 9. Naming Conventions

| Convention | Rule |
|---|---|
| Table names | Lowercase with underscores (snake_case) |
| Column names | Lowercase with underscores (snake_case) |
| Index names | `idx_<table>_<column>` or `uq_<table>_<column>` for unique |
| Primary keys | Always named `id` |
| Boolean columns | Prefixed with `is_` (e.g. `is_active`) |
| Timestamp columns | Suffixed with `_at` (e.g. `created_at`, `expires_at`) |
| Enum-like columns | VARCHAR — no DB-level ENUM type (avoids MariaDB migration pain) |
