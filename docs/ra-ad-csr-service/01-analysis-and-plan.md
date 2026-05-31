# RA AD CSR Service — Analysis & Plan

**Document type:** Phase 1 — Analysis and Plan
**Service name:** `ra-ad-csr-service`
**Module path:** `pki-ra/subprojects/ra-ad-csr-service`
**Stack:** Spring Boot 4 · Java 26 · Bouncy Castle 1.80 · Spring Security 7

---

## 1. Problem Statement

The Registration Authority (RA) must accept Digital Signature Certificate (DSC) requests from applicants who have already been authenticated via **Active Directory (AD)**. The AD authentication is handled externally by a reverse proxy or API gateway (e.g., Apache mod_auth_sspi, Microsoft IIS, NGINX + LDAP, or an API Management layer).

Once the AD gateway validates the user, it forwards the authenticated identity in an HTTP request header. The RA service must:

1. Trust the identity header set by the **internal AD gateway only** (reject direct client calls)
2. Accept and cryptographically validate the applicant's **PKCS#10 CSR** in the request body
3. Validate that the CSR identity fields match the authenticated AD user
4. Defend against XSS, CSRF, header injection, and other OWASP Top 10 attacks
5. Persist an audit trail for every CSR submission attempt
6. Forward approved CSR submissions to `ca-pki` for certificate issuance

---

## 2. Actors and Stakeholders

| Actor | Role |
|---|---|
| **AD-Authenticated Applicant** | Submits CSR after AD login via browser/client |
| **AD Reverse Proxy / Gateway** | Authenticates user, injects identity header, forwards request |
| **RA AD CSR Service** | Validates header + CSR, persists record, calls CA |
| **CA Service (`ca-pki`)** | Signs the CSR and issues the X.509 certificate |
| **RA Operator** | Reviews submissions, approves or rejects via RA portal |
| **Audit / Compliance Officer** | Reviews audit trail for regulatory purposes |

---

## 3. Scope

### In scope (Phase 1)
- New Spring Boot 4 / Java 26 module: `ra-ad-csr-service`
- `POST /api/ra/ad/csr/submit` endpoint
- AD identity header extraction and validation
- PKCS#10 CSR parsing and cryptographic validation
- Subject DN vs AD identity cross-validation
- Spring Security configuration (XSS headers, CSRF, CORS)
- Input sanitization — header injection prevention
- Request logging and audit trail (reusing `:common`)
- Bean reuse from `:common` module
- Swagger / OpenAPI documentation
- Unit and integration tests

### Out of scope (Phase 1 — future sprints)
- Direct LDAP / Active Directory lookup to verify user attributes
- CSR signing — forwarding to `ca-pki` (Phase 2)
- Email notifications to applicants
- RA operator approval workflow UI
- Certificate status tracking

---

## 4. Assumptions

| # | Assumption |
|---|---|
| A1 | The AD reverse proxy is deployed in the same internal network segment as this service |
| A2 | The proxy sets `X-AD-Username` (sAMAccountName) and `X-AD-Email` (UPN / email) headers |
| A3 | Direct access from the public internet to this service is blocked at the network layer |
| A4 | The service runs behind a trusted internal load balancer; `X-Forwarded-For` is trusted |
| A5 | CSRs are submitted in PEM format (`-----BEGIN CERTIFICATE REQUEST-----`) |
| A6 | The `:common` module JAR is available in the Gradle multi-module build |
| A7 | MariaDB is the production datasource; H2 is used for local development under the `h2` profile |

---

## 5. Constraints

| # | Constraint |
|---|---|
| C1 | Java 26 (latest LTS candidate) — uses records, sealed classes, pattern matching |
| C2 | Spring Boot 4 (Spring Framework 7) — Jakarta EE 11 namespace |
| C3 | Must not expose private key material — CSR only, no key generation |
| C4 | All headers containing identity must be stripped from responses |
| C5 | CSRF protection must be enabled for browser-based flows |
| C6 | Bouncy Castle 1.80 for all PKCS#10 / X.509 operations |
| C7 | No hard-coded credentials — all secrets via environment variables or Vault |

---

## 6. Risk Analysis

| Risk | Impact | Probability | Mitigation |
|---|---|---|---|
| Header spoofing — attacker injects own `X-AD-Username` header | High | Medium | Whitelist trusted proxy IP; reject requests without proxy signature |
| Malformed CSR DoS — oversized or deeply nested ASN.1 | Medium | Low | Enforce max body size (16 KB); timeout on CSR parse |
| Replay attack — same CSR submitted multiple times | Medium | Medium | Store CSR hash; reject duplicates within a TTL window |
| LDAP injection via username | High | Low | Treat header as opaque string; validate with regex before any LDAP use |
| XSS in error responses | Medium | Low | Strict CSP headers; encode all user-controlled data in error messages |

