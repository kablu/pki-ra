# RA AD CSR Service — Component Design Document

**Document type:** Phase 1 — Detailed Component Design
**Service:** `ra-ad-csr-service`
**Java:** 26 | **Spring Boot:** 4.x | **Spring Security:** 7.x

---

## 1. Module Structure

The module lives at `pki-ra/subprojects/ra-ad-csr-service/` and follows standard Spring Boot multi-module conventions. The main source tree under `src/main/java/com/pki/ra/adcsr/` is organised into the following packages:

- **Root** — Contains `AdCsrServiceApplication`, the Spring Boot entry point.
- **`config/`** — Spring configuration classes: `SecurityConfig` for the security filter chain, `TrustedProxyConfig` for the IP whitelist bean, and `OpenApiConfig` for Swagger.
- **`filter/`** — Contains `AdIdentityFilter`, the `OncePerRequestFilter` that validates every incoming request.
- **`controller/`** — Contains `CsrSubmitController`, which maps `POST /api/ra/ad/csr/submit`.
- **`dto/`** — Data transfer objects: `CsrSubmitRequest` (inbound), `CsrSubmitResponse` (outbound), and `AdIdentity` (the extracted AD identity record).
- **`validation/`** — Validation components: `HeaderValidator`, `Pkcs10CsrValidator` (Bouncy Castle), `CsrIdentityMatcher`, and the `annotation/` sub-package containing the `@ValidCsrPem` custom constraint.
- **`service/`** — Business logic: `CsrValidationService` (pipeline orchestrator) and `CsrSubmissionService` (persistence and audit).
- **`model/`** — JPA entity `CsrSubmissionRecord`, which extends `BaseAuditEntity` from `:common`.
- **`repository/`** — `CsrSubmissionRepository`, a Spring Data JPA repository.
- **`exception/`** — Application exceptions (`CsrValidationException`, `UnauthorizedProxyException`) and `GlobalExceptionHandler`.

The `src/main/resources/` directory contains `application.properties`, profile-specific overrides (`application-h2.properties`, `application-mariadb.properties`, `application-prod.properties`), and `db.properties` (loaded by `:common` `DataSourceConfig`).

The `src/test/java/com/pki/ra/adcsr/` directory mirrors the main structure with test classes for the controller, validation components, and service layer.

---

## 2. Component Descriptions

---

### 2.1 `AdCsrServiceApplication`

Entry point for the Spring Boot application. The class is annotated with `@SpringBootApplication` and sets `scanBasePackages` to `"com.pki.ra"`. This single setting causes Spring to scan both the `com.pki.ra.common.*` package tree (picking up all `:common` infrastructure beans) and the `com.pki.ra.adcsr.*` package tree (picking up all local service beans). No `@Import`, `@ComponentScan`, or `@Bean` re-declaration is required to activate the shared beans.

---

### 2.2 `SecurityConfig`

Configures Spring Security 7 for the service. Its responsibilities are:

- Disable default form login — the service uses header-based identity, not form authentication.
- Enable CSRF protection using the double-submit cookie pattern via `CookieCsrfTokenRepository`.
- Configure CORS to allow only the trusted AD proxy origin.
- Set all security response headers (see table below).
- Restrict access so that only `POST /api/ra/ad/csr/submit`, the actuator health endpoint, and the Swagger UI paths are permitted. All other paths return 403.
- Register `AdIdentityFilter` before the standard authentication filter in the filter chain.

**Security Headers Configured:**

| Header | Value |
|---|---|
| `X-Content-Type-Options` | `nosniff` |
| `X-Frame-Options` | `DENY` |
| `X-XSS-Protection` | `0` (disabled — browser heuristic; rely on CSP) |
| `Content-Security-Policy` | `default-src 'none'; frame-ancestors 'none'` |
| `Strict-Transport-Security` | `max-age=31536000; includeSubDomains` |
| `Referrer-Policy` | `no-referrer` |
| `Cache-Control` | `no-store` |
| `Permissions-Policy` | `geolocation=(), microphone=(), camera=()` |

---

### 2.3 `TrustedProxyConfig`

