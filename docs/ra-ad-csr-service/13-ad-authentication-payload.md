# RA AD CSR Service — Payload Structure for AD Authentication

**Document type:** Payload and Interface Design
**Module:** `pki-ra/subprojects/ra-ad-csr-service`

---

## 1. Overview

The `ra-ad-csr-service` does not authenticate users itself. Authentication is delegated entirely to an upstream **Active Directory Reverse Proxy** (also referred to as the AD Gateway). The proxy authenticates the end user via Kerberos, NTLM, or LDAP, and then communicates the authenticated identity to the RA service through **HTTP request headers**. The RA service trusts these headers only when the request arrives from a known, whitelisted proxy IP address.

The payload for AD authentication therefore consists of two parts:

- **Inbound authentication headers** — set by the AD proxy and read by `AdIdentityFilter`
- **Request body** — the JSON payload submitted by the applicant containing the CSR

The service never issues tokens, cookies, or session identifiers for authentication. Every request is independently validated by checking the proxy IP and the injected identity headers.

---

## 2. Authentication Flow Summary

| Step | Actor | Action |
|---|---|---|
| 1 | AD Applicant | Sends HTTP request to the AD reverse proxy (HTTPS) |
| 2 | AD Proxy | Authenticates the user via Kerberos / NTLM / LDAP |
| 3 | AD Proxy | Strips any pre-existing `X-AD-*` headers from the client request |
| 4 | AD Proxy | Injects authenticated identity headers from the AD directory |
| 5 | AD Proxy | Forwards the request to `ra-ad-csr-service` over the internal network |
| 6 | AdIdentityFilter | Validates the source IP against the trusted proxy whitelist |
| 7 | AdIdentityFilter | Extracts and validates the injected identity headers |
| 8 | AdIdentityFilter | Builds the `AdIdentity` object and passes it to the controller |
| 9 | CsrSubmissionService | Uses the AD identity for CSR CN matching and audit logging |

---

## 3. Inbound Request — Headers Injected by AD Proxy

The AD proxy injects the following headers on every forwarded request. All header names are configurable via `ra_config` (AD group). The defaults shown below are the Phase 1 standard.

### 3.1 Required Headers

These headers must be present. If either the username or email header is missing, the `AdIdentityFilter` rejects the request immediately with HTTP 401.

| Header Name | Config Key | Required | Description |
|---|---|---|---|
| `X-AD-Username` | `ad.header.username` | One of these two is required | The authenticated user's Active Directory `sAMAccountName` (login name). Example: `jsmith` |
| `X-AD-Email` | `ad.header.email` | One of these two is required | The authenticated user's UPN or email address. Example: `john.smith@company.com` |

**Rule:** At least one of `X-AD-Username` or `X-AD-Email` must be present and non-blank. Both may be present simultaneously — this is the recommended configuration.

### 3.2 Optional Headers

These headers enrich the identity context but are not required for the service to function. They are used for display purposes, group-based authorisation (Phase 2), and richer audit logging.

| Header Name | Config Key | Description |
|---|---|---|
| `X-AD-DisplayName` | `ad.header.display.name` | Full display name of the user from AD. Example: `John Smith` |
| `X-AD-Groups` | `ad.header.groups` | Comma-separated list of AD group memberships. Example: `RA-Users,PKI-Submitters,Domain-Users` |

### 3.3 Infrastructure Headers

These headers are set by the proxy infrastructure and used by the service for IP resolution and request tracing.

| Header Name | Set By | Description |
|---|---|---|
| `X-Forwarded-For` | Load balancer / proxy | Comma-separated chain of originating IPs. The service reads the first (leftmost) value as the client IP. |
| `X-Request-ID` | Proxy or client | Optional correlation ID. Echoed in the `request_id` field of error responses for end-to-end tracing. |

---

## 4. Inbound Request — Body Structure

The request body is a JSON object submitted to `POST /api/ra/ad/csr/submit`. It contains the CSR and submission metadata.

### 4.1 Request Body Fields

| Field | JSON Key | Type | Required | Max Length | Description |
|---|---|---|---|---|---|
| CSR PEM | `pkcs10_pem` | String | Yes | 8192 chars | PEM-encoded PKCS#10 Certificate Signing Request. Must begin with `-----BEGIN CERTIFICATE REQUEST-----` and end with `-----END CERTIFICATE REQUEST-----`. |
| Certificate Profile | `certificate_profile` | String | No | 30 chars | The type of certificate requested. If absent, defaults to `DSC`. |
| Validity Days | `validity_days` | Integer | No | — | Requested certificate validity in days. Minimum 1, maximum 3650 (10 years). Defaults to 365. |
| Remarks | `remarks` | String | No | 500 chars | Optional free-text note from the applicant. HTML is stripped before persistence. |

