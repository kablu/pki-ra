# RA AD CSR Service — Component Design Document

**Document type:** Phase 1 — Detailed Component Design
**Service:** `ra-ad-csr-service`
**Java:** 26 | **Spring Boot:** 4.x | **Spring Security:** 7.x

---

## 1. Module Structure

```
pki-ra/subprojects/ra-ad-csr-service/
├── build.gradle
└── src/
    ├── main/
    │   ├── java/com/pki/ra/adcsr/
    │   │   ├── AdCsrServiceApplication.java
    │   │   │
    │   │   ├── config/
    │   │   │   ├── SecurityConfig.java          ← Spring Security (CSRF, CORS, headers)
    │   │   │   ├── TrustedProxyConfig.java      ← Trusted AD proxy IP whitelist
    │   │   │   └── OpenApiConfig.java           ← Swagger / SpringDoc
    │   │   │
    │   │   ├── filter/
    │   │   │   └── AdIdentityFilter.java        ← Extract + validate AD headers
    │   │   │
    │   │   ├── controller/
    │   │   │   └── CsrSubmitController.java     ← POST /api/ra/ad/csr/submit
    │   │   │
    │   │   ├── dto/
    │   │   │   ├── CsrSubmitRequest.java        ← Request body (pkcs10 PEM)
    │   │   │   ├── CsrSubmitResponse.java       ← Success response
    │   │   │   └── AdIdentity.java              ← Extracted header identity (record)
    │   │   │
    │   │   ├── validation/
    │   │   │   ├── HeaderValidator.java         ← Username/email format rules
    │   │   │   ├── Pkcs10CsrValidator.java      ← BC-based CSR cryptographic checks
    │   │   │   ├── CsrIdentityMatcher.java      ← CN/SAN vs AD identity
    │   │   │   └── annotation/
    │   │   │       ├── ValidCsrPem.java         ← Custom constraint annotation
    │   │   │       └── ValidCsrPemValidator.java
    │   │   │
    │   │   ├── service/
    │   │   │   ├── CsrValidationService.java    ← Orchestrates all validators
    │   │   │   └── CsrSubmissionService.java    ← Business logic, persistence, audit
    │   │   │
    │   │   ├── model/
    │   │   │   └── CsrSubmissionRecord.java     ← JPA entity (extends BaseAuditEntity)
    │   │   │
    │   │   ├── repository/
    │   │   │   └── CsrSubmissionRepository.java ← Spring Data JPA
    │   │   │
    │   │   └── exception/
    │   │       ├── CsrValidationException.java  ← 400 — invalid CSR or header
    │   │       ├── UnauthorizedProxyException.java ← 403 — untrusted proxy
    │   │       └── GlobalExceptionHandler.java  ← @RestControllerAdvice
    │   │
    │   └── resources/
    │       ├── application.yml
    │       ├── application-h2.yml
    │       └── db.properties               ← reused from :common pattern
    │
    └── test/
        └── java/com/pki/ra/adcsr/
            ├── controller/CsrSubmitControllerTest.java
            ├── validation/Pkcs10CsrValidatorTest.java
            ├── validation/CsrIdentityMatcherTest.java
            └── service/CsrSubmissionServiceTest.java
```

---

## 2. Component Descriptions

---

### 2.1 `AdCsrServiceApplication`

Entry point. Scans `com.pki.ra` to pick up `:common` beans.

```java
@SpringBootApplication(scanBasePackages = "com.pki.ra")
public class AdCsrServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AdCsrServiceApplication.class, args);
    }
}
```

**Key:** `scanBasePackages = "com.pki.ra"` ensures `:common` configuration classes
(`DataSourceConfig`, `DatabaseConfig`, `AuditorAwareImpl`, `AuditLogService`) are discovered.

---

### 2.2 `SecurityConfig`

Configures Spring Security 7 for the service.

**Responsibilities:**
- Disable default form login (stateless / header-based identity)
- Enable CSRF protection with `CookieCsrfTokenRepository` (double-submit cookie)
- Configure CORS — allow only the trusted AD proxy origin
- Set security response headers (XSS, CSP, HSTS, frame options, referrer policy)
- Permit only `POST /api/ra/ad/csr/submit` — all other paths 403
- Register `AdIdentityFilter` before `UsernamePasswordAuthenticationFilter`

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

