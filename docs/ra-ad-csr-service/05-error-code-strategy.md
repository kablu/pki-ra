# Error Code and Message Strategy

## 1. Overview

A centralised **Error Code Catalog** is maintained in the database. Every possible application error has a unique code and a human-readable message stored in this table. When the application starts, the entire catalog is loaded into an in-memory cache. At runtime, all error responses are built by looking up the cache — no hard-coded strings anywhere in the codebase.

**Startup sequence for cache initialisation:**

1. The application starts and Spring Boot completes context initialisation.
2. `ApplicationReadyEvent` is fired by Spring Boot once the context is fully ready.
3. `ErrorCodeCacheService.initialize()` is triggered by this event.
4. The method calls `ErrorCodeRepository.findAllByIsActiveTrue()`, which reads all active rows from the `error_code_catalog` table in a single database query.
5. Each `ErrorCodeEntry` is placed into a `ConcurrentHashMap` keyed by the `error_code` string (e.g. `"RA-CSR-001"`).
6. The service logs the number of entries loaded and marks itself as initialised.

**Runtime error lookup:**

1. An exception is thrown somewhere in the application.
2. `GlobalExceptionHandler` intercepts it and calls `ErrorCodeCacheService.buildErrorResponse(errorCode)`.
3. The cache service looks up the code in the `ConcurrentHashMap` — this is a pure in-memory operation with no database call.
4. It builds and returns an `ErrorResponse` DTO with the code, message, HTTP status, category, and timestamp.
5. The handler returns this response to the caller.

---

## 2. Error Code Table — `error_code_catalog`

| Column | Type | Description |
|---|---|---|
| `id` | BIGINT PK | Auto-increment |
| `error_code` | VARCHAR(20) UNIQUE | Unique code e.g. `RA-CSR-001` |
| `error_message` | VARCHAR(500) | Human-readable message template |
| `error_category` | VARCHAR(30) | CSR_VALIDATION, HEADER_VALIDATION, IDENTITY, SECURITY, SYSTEM |
| `http_status` | INTEGER | 400 / 401 / 403 / 409 / 422 / 500 |
| `severity` | VARCHAR(10) | ERROR / WARNING / INFO |
| `is_active` | BOOLEAN | Soft-disable without deleting |
| `created_at` | TIMESTAMP | From BaseAuditEntity |
| `created_by` | VARCHAR(100) | From BaseAuditEntity |
| `updated_at` | TIMESTAMP | From BaseAuditEntity |
| `updated_by` | VARCHAR(100) | From BaseAuditEntity |

---

## 3. Error Code Catalog — Seed Data

### Category: CSR Validation (`RA-CSR-*`)

| Error Code | HTTP | Message | Severity |
|---|---|---|---|
| `RA-CSR-001` | 400 | PKCS#10 CSR is not a valid PEM or base64-DER encoded request | ERROR |
| `RA-CSR-002` | 400 | CSR self-signature verification failed — CSR may have been tampered | ERROR |
| `RA-CSR-003` | 400 | RSA key size must be at least 2048 bits | ERROR |
| `RA-CSR-004` | 400 | EC curve not supported — allowed curves are P-256, P-384, P-521 | ERROR |
| `RA-CSR-005` | 400 | Unsupported key algorithm — only RSA and ECDSA are accepted | ERROR |
| `RA-CSR-006` | 400 | CSR Subject must contain a non-blank Common Name (CN) | ERROR |
| `RA-CSR-007` | 400 | CSR exceeds maximum allowed size of 16 KB | ERROR |
| `RA-CSR-008` | 400 | Certificate profile is not valid — allowed profiles: DSC, DOCUMENT_SIGNING, CODE_SIGNING, TLS_CLIENT, TLS_SERVER, EMAIL_ENCRYPT | ERROR |
| `RA-CSR-009` | 400 | pkcs10_pem field must not be blank | ERROR |

### Category: Header Validation (`RA-HDR-*`)

| Error Code | HTTP | Message | Severity |
|---|---|---|---|
| `RA-HDR-001` | 401 | AD identity header is missing — X-AD-Username or X-AD-Email must be provided by the gateway | ERROR |
| `RA-HDR-002` | 400 | Username format is invalid — only alphanumeric, dot, underscore, hyphen allowed (max 64 chars) | ERROR |
| `RA-HDR-003` | 400 | Email format is invalid — must be a valid RFC 5321 email address (max 254 chars) | ERROR |
| `RA-HDR-004` | 400 | Identity header contains illegal characters (CRLF or null byte) | ERROR |
| `RA-HDR-005` | 400 | Username exceeds maximum allowed length of 64 characters | ERROR |
| `RA-HDR-006` | 400 | Email exceeds maximum allowed length of 254 characters | ERROR |

### Category: Identity Mismatch (`RA-IDN-*`)

| Error Code | HTTP | Message | Severity |
|---|---|---|---|
| `RA-IDN-001` | 422 | CSR Subject CN does not match the authenticated AD username or email | ERROR |
| `RA-IDN-002` | 422 | CSR Subject Alternative Name does not contain the authenticated email address | ERROR |

