# RA AD CSR Service — API & Development Specification

**Document type:** Controller · Service · Repository Specification
**Audience:** Developers starting implementation
**Service:** `ra-ad-csr-service`

---

## 1. Gradle Module Setup

The `build.gradle` file for `ra-ad-csr-service` declares the following dependency groups:

**Module dependency:**
The `:common` module is declared as an `implementation` dependency. This single declaration brings in all shared infrastructure beans — `DataSourceConfig`, `DatabaseConfig`, `AuditorAwareImpl`, `AuditLogService`, `BaseAuditEntity`, and all common exception classes.

**Spring Boot starters:**
The module includes the `spring-boot-starter-web` (Tomcat + Spring MVC), `spring-boot-starter-security` (Spring Security 7), `spring-boot-starter-data-jpa` (Hibernate 7 + Spring Data), `spring-boot-starter-validation` (Jakarta Bean Validation 3.1), and `spring-boot-starter-actuator` (health endpoint) starters.

**Cryptography:**
Bouncy Castle is included via two JARs — `bcprov-jdk18on` (the JCE provider) and `bcpkix-jdk18on` (PKCS and CMS support). Both are referenced via the version catalog to stay in sync with the `ca-pki` module.

**Input sanitisation:**
The OWASP Java HTML Sanitizer library is included for stripping HTML from the `remarks` request field before persistence.

**API documentation:**
SpringDoc OpenAPI is included for automatic Swagger UI generation from the controller annotations.

**Build tools:**
Lombok is declared as a `compileOnly` and `annotationProcessor` dependency to generate boilerplate. Java toolchain is set to version 26.

**Runtime drivers:**
H2 and the MariaDB JDBC driver are declared as `runtimeOnly` — both are on the classpath but the correct one is activated by the active Spring profile.

**Test dependencies:**
`spring-boot-starter-test` (JUnit 5, MockMvc, AssertJ) and `spring-security-test` (security test utilities) are included in the test scope.

---

## 2. Application Entry Point

### `AdCsrServiceApplication.java`

**Package:** `com.pki.ra.adcsr`

The application entry point is a standard Spring Boot main class annotated with `@SpringBootApplication`. The critical configuration is the `scanBasePackages` attribute set to `"com.pki.ra"`. This causes Spring to scan both the `com.pki.ra.common.*` package tree and the `com.pki.ra.adcsr.*` package tree during component scan, picking up all `:common` beans without any re-declaration in this module.

Without this setting, `:common` beans would not be discovered because they reside in a different module and package root.

---

## 3. Controller Specification

### `CsrSubmitController.java`

**Package:** `com.pki.ra.adcsr.controller`
**Mapping:** `POST /api/ra/ad/csr/submit`
**Annotations:** `@RestController`, `@RequestMapping("/api/ra/ad/csr")`, `@RequiredArgsConstructor`, `@Slf4j`, and the OpenAPI `@Tag(name = "AD CSR Submission")` annotation.

#### Method: `submit`

| Attribute | Value |
|---|---|
| HTTP Method | `POST` |
| Path | `/submit` |
| Consumes | `application/json` |
| Produces | `application/json` |
| Response success | `202 Accepted` |

**Parameters:**

| Parameter | Source | Type | Required | Description |
|---|---|---|---|---|
| `adIdentity` | Request attribute `"AD_IDENTITY"` | `AdIdentity` | Yes | Set by `AdIdentityFilter` |
| `request` | Request body | `@Valid CsrSubmitRequest` | Yes | Contains `pkcs10_pem` |
| `httpRequest` | `HttpServletRequest` | — | Yes | For source IP extraction |

**Method logic (controller only — no business logic):**

1. Log the incoming request at INFO level, including the AD display name and source IP from the `AdIdentity` object.
2. Delegate entirely to `csrSubmissionService.submit(adIdentity, request, sourceIp)`.
3. Return `ResponseEntity.accepted().body(response)` with the `CsrSubmitResponse`.

**Swagger response documentation:**
- `202` — CSR accepted, `CsrSubmitResponse` body
- `400` — Validation failed (header or CSR invalid)
- `401` — Missing AD identity header
- `403` — Request not from trusted proxy
- `409` — Duplicate CSR already submitted
- `422` — CSR identity does not match AD user

---

## 4. DTO Specifications

### `CsrSubmitRequest.java`