### 4.2 Allowed Values for `certificate_profile`

| Value | Description |
|---|---|
| `DSC` | Digital Signature Certificate — default |
| `DOCUMENT_SIGNING` | Document signing certificate |
| `CODE_SIGNING` | Software code signing certificate |
| `TLS_CLIENT` | TLS client authentication certificate |
| `TLS_SERVER` | TLS server certificate |
| `EMAIL_ENCRYPT` | S/MIME email encryption certificate |

### 4.3 Request Content-Type

All requests must carry the `Content-Type: application/json` header. Requests without this header or with a malformed JSON body are rejected with HTTP 400 and error code `RA-SYS-004`.

---

## 5. Complete Inbound Request Structure

A full CSR submission request from the AD proxy to the RA service has the following structure:

**HTTP Method:** POST
**URL:** `https://ra-service.internal/api/ra/ad/csr/submit`

**Required Headers:**

| Header | Example Value |
|---|---|
| `Content-Type` | `application/json` |
| `X-AD-Username` | `jsmith` |
| `X-AD-Email` | `john.smith@company.com` |
| `X-AD-DisplayName` | `John Smith` |
| `X-AD-Groups` | `RA-Users,PKI-Submitters` |
| `X-Forwarded-For` | `192.168.10.5` |
| `X-XSRF-TOKEN` | `a1b2c3d4-e5f6-7890-abcd-ef1234567890` (for browser clients) |
| `Cookie` | `XSRF-TOKEN=a1b2c3d4-e5f6-7890-abcd-ef1234567890` (for browser clients) |

**Request Body Fields:**

| Field | Example Value |
|---|---|
| `pkcs10_pem` | The full PEM-encoded CSR string starting with BEGIN CERTIFICATE REQUEST |
| `certificate_profile` | `DSC` |
| `validity_days` | `365` |
| `remarks` | `Annual certificate renewal for John Smith` |

---

## 6. Header Validation Rules

The `AdIdentityFilter` applies the following validation rules to every header value before building the `AdIdentity` object. Any rule failure immediately terminates the request with HTTP 400.

### 6.1 Username Validation Rules

| Rule | Detail | Error Code |
|---|---|---|
| Allowed characters | Only alphanumeric characters, dot (`.`), underscore (`_`), and hyphen (`-`) | `RA-HDR-002` |
| Maximum length | Must not exceed 64 characters | `RA-HDR-005` |
| No CRLF or null bytes | `\r`, `\n`, `\0` not permitted | `RA-HDR-004` |
| Not blank | Must contain at least one non-whitespace character | `RA-HDR-001` |

### 6.2 Email Validation Rules

| Rule | Detail | Error Code |
|---|---|---|
| Format | Must match the pattern `local-part@domain.tld` per RFC 5321 | `RA-HDR-003` |
| Maximum length | Must not exceed 254 characters | `RA-HDR-006` |
| No CRLF or null bytes | `\r`, `\n`, `\0` not permitted | `RA-HDR-004` |
| Not blank | Must contain at least one non-whitespace character | `RA-HDR-001` |

### 6.3 Display Name and Groups Validation

The `X-AD-DisplayName` and `X-AD-Groups` headers are not subjected to format regex checks but are validated for:
- Length: must not exceed the value of `security.header.max.length` (default 512 characters)
- Characters: CRLF and null bytes are stripped, not rejected, to avoid breaking the request for cosmetic data

### 6.4 Header Length Guard

All header values, regardless of type, are checked against `security.header.max.length` (default 512) before any other validation. This prevents excessively long values from reaching regex validation.

---

## 7. `AdIdentity` Object — Internal Representation

After successful header extraction and validation, `AdIdentityFilter` builds an `AdIdentity` value object and stores it as a request attribute. This object is the authoritative identity representation used by all downstream components.

