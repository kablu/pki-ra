# RA AD CSR Service — Logging and Observability

**Document type:** Observability Design
**Module:** `pki-ra/subprojects/ra-ad-csr-service`

---

## 1. Overview

Observability in `ra-ad-csr-service` is built on three pillars:

- **Logging** — Structured, contextual log output at appropriate levels across all layers
- **Audit Trail** — Business event records written to `audit_log` table via `AuditLogService`
- **Request Log** — Inbound HTTP request records written to `request_log` table

Together these three sources give the operations team full visibility into what the service is doing, who is doing it, and what went wrong when something fails.

---

## 2. Logging Framework

The service uses **SLF4J** as the logging API with **Logback** as the implementation (default in Spring Boot). Log output is written to the console in local development and to a rolling file in production.

Log format is structured using a consistent pattern:

| Field | Description |
|---|---|
| Timestamp | ISO-8601 with milliseconds |
| Log Level | TRACE, DEBUG, INFO, WARN, ERROR |
| Thread name | Identifies the request-handling thread |
| MDC fields | Request ID, username, source IP (see Section 3) |
| Logger name | Fully qualified class name |
| Message | Human-readable event description |

---

## 3. MDC — Mapped Diagnostic Context

MDC (Mapped Diagnostic Context) fields are attached to every log record produced during a request. They allow log aggregation tools (ELK, Splunk, Loki) to correlate all log lines belonging to a single request without parsing the message text.

MDC fields are set by `AdIdentityFilter` at the beginning of every request and cleared at the end.

| MDC Key | Value | Source |
|---|---|---|
| `requestId` | UUID generated per request | Generated in filter |
| `username` | AD sAMAccountName | `X-AD-Username` header |
| `displayName` | AD display name | `X-AD-DisplayName` header |
| `sourceIp` | Proxy remote IP | `HttpServletRequest.getRemoteAddr()` |
| `method` | HTTP method (POST, GET) | `HttpServletRequest.getMethod()` |
| `path` | Request URI path | `HttpServletRequest.getRequestURI()` |

**Why MDC matters:**
In concurrent environments, multiple requests are handled simultaneously on different threads. Without MDC, log lines from different requests are interleaved with no way to associate them. With MDC, filtering by `requestId` in any log aggregation tool instantly isolates all log lines for a single request.

---

## 4. Log Levels per Package

| Package | Level (Dev) | Level (Prod) | Reason |
|---|---|---|---|
| `com.pki.ra.adcsr` | DEBUG | INFO | Application code — full detail in dev, business events only in prod |
| `com.pki.ra.common` | DEBUG | INFO | Shared infrastructure |
| `org.springframework.security` | DEBUG | WARN | Security filter chain decisions visible in dev |
| `org.springframework.web` | DEBUG | WARN | Request mapping and validation errors |
| `org.hibernate.SQL` | DEBUG | OFF | SQL statements visible in dev only |
| `org.hibernate.type` | TRACE | OFF | Bind parameter values — dev only |
| `com.zaxxer.hikari` | DEBUG | INFO | Connection pool lifecycle |
| `org.flywaydb` | INFO | INFO | Migration execution always logged |

---

## 5. What Gets Logged at Each Layer

### 5.1 AdIdentityFilter

| Event | Level | Message Pattern |
|---|---|---|
| IP not in whitelist | WARN | Rejected request from untrusted IP: {ip} |
| Required header missing | WARN | Missing required AD header: {headerName} |
| Header value too long | WARN | Header {headerName} exceeds max length {max} for IP {ip} |
| CRLF characters stripped | WARN | CRLF characters stripped from header {headerName} for user {username} |
| Identity extracted successfully | DEBUG | AD identity extracted: username={username} ip={ip} |
| Maintenance mode active | INFO | Request rejected — service in maintenance mode |

### 5.2 CsrSubmissionController

| Event | Level | Message Pattern |
|---|---|---|
| Request received | INFO | CSR submission received: profile={profile} username={username} |
| Submission created | INFO | CSR submission accepted: submissionId={id} |

### 5.3 CsrValidationService

| Event | Level | Message Pattern |
|---|---|---|
| CSR size exceeded | WARN | CSR size {size} bytes exceeds maximum {max} |
| PEM format invalid | WARN | Invalid PEM format for username={username} |
| PKCS#10 parse failed | WARN | PKCS#10 parse error for username={username}: {message} |
| Signature verification failed | WARN | CSR signature verification failed for username={username} |
| Algorithm not allowed | WARN | Algorithm {algo} not in allowed list for username={username} |
| Key size not allowed | WARN | Key size {size} not in allowed list for username={username} |
| Validation passed | DEBUG | CSR validation passed: algo={algo} keySize={size} |

### 5.4 CsrSubmissionService