**Package:** `com.pki.ra.adcsr.dto`
**Description:** The inbound JSON request body for a CSR submission.

| Field | JSON Key | Type | Required | Constraints | Description |
|---|---|---|---|---|---|
| `pkcs10Pem` | `pkcs10_pem` | String | Yes | Not blank, max 8192 chars, `@ValidCsrPem` | PEM-encoded PKCS#10 CSR |
| `certificateProfile` | `certificate_profile` | String | No | Pattern: DSC, DOCUMENT_SIGNING, CODE_SIGNING, TLS_CLIENT, TLS_SERVER, EMAIL_ENCRYPT | Requested certificate profile; defaults to DSC |
| `validityDays` | `validity_days` | Integer | No | Min 1, Max 3650 | Requested validity in days; defaults to 365 |
| `remarks` | `remarks` | String | No | Max 500 chars, HTML-sanitised | Optional applicant note |

---

### `CsrSubmitResponse.java`

**Package:** `com.pki.ra.adcsr.dto`
**Description:** The outbound JSON response returned on a successful CSR submission.

| Field | JSON Key | Type | Description |
|---|---|---|---|
| `submissionId` | `submission_id` | String | UUID identifying this submission — use for status queries |
| `status` | `status` | String | Always `PENDING` at submission time |
| `submittedBy` | `submitted_by` | String | AD username or email of the submitting user |
| `submittedAt` | `submitted_at` | String | ISO-8601 timestamp of submission |
| `message` | `message` | String | Human-readable confirmation message |

---

### `AdIdentity.java` (Record)

**Package:** `com.pki.ra.adcsr.dto`
**Description:** An immutable Java record carrying the extracted AD identity. Built by `AdIdentityFilter` and stored as a request attribute.

| Field | Type | Nullable | Description |
|---|---|---|---|
| `username` | String | Yes | AD sAMAccountName — null if only email header is present |
| `email` | String | Yes | UPN or email — null if only username header is present |
| `sourceIp` | String | No | Client IP from `X-Forwarded-For` or `RemoteAddr` |

The record provides two helper methods:
- `displayName()` — returns `username` if non-null, otherwise returns `email`. Used as the canonical identity string throughout the service.
- `hasUsername()` — returns true if `username` is non-null and non-blank.
- `hasEmail()` — returns true if `email` is non-null and non-blank.

---

## 5. Filter Specification

### `AdIdentityFilter.java`

**Package:** `com.pki.ra.adcsr.filter`
**Extends:** `OncePerRequestFilter`

**Fields:**
- `usernameHeader` (String) — loaded from config key `ad.header.username`, default `X-AD-Username`
- `emailHeader` (String) — loaded from config key `ad.header.email`, default `X-AD-Email`
- `trustedIps` (Set of String) — loaded from config key `security.proxy.whitelist.ips`

**Processing steps inside `doFilterInternal`:**

**Step 1 — Proxy IP validation:**
The client IP is resolved from the `X-Forwarded-For` header (taking the first/outermost IP) or from `RemoteAddr` if the header is absent. The resolved IP is checked against the trusted proxy set. If it is not present, `UnauthorizedProxyException` is thrown immediately and the request is rejected with HTTP 403.

**Step 2 — Header extraction:**
The `X-AD-Username` and `X-AD-Email` header values are read from the request and stripped of leading and trailing whitespace.

**Step 3 — Presence check:**
If both `username` and `email` are blank after trimming, the filter sends a 401 response directly and returns without calling the next filter in the chain.

**Step 4 — CRLF and null-byte injection check:**
If either value contains carriage return (`\r`), line feed (`\n`), or null byte (`\0`) characters, a `CsrValidationException` is thrown and the request is rejected with HTTP 400.

**Step 5 — Identity construction and delegation:**
An `AdIdentity` record is constructed from the sanitised values and stored as the request attribute `"AD_IDENTITY"`. A `UsernamePasswordAuthenticationToken` is set in the Spring `SecurityContextHolder` using the display name so that `AuditorAwareImpl` can populate audit columns. The MDC context is populated with `requestId`, `username`, and `sourceIp`. Finally, `chain.doFilter(request, response)` is called to pass control to the next filter. The MDC context is cleared after the filter chain completes.

---

## 6. Validation Specifications

### 6.1 `HeaderValidator.java`

**Package:** `com.pki.ra.adcsr.validation`