Loads the allowlist of trusted AD proxy IP addresses from the `ra_config` cache (key `security.proxy.whitelist.ips`). The list is comma-separated and split at startup. It exposes a `Set<String>` of trusted IP strings consumed by `AdIdentityFilter`. Because the list is loaded from `RaConfigCacheService`, it can be refreshed at runtime via the admin API without restarting the service.

---

### 2.4 `AdIdentityFilter`

A `OncePerRequestFilter` that runs before the controller as the first line of defence. It processes every request in the following steps:

1. Extract the client IP from the `X-Forwarded-For` header (outermost hop) or from `RemoteAddr` if the header is absent.
2. Check whether the IP is in the trusted proxy list. If not, return HTTP 403 and throw `UnauthorizedProxyException`.
3. Read the `X-AD-Username` header (header name is configurable via `ra_config`).
4. Read the `X-AD-Email` header (header name is configurable via `ra_config`).
5. Verify that at least one of the two identity headers is present and non-blank. If both are absent, return HTTP 401.
6. Strip any CRLF characters from both header values to prevent header injection attacks.
7. Construct an `AdIdentity` record from the sanitised values and the client IP.
8. Store the `AdIdentity` as a request attribute under the key `"AD_IDENTITY"` for retrieval by the controller.
9. Set a `UsernamePasswordAuthenticationToken` in the Spring Security `SecurityContextHolder` using the AD display name. This enables `AuditorAwareImpl` (from `:common`) to populate `created_by` and `updated_by` audit columns automatically.
10. Call `chain.doFilter()` to pass the request to the next filter.

**Header names are configurable:**

| Property | Default |
|---|---|
| `ra.ad.header.username` | `X-AD-Username` |
| `ra.ad.header.email` | `X-AD-Email` |

---

### 2.5 `AdIdentity` (Record)

An immutable Java record carrying the extracted and pre-validated AD identity. It holds three fields:

| Field | Type | Description |
|---|---|---|
| `username` | String | AD sAMAccountName — may be null if only email header is present |
| `email` | String | UPN or email — may be null if only username header is present |
| `sourceIp` | String | Originating client IP, extracted from `X-Forwarded-For` |

The record also provides a `displayName()` helper that returns `username` if non-null, otherwise `email`. This is used as the canonical identity string for audit log entries and CN matching.

---

### 2.6 `CsrSubmitController`

Single endpoint controller mapped to `POST /api/ra/ad/csr/submit`. It receives three inputs: the `AdIdentity` object injected from the request attribute, the `@Valid CsrSubmitRequest` body, and the `HttpServletRequest` for extracting the source IP. The controller performs no business logic — it logs the incoming request and immediately delegates to `CsrSubmissionService.submit()`. It returns the result as `HTTP 202 Accepted`.

**Response status codes:**

| Status | Meaning |
|---|---|
| 202 Accepted | CSR accepted, returns `submission_id` and `status: PENDING` |
| 400 Bad Request | Validation failed — CSR invalid or header malformed |
| 401 Unauthorized | No AD identity header present |
| 403 Forbidden | Request not from trusted proxy |
| 409 Conflict | Duplicate CSR (same hash already submitted) |
| 422 Unprocessable | CSR parsed but identity does not match AD user |

---

### 2.7 `CsrSubmitRequest` (DTO)

The inbound JSON request body. Fields and their validation constraints:

| Field | JSON Key | Required | Constraints | Description |
|---|---|---|---|---|
| `pkcs10Pem` | `pkcs10_pem` | Yes | Not blank, max 8192 chars, `@ValidCsrPem` | PEM-encoded PKCS#10 CSR |
| `certificateProfile` | `certificate_profile` | No | Must match allowed profile pattern | DSC, DOCUMENT_SIGNING, CODE_SIGNING, TLS_CLIENT, TLS_SERVER, EMAIL_ENCRYPT |
| `validityDays` | `validity_days` | No | Min 1, Max 3650 | Requested certificate validity in days |
| `remarks` | `remarks` | No | Max 500 chars, HTML-sanitised before persistence | Optional applicant note |

---

### 2.8 `HeaderValidator`

A stateless Spring component that validates the `AdIdentity` extracted by the filter. It performs the following checks in order:

1. Verifies that at least one of `username` or `email` is non-blank.
2. If `username` is present: validates it against the pattern `^[a-zA-Z0-9._-]{1,64}$` (characters safe for AD sAMAccountName) and enforces a 64-character maximum length.
3. If `email` is present: validates it against an RFC 5321 email pattern and enforces a 254-character maximum length.
4. Checks that neither value contains null bytes, CRLF sequences, or HTML tags (header injection prevention).

---

### 2.9 `Pkcs10CsrValidator`

A Bouncy Castle-based cryptographic CSR validator. It performs the following checks in order:

| Check | Failure Response |
|---|---|
| 1. Parse PEM or base64-DER | 400 — unparseable CSR |
| 2. Verify CSR self-signature | 400 — signature invalid |
| 3. Key algorithm: RSA or ECDSA only | 400 — unsupported algorithm |
| 4. RSA key size at or above 2048 bits | 400 — key too weak |
| 5. ECDSA curve: P-256, P-384, or P-521 | 400 — unsupported curve |
| 6. Subject CN is present and non-blank | 400 — missing CN |
| 7. CSR size at or below configured maximum | 400 — CSR too large |

On success it returns the parsed `PKCS10CertificationRequest` object for use by downstream components. On any failure it throws `CsrValidationException`.

---

### 2.10 `CsrIdentityMatcher`

Cross-validates the CSR Subject DN against the AD identity. The matching logic is as follows:

1. Extract the Common Name from the CSR Subject using Bouncy Castle's `X500Name` and `BCStyle.CN`.
2. Extract any `rfc822Name` entries from the Subject Alternative Name extension in the CSR attributes.
3. Convert the CN, all SAN emails, the AD username, and the AD email to lowercase for case-insensitive comparison.
4. If AD email is present: check whether the CN equals the email, or whether the SAN email list contains the email. If either matches, accept.
5. If AD username is present: check whether the CN equals the username. If it matches, accept.
6. If no match was found through any of the above paths: throw `CsrIdentityMismatchException` with error code `RA-IDN-001`.

---

### 2.11 `CsrValidationService`

Orchestrates the full validation pipeline by calling three validators in sequence:

1. Calls `HeaderValidator.validate(adIdentity)` — validates AD header values.
2. Calls `Pkcs10CsrValidator.validate(pkcs10Pem)` — performs cryptographic CSR checks and returns the parsed `PKCS10CertificationRequest`.
3. Calls `CsrIdentityMatcher.match(adIdentity, parsedCsr)` — confirms CN matches AD identity.
4. Returns a `CsrValidationResult` containing the parsed CSR, the identity, the requested profile, and the requested validity period.

Any exception from the three validators propagates immediately and the pipeline is aborted.

---

### 2.12 `CsrSubmissionService`

Business logic layer — runs after the validation pipeline succeeds. Its flow:

1. Compute the SHA-256 hash of the raw PKCS#10 PEM bytes.
2. Query `CsrSubmissionRepository.existsByPkcs10Hash(hash)`. If a duplicate is found within the configured time window, write a failure audit log entry and throw `DuplicateCsrException`.
3. Build the `CsrSubmissionRecord` entity with all extracted fields: submission ID (UUID), AD username, AD email, CSR hash, PEM, subject CN, key algorithm, key size, EC curve, profile, status `PENDING`, and source IP.
4. Sanitise the `remarks` field using OWASP Java HTML Sanitizer before assigning it to the entity.
5. Call `repository.save(record)` to persist the entity.
6. Call `auditLogService.logSuccess()` from `:common` with action `CSR_SUBMIT`.
7. Return a `CsrSubmitResponse` with the submission ID, status, submitter name, timestamp, and confirmation message.

On any exception in steps 4 through 7: call `auditLogService.logFailure()` and then re-throw the exception. Because `AuditLogService` runs in `REQUIRES_NEW` propagation, the failure audit record is committed to the database even if the main transaction rolls back.

---

### 2.13 `CsrSubmissionRecord` (JPA Entity)

Extends `BaseAuditEntity` from `:common`. The entity maps to the `csr_submission` table and inherits `created_at`, `created_by`, `updated_at`, and `updated_by` columns from the parent class.