Loads the allowlist of trusted AD proxy IP addresses from configuration.

```yaml
# application.yml
ra.ad.trusted-proxy-ips:
  - 10.0.1.10   # AD Gateway prod
  - 10.0.1.11   # AD Gateway standby
  - 127.0.0.1   # localhost (dev/test)
```

Exposes a `Set<String> trustedProxyIps()` bean consumed by `AdIdentityFilter`.

---

### 2.4 `AdIdentityFilter`

`OncePerRequestFilter` — first line of defense, runs before the controller.

**Flow:**
```
Request arrives
  │
  ├─ Extract client IP from X-Forwarded-For (outermost hop)
  ├─ Is IP in trustedProxyIps? ──No──▶ 403 UnauthorizedProxyException
  │
  ├─ Read X-AD-Username header
  ├─ Read X-AD-Email header
  ├─ At least one must be present ──No──▶ 401
  │
  ├─ Strip X-AD-* headers from response (never echo back)
  ├─ Construct AdIdentity record
  ├─ Store in request attribute: "AD_IDENTITY"
  │
  └─ chain.doFilter(request, response)
```

**Header names (configurable):**

| Property | Default |
|---|---|
| `ra.ad.header.username` | `X-AD-Username` |
| `ra.ad.header.email` | `X-AD-Email` |

---

### 2.5 `AdIdentity` (Record)

Immutable value object carrying the extracted and pre-validated AD identity.

```java
public record AdIdentity(
    String username,   // sAMAccountName — may be null if only email present
    String email,      // UPN / email — may be null if only username present
    String sourceIp    // originating client IP (from X-Forwarded-For)
) {
    public String displayName() {
        return username != null ? username : email;
    }
}
```

---

### 2.6 `CsrSubmitController`

Single endpoint: `POST /api/ra/ad/csr/submit`

**Request:**
- Header: `X-AD-Username` and/or `X-AD-Email` (set by proxy, validated by `AdIdentityFilter`)
- Header: `X-CSRF-Token` (required for browser clients)
- Body: `CsrSubmitRequest` (JSON — contains `pkcs10_pem`, optional `certificate_profile`, `validity_days`)

**Response:**
- 202 Accepted — CSR accepted, returns `submission_id` and `status: PENDING`
- 400 Bad Request — validation failed (CSR invalid, header malformed)
- 401 Unauthorized — no AD identity header
- 403 Forbidden — request not from trusted proxy
- 409 Conflict — duplicate CSR (same hash already submitted)
- 422 Unprocessable — CSR parsed but identity mismatch

**Design:** The controller does zero validation itself. All validation is delegated to `CsrValidationService`.

---

### 2.7 `CsrSubmitRequest` (DTO)

```
{
  "pkcs10_pem":          string  -- REQUIRED. PEM or base64-DER
  "certificate_profile": string  -- OPTIONAL. DSC | DOCUMENT_SIGNING | CODE_SIGNING
  "validity_days":       integer -- OPTIONAL. 1–3650
  "remarks":             string  -- OPTIONAL. Applicant note. Max 500 chars.
}
```

**Bean Validation constraints:**
- `pkcs10_pem` — `@NotBlank`, max 8192 chars, `@ValidCsrPem` (custom)
- `certificate_profile` — `@Pattern(regexp = "DSC|DOCUMENT_SIGNING|CODE_SIGNING|TLS_CLIENT|TLS_SERVER|EMAIL_ENCRYPT")`
- `validity_days` — `@Min(1)`, `@Max(3650)`
- `remarks` — `@Size(max = 500)`, HTML-sanitized before persistence

---

### 2.8 `HeaderValidator`

Stateless component validating the extracted `AdIdentity`.

**Checks performed:**
1. At least one of `username` or `email` is non-blank
2. `username` — matches `^[a-zA-Z0-9._-]{1,64}$` (sAMAccountName safe chars)
3. `email` — matches RFC 5321 pattern (Jakarta `@Email` equivalent regex)
4. No null bytes, CRLF sequences, or HTML tags in either field (header injection prevention)
5. Length limit: username ≤ 64 chars, email ≤ 254 chars

---

### 2.9 `Pkcs10CsrValidator`

Bouncy Castle-based cryptographic CSR validator.