### Category: Security (`RA-SEC-*`)

| Error Code | HTTP | Message | Severity |
|---|---|---|---|
| `RA-SEC-001` | 403 | Request did not originate from a trusted AD proxy — access denied | ERROR |
| `RA-SEC-002` | 403 | CSRF token is missing or invalid | ERROR |
| `RA-SEC-003` | 429 | Too many CSR submissions — rate limit exceeded, please try again later | WARNING |

### Category: Duplicate (`RA-DUP-*`)

| Error Code | HTTP | Message | Severity |
|---|---|---|---|
| `RA-DUP-001` | 409 | This CSR has already been submitted — duplicate submissions are not allowed | WARNING |

### Category: System (`RA-SYS-*`)

| Error Code | HTTP | Message | Severity |
|---|---|---|---|
| `RA-SYS-001` | 500 | An unexpected internal error occurred — please contact the RA administrator | ERROR |
| `RA-SYS-002` | 500 | Database operation failed — please retry or contact support | ERROR |
| `RA-SYS-003` | 503 | Error code catalog is not yet initialised — service is starting up | ERROR |
| `RA-SYS-004` | 400 | Request body is missing or malformed — ensure Content-Type is application/json | ERROR |

---

## 4. Component Design

### 4.1 `ErrorCodeEntry` — JPA Entity

**Package:** `com.pki.ra.adcsr.error`
**Table:** `error_code_catalog`
**Extends:** `BaseAuditEntity` (from `:common`)

The entity maps to the `error_code_catalog` table and inherits the four audit columns from `BaseAuditEntity`. Its own fields are:

| Field | Column | Type | Description |
|---|---|---|---|
| `id` | `id` | Long | Auto-increment primary key |
| `errorCode` | `error_code` | String (20) | Unique code string, e.g. `RA-CSR-001` |
| `errorMessage` | `error_message` | String (500) | User-facing message text |
| `errorCategory` | `error_category` | String (30) | Category for grouping: CSR_VALIDATION, HEADER_VALIDATION, IDENTITY, SECURITY, DUPLICATE, SYSTEM |
| `httpStatus` | `http_status` | int | HTTP status code to return |
| `severity` | `severity` | String (10) | ERROR, WARNING, or INFO |
| `isActive` | `is_active` | boolean | Defaults to true; false excludes the entry from cache |

An index on `error_code` (unique) and a non-unique index on `error_category` are declared for efficient lookup and grouping.

---

### 4.2 `ErrorCodeRepository` — JPA Repository

**Package:** `com.pki.ra.adcsr.error`
**Extends:** `JpaRepository<ErrorCodeEntry, Long>`

| Method | Return Type | Description |
|---|---|---|
| `findAllByIsActiveTrue()` | List of ErrorCodeEntry | Loads all active codes — called at startup |
| `findByErrorCode(errorCode)` | Optional of ErrorCodeEntry | Direct lookup by code — used as fallback only |
| `findByErrorCategory(category)` | List of ErrorCodeEntry | Lookup all codes in a category — admin use |

At runtime, the repository is only called during cache load (startup) and cache refresh (admin). All request-time lookups go through the cache.

---

### 4.3 `ErrorCodeCacheService` — Cache Manager

**Package:** `com.pki.ra.adcsr.error`
**Annotation:** `@Service`

**Internal state:**
- A `ConcurrentHashMap<String, ErrorCodeEntry>` named `cache`, keyed by `errorCode` string.
- An `AtomicBoolean` named `initialized` that is set to true after the first successful load.

**Methods:**

| Method | Description |
|---|---|
| `initialize()` | Annotated with `@EventListener(ApplicationReadyEvent.class)`. Calls `refresh()` and logs the count. |
| `getError(errorCode)` | Checks the `initialized` flag; if false, throws a service-unavailable exception. Looks up the code in the cache. If not found, logs a warning and returns the fallback entry for `RA-SYS-001`. |
| `buildErrorResponse(errorCode)` | Calls `getError()` to get the entry, then builds and returns an `ErrorResponse` DTO with all fields populated. |
| `refresh()` | Clears the cache, calls `repository.findAllByIsActiveTrue()`, and repopulates the map. Returns the count of loaded entries. |
| `getCacheSize()` | Returns the current number of entries in the cache. |
| `isInitialized()` | Returns the value of the `initialized` flag. |

The `getError()` method never returns null — it always either returns a valid entry or the generic `RA-SYS-001` fallback. This ensures `buildErrorResponse()` always produces a valid response even if an unknown error code is requested.

---

### 4.4 `ErrorResponse` — Standard Error Response DTO

**Package:** `com.pki.ra.adcsr.dto`

Every error returned by the service uses this DTO structure, serialised as JSON. The fields are:

| Field | JSON Key | Type | Example |
|---|---|---|---|
| `errorCode` | `error_code` | String | `RA-CSR-002` |
| `message` | `message` | String | `CSR self-signature verification failed` |
| `category` | `category` | String | `CSR_VALIDATION` |
| `httpStatus` | `http_status` | int | `400` |
| `timestamp` | `timestamp` | String | `2026-05-09T08:30:00Z` |
| `requestId` | `request_id` | String | Echo of MDC request ID for tracing; nullable |