The validator applies the following rules using two regex patterns. The username pattern accepts only alphanumeric characters, dots, underscores, and hyphens, with a maximum length of 64 characters. The email pattern follows RFC 5321 structure with a maximum length of 254 characters.

**Validation rules applied in sequence:**

1. If `username` is present: match against the username pattern. If it fails, throw `CsrValidationException` with message "Invalid username format in X-AD-Username header".
2. If `username` is present: verify length does not exceed 64 characters. If it does, throw `CsrValidationException` with message "Username exceeds 64 characters".
3. If `email` is present: match against the email pattern. If it fails, throw `CsrValidationException` with message "Invalid email format in X-AD-Email header".
4. If `email` is present: verify length does not exceed 254 characters. If it does, throw `CsrValidationException` with message "Email exceeds 254 characters".

---

### 6.2 `Pkcs10CsrValidator.java`

**Package:** `com.pki.ra.adcsr.validation`
**Dependency:** Bouncy Castle `PKCS10CertificationRequest`

Validates and parses the PEM-encoded CSR in the following ordered steps:

**Step 1 — Size check:**
The PEM string is converted to bytes using UTF-8 encoding. If the byte count exceeds the `csr.max.size.bytes` configuration value (default 16 384 bytes), throw `CsrValidationException` with error code `RA-CSR-007`.

**Step 2 — Parse:**
If the input begins with a PEM header, it is parsed using Bouncy Castle's `PEMParser`. If the input appears to be raw Base64, it is decoded to DER bytes and fed to the `PKCS10CertificationRequest` constructor. If parsing fails for any reason, throw `CsrValidationException` with error code `RA-CSR-001`.

**Step 3 — Signature verification:**
A `ContentVerifierProvider` is built from the CSR's embedded `SubjectPublicKeyInfo`. The CSR's self-signature is verified using this provider. If verification fails, throw `CsrValidationException` with error code `RA-CSR-002`.

**Step 4 — Algorithm and key strength check:**
The public key algorithm identifier is extracted from the `SubjectPublicKeyInfo`. For RSA keys, the modulus bit length is computed and compared to the minimum from configuration. For EC keys, the named curve is extracted from the algorithm parameters and checked against the allowed curves list. Keys using unsupported algorithms are rejected with error code `RA-CSR-005`. RSA keys below the minimum size are rejected with error code `RA-CSR-003`. EC keys on disallowed curves are rejected with error code `RA-CSR-004`.

**Step 5 — Subject CN presence:**
The `X500Name` is extracted from the CSR subject. The `CN` RDN is located using `BCStyle.CN`. If no CN is present or it is blank, throw `CsrValidationException` with error code `RA-CSR-006`.

On success, the method returns the parsed `PKCS10CertificationRequest` object.

---

### 6.3 `CsrIdentityMatcher.java`

**Package:** `com.pki.ra.adcsr.validation`

Validates that the CSR Subject CN matches the authenticated AD user. Steps:

1. Extract the Common Name from the CSR subject using Bouncy Castle's `X500Name.getRDNs(BCStyle.CN)` and convert to lowercase.
2. Extract any `rfc822Name` entries from the Subject Alternative Name extension in the CSR's attribute set. Convert all to lowercase.
3. Convert the AD `username` and `email` (if present) to lowercase.
4. If AD `email` is present: check whether the CSR CN equals the email, or whether the SAN list contains the email. Either constitutes a match.
5. If AD `username` is present: check whether the CSR CN equals the username. A match is accepted.
6. If no match was found through any path: throw `CsrIdentityMismatchException` with a message identifying both the CSR CN and the AD identity, using error code `RA-IDN-001`.

---

## 7. Service Specifications

### 7.1 `CsrValidationService.java`

**Package:** `com.pki.ra.adcsr.service`
**Annotation:** `@Service`
**Dependencies:** `HeaderValidator`, `Pkcs10CsrValidator`, `CsrIdentityMatcher`

This service orchestrates the full pre-submission validation pipeline. It accepts the `AdIdentity` and `CsrSubmitRequest`, calls the three validators in sequence, and returns a `CsrValidationResult` record containing the parsed CSR object, the identity, the requested certificate profile, and the requested validity days. Any exception thrown by the validators propagates without wrapping.

---

### 7.2 `CsrSubmissionService.java`

**Package:** `com.pki.ra.adcsr.service`
**Annotations:** `@Service`, `@Transactional`
**Dependencies:** `CsrValidationService`, `CsrSubmissionRepository`, `AuditLogService` (from `:common`), `RaConfigCacheService`