**Checks performed (in order):**

| # | Check | Failure |
|---|---|---|
| 1 | Parse PEM / base64-DER | 400 — unparseable CSR |
| 2 | Verify CSR self-signature | 400 — signature invalid |
| 3 | Key algorithm: RSA or ECDSA only | 400 — unsupported algorithm |
| 4 | RSA key size ≥ 2048 bits | 400 — key too weak |
| 5 | ECDSA curve: P-256, P-384, or P-521 | 400 — unsupported curve |
| 6 | Subject CN is present and non-blank | 400 — missing CN |
| 7 | CSR size ≤ 16 KB raw bytes | 400 — CSR too large |
| 8 | Not expired (if notBefore extension present) | 400 — stale CSR |

**Result:** Returns parsed `PKCS10CertificationRequest` on success, throws `CsrValidationException` on any failure.

---

### 2.10 `CsrIdentityMatcher`

Cross-validates the CSR Subject DN / SAN against the AD identity.

**Matching logic:**

```
If X-AD-Email present:
  CSR Subject CN  ==  email            → MATCH
  CSR SAN rfc822  contains email       → MATCH
  CSR Subject CN  ==  username         → MATCH (email used as secondary)

If only X-AD-Username present:
  CSR Subject CN  ==  username         → MATCH

None match → 422 CsrIdentityMismatchException
```

**Case-insensitive comparison** for both CN and email fields.

---

### 2.11 `CsrValidationService`

Orchestrates the full validation pipeline.

```
validate(adIdentity, csrSubmitRequest):
  1. headerValidator.validate(adIdentity)
  2. pkcs10CsrValidator.validate(csrSubmitRequest.pkcs10Pem())
  3. csrIdentityMatcher.match(adIdentity, parsedCsr)
  4. Return CsrValidationResult (parsedCsr + adIdentity + profile)
```

Throws `CsrValidationException` (400) or `CsrIdentityMismatchException` (422) on failure.

---

### 2.12 `CsrSubmissionService`

Business logic layer — runs after successful validation.

**Flow:**
```
1. Compute SHA-256 hash of raw pkcs10 bytes
2. Check CsrSubmissionRepository.existsByPkcs10Hash(hash) → 409 if duplicate
3. Build CsrSubmissionRecord entity
4. repository.save(record)
5. auditLogService.logSuccess(username, "CSR_SUBMIT", submissionId, description, ip)
6. Return CsrSubmitResponse(submissionId, status=PENDING)
```

On any exception in step 4+:
```
auditLogService.logFailure(username, "CSR_SUBMIT", null, reason, ip)
throw PkiRaException(500)
```

`AuditLogService` runs in `REQUIRES_NEW` — audit record is committed even if main TX rolls back.

---

### 2.13 `CsrSubmissionRecord` (JPA Entity)

Extends `BaseAuditEntity` from `:common`.

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

Spring Data JPA repository.

```java
interface CsrSubmissionRepository extends JpaRepository<CsrSubmissionRecord, Long> {
    boolean existsByPkcs10Hash(String hash);
    Optional<CsrSubmissionRecord> findBySubmissionId(String submissionId);
    Page<CsrSubmissionRecord> findByAdUsername(String username, Pageable pageable);
    Page<CsrSubmissionRecord> findByStatus(String status, Pageable pageable);
}
```

---

### 2.15 `GlobalExceptionHandler`

`@RestControllerAdvice` — maps all exceptions to RFC 7807 Problem Detail responses.

| Exception | HTTP Status | Title |
|---|---|---|
| `CsrValidationException` | 400 | CSR Validation Failed |
| `CsrIdentityMismatchException` | 422 | Identity Mismatch |
| `UnauthorizedProxyException` | 403 | Forbidden |
| `DuplicateCsrException` | 409 | Duplicate Submission |
| `MethodArgumentNotValidException` | 400 | Validation Failed |
| `PkiRaException` | (from exception) | Operation Failed |
| `Exception` (fallback) | 500 | Internal Server Error |

**XSS Prevention in error responses:** All user-controlled data in error messages is HTML-encoded before inclusion in the Problem Detail body.

---

### 2.16 `ValidCsrPem` (Custom Constraint)