| Column | Type | Description |
|---|---|---|
| `id` | BIGINT PK | Auto-increment surrogate key |
| `submission_id` | VARCHAR(36) | UUID — returned to caller |
| `ad_username` | VARCHAR(100) | sAMAccountName from header |
| `ad_email` | VARCHAR(254) | UPN / email from header |
| `pkcs10_hash` | VARCHAR(64) | SHA-256 hex of raw CSR bytes |
| `pkcs10_pem` | TEXT | Full PEM CSR |
| `subject_cn` | VARCHAR(255) | Extracted from CSR Subject CN |
| `key_algorithm` | VARCHAR(10) | RSA or ECDSA |
| `key_size` | INTEGER | Bits (RSA) or 0 (ECDSA) |
| `ec_curve` | VARCHAR(20) | P-256 / P-384 / P-521 / null |
| `certificate_profile` | VARCHAR(30) | DSC, CODE_SIGNING, etc. |
| `status` | VARCHAR(20) | PENDING / APPROVED / REJECTED / SIGNED |
| `source_ip` | VARCHAR(50) | Client IP from X-Forwarded-For |
| `remarks` | VARCHAR(500) | Applicant note (sanitized) |
| `created_at` | TIMESTAMP | From BaseAuditEntity |
| `created_by` | VARCHAR(100) | From BaseAuditEntity |
| `updated_at` | TIMESTAMP | From BaseAuditEntity |
| `updated_by` | VARCHAR(100) | From BaseAuditEntity |

**Indexes:** `submission_id` (unique), `pkcs10_hash` (unique), `ad_username`, `status`

---

### 2.14 `CsrSubmissionRepository`

Spring Data JPA repository that extends `JpaRepository<CsrSubmissionRecord, Long>`. The following query methods are declared on the interface:

| Method | Return Type | Description |
|---|---|---|
| `existsByPkcs10Hash(hash)` | boolean | Duplicate CSR check — called before every save |
| `findBySubmissionId(id)` | Optional | Status query by submission ID |
| `findByAdUsername(username, pageable)` | Page | List all submissions by an AD user |
| `findByStatus(status, pageable)` | Page | RA operator queue filtered by status |
| `countByAdUsernameAndCreatedAtAfter(username, since)` | long | Rate limit check — submissions per user within a window |

---

### 2.15 `GlobalExceptionHandler`

A `@RestControllerAdvice` that maps all application exceptions to structured error responses. All user-controlled data in error messages is HTML-encoded before inclusion in the response body to prevent reflected XSS.

| Exception | HTTP Status | Title |
|---|---|---|
| `CsrValidationException` | 400 | CSR Validation Failed |
| `CsrIdentityMismatchException` | 422 | Identity Mismatch |
| `UnauthorizedProxyException` | 403 | Forbidden |
| `DuplicateCsrException` | 409 | Duplicate Submission |
| `MethodArgumentNotValidException` | 400 | Validation Failed |
| `PkiRaException` | (from exception field) | Operation Failed |
| `Exception` (fallback) | 500 | Internal Server Error |

---

### 2.16 `ValidCsrPem` (Custom Constraint)

A Jakarta Bean Validation constraint annotation applied to the `pkcs10Pem` field on `CsrSubmitRequest`. It is annotated with `@Constraint`, targeting `ElementType.FIELD`, and references `ValidCsrPemValidator` as its implementing class.