**Submission flow — step by step:**

**Step 1 — Validate:**
Calls `CsrValidationService.validate(identity, request)` to run the full validation pipeline. Any validation exception propagates immediately.

**Step 2 — Duplicate check:**
Computes `SHA-256` hex of the PEM bytes. Checks `repository.existsByPkcs10Hash(hash)`. If a duplicate exists within the configured time window, writes a failure audit log entry (`CSR_SUBMIT_DUPLICATE`, FAILURE) and throws `DuplicateCsrException`.

**Step 3 — Build entity:**
Generates a UUID for `submissionId`. Resolves the certificate profile from the request (defaults to `"DSC"` if absent). Sanitises the `remarks` field using OWASP HTML sanitizer. Builds the `CsrSubmissionRecord` by mapping all fields from the validation result and the sanitised remarks.

**Step 4 — Persist:**
Calls `repository.save(record)`. Spring Data JPA and the `BaseAuditEntity` mechanism automatically populate `created_at`, `created_by`, `updated_at`, and `updated_by`.

**Step 5 — Audit:**
Calls `auditLogService.logSuccess(identity.displayName(), "CSR_SUBMIT", submissionId, description, sourceIp)`. Because `AuditLogService` runs in `REQUIRES_NEW` propagation, this audit record is committed independently.

**Step 6 — Return:**
Builds and returns a `CsrSubmitResponse` with the submission ID, status `PENDING`, submitter display name, current ISO-8601 timestamp, and a confirmation message.

**Exception path:**
If any exception occurs in steps 4 through 6, the service calls `auditLogService.logFailure()` before re-throwing. The failure audit record is always committed even if the main transaction rolls back.

---

## 8. Repository Specification

### `CsrSubmissionRepository.java`

**Package:** `com.pki.ra.adcsr.repository`
**Extends:** `JpaRepository<CsrSubmissionRecord, Long>`

| Method | Return Type | Description |
|---|---|---|
| `existsByPkcs10Hash(String hash)` | `boolean` | Duplicate CSR check — called before save |
| `findBySubmissionId(String id)` | `Optional<CsrSubmissionRecord>` | Status query by caller |
| `findByAdUsername(String username, Pageable p)` | `Page<CsrSubmissionRecord>` | List by AD user |
| `findByStatus(String status, Pageable p)` | `Page<CsrSubmissionRecord>` | RA operator queue |
| `findByAdUsernameAndStatus(String username, String status, Pageable p)` | `Page<CsrSubmissionRecord>` | Filtered view |
| `countByAdUsernameAndCreatedAtAfter(String username, Instant since)` | `long` | Rate limit check |

---

## 9. Exception Specifications

### Exception Hierarchy

All application exceptions extend `PkiRaException` from `:common`, which itself extends `RuntimeException` and carries an `HttpStatus` field. The local exceptions and their HTTP status codes are:

- `CsrValidationException` — HTTP 400 Bad Request. Thrown by any of the nine validation stages.
- `CsrIdentityMismatchException` — HTTP 422 Unprocessable Entity. Thrown when the CSR CN does not match the AD identity.
- `DuplicateCsrException` — HTTP 409 Conflict. Thrown when the CSR hash matches an existing submission within the duplicate window.
- `UnauthorizedProxyException` — HTTP 403 Forbidden. Thrown when the request originates from an IP not in the trusted proxy whitelist.

Each exception class carries a static constant for each error code it can represent. For example, `CsrValidationException` has constants such as `INVALID_PEM`, `BAD_SIGNATURE`, `WEAK_RSA_KEY`, `UNSUPPORTED_CURVE`, `UNSUPPORTED_ALGO`, `MISSING_CN`, `CSR_TOO_LARGE`, `INVALID_PROFILE`, and `BLANK_PEM`, each mapping to the corresponding `RA-CSR-*` error code string. These constants are used as constructor arguments so that the error code is always set at the throw site and the `GlobalExceptionHandler` can look it up in the `ErrorCodeCacheService`.

---

### `GlobalExceptionHandler.java`

**Package:** `com.pki.ra.adcsr.exception`
**Annotation:** `@RestControllerAdvice`

The handler maps every exception type to an `ErrorResponse` by calling `ErrorCodeCacheService.buildErrorResponse(errorCode)`. The error code is retrieved from the exception's `errorCode` field. The `ErrorResponse` is returned with the HTTP status code from the error catalog.