The JSON serialisation order is fixed: `error_code`, `message`, `category`, `http_status`, `timestamp`, `request_id`. This consistent structure makes client-side error handling straightforward.

---

### 4.5 `GlobalExceptionHandler` — Updated with Cache Lookup

All exception handlers in `GlobalExceptionHandler` resolve their response from `ErrorCodeCacheService`. The pattern is: extract the error code string from the exception, call `cacheService.buildErrorResponse(errorCode)`, and return the response with the HTTP status from the `ErrorResponse` object.

| Exception Type | Error Code Resolution |
|---|---|
| `CsrValidationException` | Code is read from `ex.getErrorCode()` — set at throw site |
| `CsrIdentityMismatchException` | Hard-coded to `RA-IDN-001` |
| `DuplicateCsrException` | Hard-coded to `RA-DUP-001` |
| `UnauthorizedProxyException` | Hard-coded to `RA-SEC-001` |
| `MethodArgumentNotValidException` | Base code `RA-CSR-009`, field-level details appended to message |
| `HttpMessageNotReadableException` | Hard-coded to `RA-SYS-004` |
| `Exception` (fallback) | Hard-coded to `RA-SYS-001` |

**Key benefit:** Changing an error message requires only a database update and a cache refresh call to the admin endpoint. Zero code change, zero redeployment.

---

### 4.6 Exception Classes — Error Code Constants

Each exception class defines its possible error codes as public static String constants. The constants hold the `RA-*-*` code strings that are passed to the cache service. For example, `CsrValidationException` defines constants for each CSR error scenario — invalid PEM, bad signature, weak RSA key, unsupported curve, unsupported algorithm, missing CN, CSR too large, invalid profile, and blank PEM — each mapped to the corresponding `RA-CSR-*` code.

When a validator throws an exception, it passes the appropriate constant as the constructor argument. The exception stores this code as a field. The `GlobalExceptionHandler` then reads it via `getErrorCode()` and uses it to look up the user-facing message from the cache.

This design keeps the throw site clean — the developer writes just the constant name, not the full message string. The message lives in the database, not in the code.

---

## 5. Startup Sequence

The error code cache is loaded as part of the application startup sequence. The steps are:

1. Spring Boot starts and the application context is fully initialised.
2. The database connection is established and Flyway runs any pending migrations. The `V2__seed_error_code_catalog.sql` migration creates the `error_code_catalog` table and inserts all 25 seed error code entries on first startup.
3. All Spring beans are fully initialised, including `ErrorCodeCacheService` and `ErrorCodeRepository`.
4. Spring Boot fires `ApplicationReadyEvent` — this signals that the application is ready to serve requests.
5. `ErrorCodeCacheService.initialize()` is triggered by the event. It calls `refresh()`, which queries all active error codes from the database in a single read and populates the `ConcurrentHashMap`.
6. The service logs a message confirming the number of entries loaded (expected: 25 for the Phase 1 seed data).
7. The `initialized` flag is set to true.
8. Tomcat begins accepting HTTP requests. All subsequent error lookups use the in-memory cache with zero database calls.

If the database is unreachable at step 5, the `initialize()` method throws an exception and the application fails to start. The service will not accept requests without a fully populated error code cache.

---

## 6. Admin Cache Refresh API

An admin controller exposes endpoints for managing the error code cache at runtime without restarting the service.

| Method | Path | Description | Response |
|---|---|---|---|
| GET | `/api/ra/ad/admin/error-cache/refresh` | Clears the cache and reloads all active error codes from DB | `{ "status": "refreshed", "count": 25 }` |
| GET | `/api/ra/ad/admin/error-cache/status` | Returns current cache state | `{ "initialized": true, "count": 25 }` |
| GET | `/api/ra/ad/admin/error-cache/list` | Returns full list of cached error entries for audit | Array of all error code entries |

**Security:** All `/admin/**` paths require the `ROLE_RA_ADMIN` authority, enforced by Spring Security method-level security. Only users whose AD groups include the admin group can call these endpoints.

**Typical use case:** An RA administrator updates an error message in the `error_code_catalog` table via a database management tool, then calls the `/refresh` endpoint. The updated message is immediately served to all new error responses without any code change or service restart.

---

## 7. Database Migration Script

The Flyway migration file `V2__seed_error_code_catalog.sql` performs two operations:

**Table creation:** Creates the `error_code_catalog` table with all required columns as defined in Section 2, including unique and category indexes. The `created_by` and `updated_by` columns default to `'system'` and `created_at` / `updated_at` default to `CURRENT_TIMESTAMP` for the seed rows.

**Seed data insertion:** Inserts all 25 error codes defined in Section 3 using individual `INSERT INTO` statements. Each row specifies the `error_code`, `error_message`, `error_category`, `http_status`, and `severity` values. The `is_active` field defaults to true and the audit fields use the column defaults. The seed data covers all six categories: CSR Validation (9 codes), Header Validation (6 codes), Identity (2 codes), Security (3 codes), Duplicate (1 code), and System (4 codes).