| Field | Source Header | Nullable | Description |
|---|---|---|---|
| `username` | `X-AD-Username` | Yes | AD sAMAccountName. Null if only email header was present. |
| `email` | `X-AD-Email` | Yes | UPN or email address. Null if only username header was present. |
| `displayName` | `X-AD-DisplayName` | Yes | Full display name. Null if header absent. |
| `groups` | `X-AD-Groups` | Yes | List of AD group names. Empty list if header absent. |
| `sourceIp` | `X-Forwarded-For` / RemoteAddr | No | Originating client IP address. Always present. |

**Display name resolution priority:**
When a canonical single-string identity is needed (for audit logs, CSR CN matching), the service uses the following priority order:
1. `displayName` if present and non-blank
2. `username` if present and non-blank
3. `email` as the last fallback

This resolved value is called `displayName()` throughout the service.

---

## 8. CSR CN vs AD Identity Matching

One of the most critical payload validations is ensuring that the Common Name in the CSR matches the authenticated AD identity. This prevents a user from requesting a certificate in someone else's name.

### 8.1 Matching Rules

| CSR Content | AD Header Available | Match Condition |
|---|---|---|
| Subject CN | `X-AD-Username` | CN must equal sAMAccountName (case-insensitive) |
| Subject CN | `X-AD-DisplayName` | CN must equal display name (case-insensitive) |
| Subject CN | `X-AD-Email` | CN must equal email address (case-insensitive) |
| SAN rfc822Name | `X-AD-Email` | SAN email entry must equal AD email (case-insensitive) |

**If any one of the above matches is satisfied, the request is accepted.**

### 8.2 Examples of Valid Matches

| CSR Common Name | AD Username | AD Email | AD Display Name | Result |
|---|---|---|---|---|
| `jsmith` | `jsmith` | `john.smith@co.com` | `John Smith` | MATCH — CN equals username |
| `john.smith@co.com` | `jsmith` | `john.smith@co.com` | `John Smith` | MATCH — CN equals email |
| `John Smith` | `jsmith` | `john.smith@co.com` | `John Smith` | MATCH — CN equals display name |
| `JSMITH` | `jsmith` | — | — | MATCH — case-insensitive |
| `other.user` | `jsmith` | `john.smith@co.com` | `John Smith` | NO MATCH — error RA-IDN-001 |

### 8.3 Failure Behaviour

When no match is found, the service throws `CsrIdentityMismatchException` with error code `RA-IDN-001`. The audit log records this as `CSR_SUBMIT_IDENTITY_MISMATCH` with FAILURE outcome. The error response includes the error code and the user-facing message from the error catalog, but does **not** expose the actual CSR CN or AD identity values to prevent information leakage.

---

## 9. Outbound Response — Success Payload

On successful CSR submission, the service returns `HTTP 201 Created` with the following JSON response body.

| Field | JSON Key | Type | Description |
|---|---|---|---|
| Submission ID | `submission_id` | String (UUID) | Unique identifier for this submission. Use this ID for all status queries. |
| Status | `status` | String | Always `PENDING` at the time of initial submission. |
| Submitted By | `submitted_by` | String | The resolved AD display name of the submitting user. |
| Submitted At | `submitted_at` | String | ISO-8601 timestamp with UTC offset of when the submission was accepted. |
| Message | `message` | String | Human-readable confirmation: `"CSR accepted and pending RA review"` |

---

## 10. Outbound Response — Error Payload

All error responses follow a uniform structure regardless of which validation stage failed.

| Field | JSON Key | Type | Description |
|---|---|---|---|
| Error Code | `error_code` | String | Unique code from the error catalog, e.g. `RA-CSR-002` |
| Message | `message` | String | User-facing message from the error catalog |
| Category | `category` | String | Error category, e.g. `CSR_VALIDATION`, `IDENTITY`, `SECURITY` |
| HTTP Status | `http_status` | Integer | Numeric HTTP status code |
| Timestamp | `timestamp` | String | ISO-8601 timestamp of when the error occurred |
| Request ID | `request_id` | String | Echo of the `X-Request-ID` header for end-to-end tracing. Null if not provided. |

---

## 11. HTTP Status Code Reference