| Exception Type | Error Code Used | HTTP Status |
|---|---|---|
| `CsrValidationException` | From `ex.getErrorCode()` field | 400 |
| `CsrIdentityMismatchException` | `RA-IDN-001` | 422 |
| `DuplicateCsrException` | `RA-DUP-001` | 409 |
| `UnauthorizedProxyException` | `RA-SEC-001` | 403 |
| `MethodArgumentNotValidException` | `RA-CSR-009` + field-level details appended | 400 |
| `HttpMessageNotReadableException` | `RA-SYS-004` | 400 |
| `Exception` (fallback) | `RA-SYS-001` | 500 |

All user-controlled strings included in error responses (such as field names and submitted values) are passed through `HtmlUtils.htmlEscape()` before inclusion to prevent reflected XSS in error messages.

---

## 10. Security Config Specification

### `SecurityConfig.java`

**Package:** `com.pki.ra.adcsr.config`
**Annotations:** `@Configuration`, `@EnableWebSecurity`, `@EnableMethodSecurity`

The `SecurityFilterChain` bean is configured with the following settings:

**CSRF:** Uses `CookieCsrfTokenRepository` with the `HttpOnly` flag set to false so that JavaScript clients can read the cookie value. The token request handler is set to `CsrfTokenRequestAttributeHandler` for stateless REST API compatibility.

**CORS:** A `CorsConfigurationSource` bean reads the allowed origins from `RaConfigCacheService` (key `security.cors.allowed.origins`). Allowed methods are `POST` and `GET`. The `OPTIONS` preflight method is handled automatically.

**Security Headers:** The following headers are added to every response by the `HeadersConfigurer`: `Content-Security-Policy` set to `default-src 'none'; frame-ancestors 'none'`, `X-Frame-Options` set to `DENY`, `X-Content-Type-Options` set to `nosniff`, `Strict-Transport-Security` with max-age 31536000 and `includeSubDomains`, `Referrer-Policy` set to `no-referrer`, and `Permissions-Policy` set to `geolocation=(), microphone=(), camera=()`.

**Session Management:** Set to `STATELESS`. No HTTP session is created or used.

**Authorization Rules:**
- `POST /api/ra/ad/csr/submit` — permitted to all (authentication is handled by `AdIdentityFilter`, not Spring Security credentials).
- `GET /actuator/health` — permitted to all.
- `GET /swagger-ui/**` and `GET /v3/api-docs/**` — permitted to all in non-production profiles.
- All other paths — denied with 403.

**Custom Filter:** `AdIdentityFilter` is added to the filter chain before `UsernamePasswordAuthenticationFilter` so it runs on every request before any Spring Security authentication attempt.

---

## 11. Test Specification

### `CsrSubmitControllerTest.java`

| Test case | Expected |
|---|---|
| Valid request with username header | 202 Accepted |
| Valid request with email header | 202 Accepted |
| Missing both AD headers | 401 |
| Request from untrusted IP | 403 |
| Blank `pkcs10_pem` | 400 |
| Oversized CSR (> 8192 chars) | 400 |
| Invalid PEM format | 400 |
| CSR signature tampered | 400 |
| RSA 1024-bit key | 400 |
| CN does not match AD username | 422 |
| Duplicate CSR (same hash) | 409 |
| Missing CSRF token (browser flow) | 403 |

### `Pkcs10CsrValidatorTest.java`

| Test case | Expected |
|---|---|
| Valid RSA-2048 PEM CSR | Pass, returns parsed CSR |
| Valid ECDSA P-256 PEM CSR | Pass |
| Valid base64-DER CSR | Pass |
| CSR with tampered signature | `CsrValidationException` |
| RSA-1024 key | `CsrValidationException` |
| EC curve secp192r1 (not allowed) | `CsrValidationException` |
| No Subject CN | `CsrValidationException` |
| CSR body exceeds 16 KB | `CsrValidationException` |
| Completely random bytes | `CsrValidationException` |

### `CsrIdentityMatcherTest.java`

| Test case | Expected |
|---|---|
| CN = username, AD username present | Match |
| CN = email, AD email present | Match |
| SAN rfc822 = email, AD email present | Match |
| CN = random string, AD username present | `CsrIdentityMismatchException` |
| Case-insensitive CN match | Match |