The `ValidCsrPemValidator` performs a fast syntactic check — it verifies that the PEM header and footer markers are present, or that the value is valid Base64-encoded content, before the full Bouncy Castle parse is attempted in `Pkcs10CsrValidator`. This separates syntactic validation (fast, in Spring MVC's validation phase) from cryptographic validation (slower, in the service layer), giving a cleaner error message to callers who submit obviously malformed input.

---

## 3. Security Design

### 3.1 CSRF Protection

The service uses the double-submit cookie pattern. Spring Security generates a cryptographically random CSRF token and stores it in a cookie named `XSRF-TOKEN`. Browser-based clients must read this token from the cookie and include its value in the `X-XSRF-TOKEN` request header. Spring Security compares the two values on every state-changing request. A mismatch results in a 403 Forbidden response. This pattern is effective because an attacker's origin cannot read the cookie value due to the same-origin policy enforced by browsers.

For non-browser API clients such as internal service-to-service calls, CSRF protection can be disabled on a per-endpoint basis via the security configuration, since these clients are not subject to cross-site request forgery attacks.

### 3.2 XSS Prevention

| Layer | Mechanism |
|---|---|
| Response headers | `Content-Security-Policy: default-src 'none'` |
| Input | OWASP Java HTML Sanitizer on `remarks` field before persistence |
| Output | Jackson encodes `<`, `>`, `&` in JSON — no raw HTML in responses |
| Error messages | User-controlled strings are HTML-encoded via `HtmlUtils.htmlEscape()` |

### 3.3 Header Injection Prevention

- `X-AD-Username` and `X-AD-Email` are validated against allowlist regex patterns before use.
- CRLF sequences (`\r`, `\n`) in header values result in a 400 Bad Request.
- Null bytes (`\0`) in header values result in a 400 Bad Request.
- Identity headers are never echoed back in responses.

### 3.4 Request Size Limits

The maximum HTTP request body size is configured to 64 KB. CSR payloads are expected to be well under 16 KB. The `csr.max.size.bytes` configuration key enforces a tighter limit on the CSR field specifically. Jakarta Bean Validation constraints enforce field-level maximum lengths on all string inputs.

### 3.5 Rate Limiting (Phase 2)

- Per `X-AD-Username`: maximum 10 CSR submissions per 60 minutes.
- Per `sourceIp`: maximum 50 requests per 60 minutes.
- Implementation: Bucket4j with in-memory or Redis backend.

---

## 4. Request / Response Flow (End to End)

The following describes the complete journey of a CSR submission from the browser to the database:

1. The AD applicant's browser or client application sends a `POST /api/ra/ad/csr/submit` request with the AD identity headers (`X-AD-Username`, `X-AD-Email`), the CSRF header (`X-XSRF-TOKEN`) and cookie (`XSRF-TOKEN`), and the JSON request body containing the PEM-encoded CSR.
2. The request arrives at the AD reverse proxy, which strips any pre-existing identity headers from the client, injects the authenticated `X-AD-*` headers, and forwards the request to the RA service on the internal network.
3. The Spring Security `CsrfFilter` runs first, validates the CSRF token, and rejects the request with 403 if the token is invalid.
4. The `AdIdentityFilter` runs next: validates the proxy IP against the whitelist, extracts and sanitises the AD identity headers, constructs the `AdIdentity` record, stores it as a request attribute, and sets the `SecurityContextHolder` authentication.
5. Spring Security adds all configured response headers (XSS, CSP, HSTS, etc.) to the outgoing response.
6. The request reaches `CsrSubmitController.submit()`, which retrieves the `AdIdentity` from the request attribute, reads the `@Valid CsrSubmitRequest` body, and delegates to `CsrSubmissionService.submit()`.
7. Jakarta Bean Validation fires on the request body, checking `pkcs10_pem` is not blank and the profile is valid. Failures return 400 before the service layer is entered.
8. `CsrValidationService.validate()` runs the full nine-stage pipeline: header format check, PEM size check, PEM format check, PKCS#10 ASN.1 parse, self-signature verification, algorithm policy check, key size check, CN-vs-AD-identity match. Any stage failure throws the appropriate exception.
9. `CsrSubmissionService.submit()` computes the CSR hash, checks for duplicates, builds and saves the `CsrSubmissionRecord`, writes the success audit log entry, and returns the `CsrSubmitResponse`.
10. The controller returns `HTTP 202 Accepted` with the response body containing the `submission_id`, status `PENDING`, submitter name, and timestamp.

---

## 5. Configuration Properties Reference

All runtime-tunable parameters for the service are stored in the `ra_config` database table and served from the `RaConfigCacheService` in-memory cache. The full list of configuration keys, their default values, and their groups is documented in Section 6 (RA Configuration Table). Key categories include:

| Group | What it controls |
|---|---|
| `CSR` | Max CSR size, allowed algorithms, allowed key sizes and curves, CN length limit, validity limit, duplicate window |
| `SECURITY` | Trusted proxy IP whitelist, rate limit parameters, header max length, CORS origins, CSRF cookie secure flag |
| `AD` | HTTP header names for username, display name, email, groups; AD group requirements |
| `AUDIT` | Log retention days, request log enable flag, sensitive field masking list |
| `SYSTEM` | Maintenance mode, submission timeout, max concurrent submissions |
