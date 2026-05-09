# RA AD CSR Service — Security Architecture

**Document type:** Security Design
**Module:** `pki-ra/subprojects/ra-ad-csr-service`

---

## 1. Overview

Security in `ra-ad-csr-service` is layered. No single control is relied upon alone. Each layer assumes the previous layer may have been bypassed and applies its own independent check. This defence-in-depth approach ensures that a failure or misconfiguration at one layer does not expose the system.

The service is an internal-facing API. It is never exposed directly to the internet. All requests arrive from a trusted internal proxy (Active Directory-aware reverse proxy) that has already authenticated the user via Kerberos/NTLM and injected AD identity headers. The service's security design is built around this trust model.

---

## 2. Security Layers

| Layer | Control | Where Applied |
|---|---|---|
| L1 | IP Whitelist | `AdIdentityFilter` — first check on every request |
| L2 | AD Identity Header Validation | `AdIdentityFilter` — after IP whitelist passes |
| L3 | CRLF and Header Injection Prevention | `AdIdentityFilter` — on every extracted header value |
| L4 | Input Size Limits | Spring MVC + CSR validation pipeline |
| L5 | CSRF Protection | Spring Security — double-submit cookie pattern |
| L6 | CORS Policy | Spring Security — origin whitelist |
| L7 | Security Response Headers | Spring Security — all responses |
| L8 | Rate Limiting | Rate limit filter — per proxy IP |
| L9 | CSR Cryptographic Validation | `CsrValidationService` — signature, algorithm, key size |
| L10 | Identity Binding | `CsrValidationService` — CN vs AD username match |
| L11 | Maintenance Mode | `AdIdentityFilter` — reads from `ra_config` cache |

---

## 3. Layer Descriptions

### L1 — IP Whitelist

Only requests from known, trusted proxy IP addresses are accepted. The list of allowed IPs is stored in `ra_config` under the key `security.proxy.whitelist.ips` and is loaded into the in-memory configuration cache at application startup.

If the incoming request's remote IP is not in the whitelist, the filter immediately returns HTTP 403 Forbidden and no further processing occurs. No exception is logged — this is treated as an expected boundary condition, not an application error. An audit record with action `CSR_SUBMIT_UNAUTHORIZED` is written.

The whitelist can be updated at runtime by modifying the `ra_config` table and calling the admin cache refresh endpoint — no service restart required.

---

### L2 — AD Identity Header Validation

After the IP check passes, the filter extracts four AD identity headers:
- `X-AD-Username` — the AD sAMAccountName
- `X-AD-DisplayName` — the user's display name
- `X-AD-Email` — the user's email address
- `X-AD-Groups` — comma-separated list of AD group memberships

The header names themselves are configurable in `ra_config` (keys `ad.header.username`, `ad.header.display.name`, etc.), which allows adaptation to different proxy configurations without code changes.

**Validation rules for each header:**
- The header must be present
- The value must not be blank after trimming
- The value length must not exceed `security.header.max.length` (default 512 characters)

If any of the four required headers is missing or blank, the filter returns HTTP 400 with error code `RA-HDR-001`. If a header value exceeds the maximum length, HTTP 400 with `RA-HDR-002` is returned.

After validation, the filter builds an `AdIdentity` value object and sets a `UsernamePasswordAuthenticationToken` in the Spring Security `SecurityContextHolder`. This allows the shared `AuditorAwareImpl` bean to retrieve the AD username for automatic audit column population.

---

### L3 — CRLF and Header Injection Prevention

Any value extracted from an HTTP header is immediately sanitised to remove carriage return (`\r`) and line feed (`\n`) characters. These characters are used in HTTP response splitting attacks, where a malicious proxy could inject additional response headers or a fake response body.

After CRLF stripping, the value is also checked against a pattern that rejects null bytes, control characters, and any characters outside the printable ASCII range for fields that are expected to contain ASCII-only content (such as the username).