```java
@Constraint(validatedBy = ValidCsrPemValidator.class)
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidCsrPem {
    String message() default "pkcs10_pem must be a valid PEM or base64 DER encoded PKCS#10 CSR";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
```

`ValidCsrPemValidator` performs a fast syntactic check (PEM header present OR valid base64) before the full Bouncy Castle parse in `Pkcs10CsrValidator`. This separates syntactic from cryptographic validation.

---

## 3. Security Design

### 3.1 CSRF Protection

```
Strategy: Double-Submit Cookie (stateless, works with REST + browsers)

1. Client sends POST /api/ra/ad/csr/submit
2. Spring Security checks:
   a. Cookie XSRF-TOKEN is present
   b. Header X-XSRF-TOKEN matches the cookie value
3. Mismatch → 403 Forbidden
4. For API clients (non-browser): CSRF can be disabled per-IP via config flag
```

### 3.2 XSS Prevention

| Layer | Mechanism |
|---|---|
| Response headers | `Content-Security-Policy: default-src 'none'` |
| Input | OWASP Java HTML Sanitizer on `remarks` field before persistence |
| Output | Jackson encodes `<`, `>`, `&` in JSON — no raw HTML in responses |
| Error messages | User-controlled strings are HTML-encoded via `HtmlUtils.htmlEscape()` |

### 3.3 Header Injection Prevention

- `X-AD-Username` and `X-AD-Email` are validated against allowlist regex before use
- CRLF sequences (`\r`, `\n`) in header values → 400 Bad Request
- Null bytes (`\0`) in header values → 400 Bad Request
- Identity headers are **never echoed** in responses

### 3.4 Request Size Limits

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 16KB
      max-request-size: 32KB
server:
  tomcat:
    max-http-form-post-size: 32KB
```

### 3.5 Rate Limiting (Phase 2)

- Per `X-AD-Username`: max 10 CSR submissions per 60 minutes
- Per `sourceIp`: max 50 requests per 60 minutes
- Implementation: Bucket4j with in-memory or Redis backend

---

## 4. Request / Response Flow (End to End)

```
AD Applicant Browser
  │
  │  POST /api/ra/ad/csr/submit
  │  Headers: X-AD-Username, X-AD-Email, X-XSRF-TOKEN
  │  Cookie:  XSRF-TOKEN
  │  Body:    { "pkcs10_pem": "-----BEGIN CERTIFICATE REQUEST-----..." }
  │
  ▼
AD Reverse Proxy (strips existing identity headers, injects X-AD-*)
  │
  ▼
Spring Security Filter Chain
  ├── CsrfFilter           — validates XSRF token
  ├── AdIdentityFilter     — IP check → extract headers → store AdIdentity
  └── SecurityHeaders      — add XSS/CSP/HSTS headers to response
  │
  ▼
CsrSubmitController.submit(request, adIdentity)
  │
  ▼
  @Valid CsrSubmitRequest  — Bean Validation (NotBlank, ValidCsrPem, Size)
  │
  ▼
CsrValidationService.validate(adIdentity, request)
  ├── HeaderValidator.validate(adIdentity)
  ├── Pkcs10CsrValidator.validate(pkcs10Pem)    ← Bouncy Castle
  └── CsrIdentityMatcher.match(adIdentity, csr)
  │
  ▼
CsrSubmissionService.submit(validationResult)
  ├── Hash check (duplicate prevention)
  ├── repository.save(CsrSubmissionRecord)
  └── auditLogService.logSuccess(...)
  │
  ▼
Response 202 Accepted
  { "submission_id": "uuid", "status": "PENDING", "message": "CSR accepted" }
```

---

## 5. Configuration Properties Reference

```yaml
# application.yml

server:
  port: 8083
  servlet:
    context-path: /

spring:
  application:
    name: ra-ad-csr-service
  profiles:
    active: h2   # override with 'prod' for MariaDB

ra:
  ad:
    header:
      username: X-AD-Username
      email: X-AD-Email
    trusted-proxy-ips:
      - 10.0.1.10
      - 127.0.0.1
    validation:
      min-rsa-key-bits: 2048
      allowed-ec-curves: [P-256, P-384, P-521]
      max-csr-size-bytes: 16384
      allow-duplicate-csr: false
  cors:
    allowed-origins:
      - https://ra.pki.internal
```