| Event | Level | Message Pattern |
|---|---|---|
| Identity mismatch | WARN | CN mismatch: csrCn={cn} adUsername={username} adDisplay={display} |
| Duplicate detected | WARN | Duplicate CSR hash detected: existingSubmissionId={id} |
| Submission persisted | INFO | Submission persisted: submissionId={id} username={username} |

### 5.5 GlobalExceptionHandler

| Event | Level | Message Pattern |
|---|---|---|
| Validation error (400) | WARN | Validation error for {path}: {message} |
| Business rule violation (4xx) | WARN | {exceptionClass}: {message} — errorCode={code} |
| Unexpected error (500) | ERROR | Unexpected error for {path} username={username}: {message} |

### 5.6 RaConfigCacheService

| Event | Level | Message Pattern |
|---|---|---|
| Cache loading on startup | INFO | Loading configuration cache from database |
| Cache loaded | INFO | Config cache loaded — {count} active entries |
| Cache refreshed via admin | INFO | Config cache refreshed by {username} — {count} entries |
| Key not found | WARN | Config key not found in cache: {key} |

### 5.7 ErrorCodeCacheService

| Event | Level | Message Pattern |
|---|---|---|
| Cache loading on startup | INFO | Loading error code cache from database |
| Cache loaded | INFO | Error code cache loaded — {count} active codes |
| Error code not found | ERROR | Error code {code} not found in cache — returning generic error |

---

## 6. Audit Log vs Application Log

| Concern | Audit Log | Application Log |
|---|---|---|
| Who did what | Yes — structured DB record | No |
| Business outcome (SUCCESS/FAILURE) | Yes | Partial (WARN/INFO) |
| Technical detail (stack trace) | No | Yes |
| Queryable for compliance | Yes | No (unless indexed in ELK) |
| Survives log rotation | Yes (DB) | Depends on log retention |
| Written even on transaction rollback | Yes (REQUIRES_NEW) | Yes |

The audit log is the authoritative business record. The application log is the technical troubleshooting record. They complement each other — never replace one with the other.

---

## 7. Request Log

The `request_log` table records basic metadata for every inbound HTTP request. It is controlled by the `audit.request.log.enabled` config key. When enabled, the request logging filter writes one row per request before the security filter chain processes it.

| Field logged | Description |
|---|---|
| HTTP method | POST, GET, etc. |
| Request path | URI path only — no query string |
| Source IP | Proxy remote IP |
| Timestamp | When the request arrived |

The request log does not record request bodies or response bodies — these may contain PEM-encoded CSR data or sensitive AD identity information. Body logging would be a data protection concern and is never enabled.

---

## 8. Sensitive Data in Logs

The following data must never appear in application log messages:

| Data | Reason |
|---|---|
| PEM-encoded CSR content | Contains public key — unnecessary in logs |
| AD email address | PII — not needed for troubleshooting |
| Config values | May contain IP addresses or credentials |
| Error code catalog values | No reason to log what is already in the DB |

The `audit.sensitive.fields` config key (default: `password,token,secret`) lists fields whose values are replaced with `***REDACTED***` in audit log descriptions. The application log does not process field-level redaction — developers are responsible for never including sensitive values in log messages.

---

## 9. Log Output Formats

**Development (Console — Human Readable):**
Each log line contains the timestamp in ISO-8601 format with milliseconds, the log level, the thread name in brackets, the MDC context fields (requestId, username, sourceIp) in brackets, the logger class name in abbreviated form, and the human-readable message. This format is easy to read when tailing the console during local development.

**Production (File — JSON Structured):**
Each log event is serialised as a single-line JSON object. The JSON contains all the same fields as the console format — timestamp, level, thread, the MDC context fields as top-level JSON properties, logger name, and message — but as machine-parseable key-value pairs rather than a formatted string. This format allows log aggregation tools such as ELK, Splunk, and Loki to index and query each field independently without applying regex patterns. Filtering by `requestId`, `username`, or `sourceIp` in a log search tool is a simple field equality query, not a text search.

---

## 10. Alerting Recommendations

Operations teams should configure alerts on the following log patterns:

| Alert | Log Pattern | Severity | Action |
|---|---|---|---|
| Repeated IP whitelist rejections | WARN "Rejected request from untrusted IP" | HIGH | Investigate potential network misconfiguration or attack |
| High rate of signature failures | WARN "CSR signature verification failed" | MEDIUM | May indicate a client bug or tampered requests |
| High rate of identity mismatches | WARN "CN mismatch" | HIGH | May indicate attempted impersonation |
| Error code not found in cache | ERROR "Error code not found in cache" | HIGH | Cache/DB inconsistency — trigger manual cache refresh |
| Unexpected 500 errors | ERROR in GlobalExceptionHandler | HIGH | Application bug — requires immediate investigation |
| Maintenance mode activated | INFO "service in maintenance mode" | LOW | Informational — confirm intentional activation |