This sanitisation runs on every header extraction before the value is stored in `AdIdentity` or used in any downstream logic.

---

### L4 — Input Size Limits

**HTTP request body size:** Spring MVC's default maximum request size is configured to 64 KB. CSR payloads are expected to be well under 16 KB. The 64 KB limit prevents memory exhaustion from unusually large JSON bodies.

**CSR PEM size:** The `csr.max.size.bytes` configuration key (default 8 192 bytes) enforces a tighter limit specifically on the CSR field before any cryptographic processing begins.

**JSON field lengths:** Jakarta Bean Validation constraints on the request DTO enforce maximum lengths on all string fields. This prevents long strings from reaching the service layer.

---

### L5 — CSRF Protection

The service uses the **double-submit cookie** pattern for CSRF protection. Spring Security generates a cryptographically random CSRF token and delivers it in a cookie. Browser-based clients must read this token from the cookie and send it back in a custom request header (`X-XSRF-TOKEN`). Spring Security verifies that the header value matches the cookie value.

This mechanism prevents cross-site request forgery because an attacker's origin cannot read the cookie value (same-origin policy) and therefore cannot construct a request with the matching header.

For non-browser clients (internal service-to-service calls), CSRF protection can be disabled per endpoint via the security configuration. This is appropriate for machine-to-machine flows where the client is not a browser.

---

### L6 — CORS Policy

Cross-Origin Resource Sharing is configured to allow only origins listed in `ra_config` key `security.cors.allowed.origins` (default: `https://ra.internal`). Requests from any other origin receive no CORS headers, causing browsers to block the response.

Allowed methods are limited to `POST` and `GET`. The `OPTIONS` preflight method is permitted automatically by Spring Security's CORS handling. Credentials (cookies) are allowed for the whitelisted origins.

The CORS allowed origins list is loaded from the configuration cache. Because CORS configuration is applied at application startup (during `SecurityFilterChain` bean construction), a service restart is required to change the CORS policy — unlike the IP whitelist, this is not dynamically refreshable.

---

### L7 — Security Response Headers

The following HTTP response headers are added to every response by Spring Security:

| Header | Value | Purpose |
|---|---|---|
| `Strict-Transport-Security` | `max-age=31536000; includeSubDomains` | Forces HTTPS for 1 year |
| `X-Content-Type-Options` | `nosniff` | Prevents MIME type sniffing |
| `X-Frame-Options` | `DENY` | Prevents clickjacking via iframes |
| `X-XSS-Protection` | `0` | Disables legacy XSS filter (CSP is the modern control) |
| `Content-Security-Policy` | `default-src 'none'; frame-ancestors 'none'` | Strict CSP for an API that returns JSON |
| `Cache-Control` | `no-store` | Prevents API responses from being cached |
| `Pragma` | `no-cache` | Compatibility header for older proxies |
| `Referrer-Policy` | `no-referrer` | Prevents referrer information leakage |

These headers are applied globally. No endpoint produces responses that require relaxing any of these headers — the service exclusively returns JSON, never HTML or embedded resources.

---

### L8 — Rate Limiting

Rate limiting protects the service from abuse by a single proxy IP. It is implemented as a filter that maintains per-IP request counters in a `ConcurrentHashMap`.

**Algorithm:** Fixed window counter. The window size is `security.rate.limit.window.seconds` (default 60 seconds). The maximum requests per window is `security.rate.limit.requests` (default 100). When the counter exceeds the limit, HTTP 429 Too Many Requests is returned with a `Retry-After` header indicating when the window resets.

**Why per-proxy IP:** Because all requests arrive via the internal proxy, rate limiting by end-user IP is not possible at this layer. Rate limiting by proxy IP protects against a single proxy going rogue or being compromised and flooding the RA service.

**Limitations:** The in-memory counter is not shared across multiple service instances in a clustered deployment. For Phase 1 (single instance), this is acceptable. Phase 2 should replace the in-memory counter with a Redis-backed distributed counter.