---

## 7. Technology Decisions

| Decision | Choice | Reason |
|---|---|---|
| Language level | Java 26 | Latest features: unnamed patterns, value types (preview), structured concurrency |
| Framework | Spring Boot 4.0 | Team standard; Jakarta EE 11 |
| Cryptography | Bouncy Castle 1.80 | Consistent with `ca-pki` |
| Security | Spring Security 7 | CSRF, CORS, security headers, method security |
| Validation | Jakarta Bean Validation 3.1 | Declarative constraints on request DTOs |
| Input sanitization | OWASP Java HTML Sanitizer | Strip any HTML from string inputs |
| DB | MariaDB (prod) / H2 (dev) | Reuse `:common` DataSourceConfig |
| ORM | Spring Data JPA + Hibernate 7 | Consistent with other modules |
| API docs | SpringDoc OpenAPI 3 | Swagger UI for testing |
| Testing | JUnit 5 + MockMvc + Testcontainers | Unit + integration |

---

## 8. High-Level Architecture

The request flow through the system proceeds through four main tiers:

**Tier 1 — AD Applicant Browser or Client:** The end user submits a CSR via a browser or API client over HTTPS. The client never communicates directly with the RA service.

**Tier 2 — AD Reverse Proxy or API Gateway:** The proxy intercepts every request, authenticates the user via Kerberos, NTLM, or LDAP, and injects the `X-AD-Username` and `X-AD-Email` identity headers. Any pre-existing identity headers from the client are stripped before forwarding. The proxy then forwards the request to the RA service over an internal trusted HTTP channel.

**Tier 3 — ra-ad-csr-service:** This is the core service. It processes each request through the following internal components in sequence:

- **SecurityFilter and AdIdentityFilter** — The Spring Security filter chain runs first, enforcing CSRF and CORS policy. The `AdIdentityFilter` then validates the originating IP against the trusted proxy whitelist and extracts the AD identity headers.
- **HeaderValidator** — Validates the extracted identity values against format rules and length limits.
- **CsrSubmitController** — Receives the validated request and delegates immediately to the service layer. Contains no business logic itself.
- **CsrValidationService** — Orchestrates the full nine-stage CSR validation pipeline, calling the header validator, PKCS#10 cryptographic validator (via Bouncy Castle), and the identity matcher.
- **CsrSubmissionService** — Performs the duplicate hash check, persists the `CsrSubmissionRecord` entity, and records the audit log entry via `AuditLogService` from the `:common` module.

**Tier 4 — CA Service (Phase 2):** In a future phase, approved submissions are forwarded to the `ca-pki` Certificate Authority for signing and certificate issuance. This integration is out of scope for Phase 1.

---

## 9. Delivery Plan

### Sprint 1 — Foundation (Week 1–2)
- [ ] Create Gradle submodule `ra-ad-csr-service` in `pki-ra/subprojects/`
- [ ] Configure `build.gradle` with dependencies (Spring Boot 4, BC 1.80, `:common`)
- [ ] Implement `AdIdentityFilter` — header extraction and IP whitelist guard
- [ ] Implement `CsrSubmissionController` skeleton with request/response DTOs
- [ ] Implement `HeaderValidator` — format rules for username and email
- [ ] Implement `Pkcs10CsrValidator` — parse, signature check, key strength
- [ ] Implement `CsrIdentityMatcher` — CN / SAN vs AD identity check
- [ ] Spring Security configuration — CSRF, CORS, XSS headers
- [ ] Wire `:common` beans — DataSource, AuditLogService, RequestLog
- [ ] `CsrSubmissionRecord` JPA entity + repository
- [ ] Swagger / OpenAPI documentation
- [ ] Unit tests for all validators
- [ ] Integration test with MockMvc

### Sprint 2 — CA Integration and Hardening (Week 3–4)
- [ ] CA client call — forward validated CSR to `ca-pki` via `:ra-client`
- [ ] Store CA response (certificate, serial, status) in `CsrSubmissionRecord`
- [ ] Replay attack prevention — CSR hash uniqueness check
- [ ] Rate limiting per AD username (Bucket4j or Resilience4j)
- [ ] Trusted proxy IP validation via `TrustedProxyConfig`
- [ ] Penetration test checklist verification
- [ ] Load test (100 concurrent CSR submissions)