| HTTP Status | When Returned | Typical Error Codes |
|---|---|---|
| 201 Created | CSR accepted and persisted successfully | — |
| 400 Bad Request | CSR format invalid, header format invalid, request body malformed | RA-CSR-001 through RA-CSR-009, RA-HDR-002 through RA-HDR-006, RA-SYS-004 |
| 401 Unauthorized | AD identity headers missing entirely | RA-HDR-001 |
| 403 Forbidden | Request from untrusted proxy IP, or invalid CSRF token | RA-SEC-001, RA-SEC-002 |
| 409 Conflict | Duplicate CSR — same hash submitted within the configured window | RA-DUP-001 |
| 422 Unprocessable Entity | CSR is cryptographically valid but identity does not match AD user | RA-IDN-001, RA-IDN-002 |
| 429 Too Many Requests | Rate limit exceeded for this proxy IP | RA-SEC-003 |
| 500 Internal Server Error | Unexpected server-side error | RA-SYS-001, RA-SYS-002 |
| 503 Service Unavailable | Service is in maintenance mode or cache not yet initialised | RA-SYS-003 |

---

## 12. CSRF Header Requirement for Browser Clients

Browser-based clients must include a CSRF token on every state-changing request (POST). The service uses the double-submit cookie pattern:

| Step | Description |
|---|---|
| 1 | On the first response from the service, the browser receives a `Set-Cookie: XSRF-TOKEN=<value>` response cookie. |
| 2 | The browser stores this cookie value. |
| 3 | On every subsequent POST request, the browser reads the cookie value and includes it in the `X-XSRF-TOKEN` request header. |
| 4 | The service compares the cookie value against the header value. If they match, the request is allowed to proceed. If they differ or either is absent, HTTP 403 is returned with error code `RA-SEC-002`. |

Non-browser API clients (such as internal automation scripts that call the service directly via a trusted server-side channel) are exempt from CSRF enforcement on designated paths, as they are not susceptible to cross-site request forgery attacks.

---

## 13. Security Headers on All Responses

Every response from the service carries the following security headers, regardless of whether the response is a success or an error:

| Header | Value | Purpose |
|---|---|---|
| `Strict-Transport-Security` | `max-age=31536000; includeSubDomains` | Instructs browsers to use HTTPS only for 1 year |
| `X-Content-Type-Options` | `nosniff` | Prevents browsers from MIME-sniffing the response |
| `X-Frame-Options` | `DENY` | Prevents the response from being embedded in iframes |
| `Content-Security-Policy` | `default-src 'none'; frame-ancestors 'none'` | Restricts all resource loading |
| `Cache-Control` | `no-store` | Prevents caching of API responses |
| `Referrer-Policy` | `no-referrer` | Prevents referrer information from being leaked to other origins |
| `Permissions-Policy` | `geolocation=(), microphone=(), camera=()` | Disables browser feature APIs |

---

## 14. End-to-End Payload Example

The following table shows a complete end-to-end example of a CSR submission, describing what the AD proxy sends and what the RA service returns.

### 14.1 Request from AD Proxy to RA Service

| Part | Detail |
|---|---|
| Method and URL | POST `https://ra.internal/api/ra/ad/csr/submit` |
| Content-Type | `application/json` |
| X-AD-Username | `jsmith` |
| X-AD-Email | `john.smith@company.com` |
| X-AD-DisplayName | `John Smith` |
| X-AD-Groups | `RA-Users,PKI-Submitters` |
| X-Forwarded-For | `192.168.1.50` (the AD proxy IP, must be in whitelist) |
| X-XSRF-TOKEN | `csrf-token-value-here` |
| Cookie | `XSRF-TOKEN=csrf-token-value-here` |
| Body — pkcs10_pem | PEM-encoded PKCS#10 CSR with Subject CN equal to `John Smith` or `jsmith` |
| Body — certificate_profile | `DSC` |
| Body — validity_days | `365` |
| Body — remarks | `Annual DSC renewal` |

### 14.2 Success Response from RA Service

| Part | Detail |
|---|---|
| HTTP Status | `201 Created` |
| submission_id | `3f7a1c2e-9b4d-4f2a-8e1c-5d6f7a8b9c0d` |
| status | `PENDING` |
| submitted_by | `John Smith` |
| submitted_at | `2026-05-09T08:30:00Z` |
| message | `CSR accepted and pending RA review` |

### 14.3 Error Response — Identity Mismatch

| Part | Detail |
|---|---|
| HTTP Status | `422 Unprocessable Entity` |
| error_code | `RA-IDN-001` |
| message | `CSR Subject CN does not match the authenticated AD username or email` |
| category | `IDENTITY` |
| http_status | `422` |
| timestamp | `2026-05-09T08:30:01Z` |
| request_id | `req-abc-123` |