---

### L9 — CSR Cryptographic Validation

Described in detail in the CSR Validation Pipeline document (Section 5 and 6). In summary:

- Signature self-verification confirms the submitter holds the private key
- Algorithm policy enforcement blocks weak or unapproved algorithms
- Key size and curve enforcement blocks cryptographically weak keys

These checks are performed by `CsrValidationService` using Bouncy Castle 1.80.

---

### L10 — Identity Binding

The Common Name in the CSR subject must match the AD identity extracted from the request headers. This prevents a user from submitting a CSR with another person's name, which would result in a certificate impersonating that person.

This control is the primary defence against privilege escalation within the user population. Even if a malicious user constructs a technically valid CSR (passes all cryptographic checks), they cannot claim an identity other than the one the AD proxy has authenticated them as.

---

### L11 — Maintenance Mode

The `system.maintenance.mode` configuration key acts as a global kill switch. When set to `true` in the `ra_config` table and the cache is refreshed, the `AdIdentityFilter` immediately returns HTTP 503 Service Unavailable for all requests. This allows the RA team to take the service offline for maintenance without stopping the JVM process.

---

## 4. Authentication vs Authorisation

| Concern | Mechanism | Notes |
|---|---|---|
| Authentication | Performed by the upstream AD proxy before the request reaches this service | This service trusts the headers the proxy injects |
| Authorisation — basic | `AdIdentityFilter` checks that all required headers are present and the proxy IP is trusted | Every user who reaches the service is authorised to submit |
| Authorisation — group-based | Optional: `AdIdentityFilter` can check `X-AD-Groups` header against `ad.required.groups` config | Phase 1: not enforced; Phase 2: restrict to specific AD groups |
| Authorisation — resource | CN vs AD identity match (Stage 8) | A user can only submit CSRs for their own identity |

---

## 5. Threat Model Summary

| Threat | Control |
|---|---|
| External attacker bypassing the proxy | IP whitelist (L1) — only trusted proxy IPs accepted |
| Attacker forging AD headers from a trusted proxy IP | Service trusts the proxy completely — this is a deployment security concern, not an application concern |
| User submitting CSR with another user's identity | Stage 8 CN match (L10) |
| User submitting a forged CSR (wrong private key) | Stage 5 signature verification (L9) |
| Replay attack — resubmitting the same CSR | Stage 9 duplicate detection |
| Weak key submission | Stage 6 and 7 algorithm and key size policy (L9) |
| HTTP response splitting via injected headers | CRLF sanitisation (L3) |
| CSRF from browser | Double-submit cookie (L5) |
| Clickjacking | X-Frame-Options DENY (L7) |
| DoS via large payloads | Input size limits (L4) |
| DoS via high request volume | Rate limiting (L8) |
| Service exploitation during maintenance | Maintenance mode kill switch (L11) |

---

## 6. Security-Relevant Configuration Keys

| Key | Group | Purpose |
|---|---|---|
| `security.proxy.whitelist.ips` | SECURITY | IP whitelist for trusted proxies |
| `security.rate.limit.requests` | SECURITY | Max requests per window |
| `security.rate.limit.window.seconds` | SECURITY | Rate limit window size |
| `security.header.max.length` | SECURITY | Max header value length |
| `security.cors.allowed.origins` | SECURITY | CORS origin whitelist |
| `security.csrf.cookie.secure` | SECURITY | CSRF cookie Secure flag |
| `ad.required.groups` | AD | AD groups required for access (Phase 2) |
| `system.maintenance.mode` | SYSTEM | Global kill switch |
| `csr.allowed.signature.algorithms` | CSR | Algorithm whitelist |
| `csr.allowed.key.sizes` | CSR | Key size whitelist |
| `csr.allowed.ec.curves` | CSR | EC curve whitelist |
