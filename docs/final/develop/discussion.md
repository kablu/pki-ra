# PKI-RA Architecture Discussion Log

**Project:** pki-ra (Enterprise Registration Authority)  
**Branch:** csr-approval-dev  
**Participants:** Kablu Mandal, Sebastian Reick (Architect)  
**Period:** June–July 2026  

---

## Table of Contents

1. [Sebastian's Architecture Review Feedback](#1-sebastians-architecture-review-feedback)
2. [V1 Changes — Point-wise Summary](#2-v1-changes--point-wise-summary)
3. [V2 RFC Document](#3-v2-rfc-document)
4. [Email Reply to Sebastian](#4-email-reply-to-sebastian)
5. [CSR Validation — John's Scenario](#5-csr-validation--johns-scenario)
6. [7-Layer Validation — Detailed Walkthrough (AD Context)](#6-7-layer-validation--detailed-walkthrough-ad-context)
7. [FQDN Validation in Code](#7-fqdn-validation-in-code)

---

## 1. Sebastian's Architecture Review Feedback

Sebastian reviewed `RA_Approval_Workflow_Architecture.txt` and gave feedback across 4 sections.

### Database Feedback

| # | Feedback |
|---|----------|
| 1 | `request_id` year-based sequence softlocks at 999,999 |
| 2 | `csr_profile` should be a separate table, not a VARCHAR column |
| 3 | Requestor data is redundant if `requestor_user_id` FK exists |
| 4 | Replace `requested_validity_days` with explicit `notBefore`/`notAfter` fields |
| 5 | Rename `assigned_to_id` to `maker_id` for symmetry with `checker_id` |
| 6 | Separate approval workflow columns from core request table |

### Endpoints Feedback

| # | Feedback |
|---|----------|
| 1 | Combine `/by-request-id` and `/by-txn-id` into unified query-param style |
| 2 | `/{id}/history` exposes internal DB id — use `requestId` and restrict auth |
| 3 | `/certificates/{serial}` — serial alone not unique across multiple CAs |
| 4 | Merge `/approve` + `/accept` and `/pickup` + `/pickup-check` server-side |

### Configuration Feedback

| # | Feedback |
|---|----------|
| 1 | Single system-wide config too restrictive — different profiles need different workflows |

### Request Payload Feedback

| # | Feedback |
|---|----------|
| 1 | Why limit to PKCS#10? WLCA also supports CRMF |
| 2 | `clientTxnId` — why mandatory? Server should handle if not provided |
| 3 | `subjectAltNames` is already inside PKCS#10 — redundant field |
| 4 | `requestorData` — is this for authentication or information? |
| 5 | `additionalAttributes` — be careful with unknown JSONs, limit allowed keys |

---

## 2. V1 Changes — Point-wise Summary

### Database Changes

**request_id — Sequence widened**
- Previous: `RA-REQ-2026-000042` (max 999,999/year)
- Updated: `RA-REQ-2026-00000042` (max 99,999,999/year)
- Tag: `[CHANGED v1 — C-02]`

**csr_profile — Dedicated table**
- `csr_profile VARCHAR(50)` removed from `csr_requests`
- New table `csr_profiles` introduced
- `csr_requests` now holds `profile_id FK → csr_profiles.id`
- Tag: `[CHANGED v1 — C-03]`

**Requestor data — Split to separate table**
- New table `csr_requestor_info`
- Authenticated user: only `requestor_user_id FK` stored, name/email fetched via JOIN
- Anonymous: nullable free-form fields retained
- Tag: `[CHANGED v1 — C-07]`

**requested_validity_days — Replaced**
- Removed `requested_validity_days INT`
- Added `requested_not_before DATETIME NULL` and `requested_not_after DATETIME NULL`
- Resolution rules:

| Fields Provided | Result |
|-----------------|--------|
| Neither | `notBefore = now`, `notAfter = now + profile default` |
| Only notBefore | `notAfter = notBefore + profile default` |
| Only notAfter | `notBefore = now`, validate `notAfter > now` |
| Both | Use as-is, validate within profile limits |

- Tag: `[CHANGED v1 — C-05]`

**assigned_to_id → maker_id**
- Renamed for symmetry with `checker_id`
- Directly reflects Maker-Checker model
- Tag: `[CHANGED v1 — C-04]`

**Table split**
- Single wide table split into three:
  - `csr_requests` — core request data
  - `csr_requestor_info` — who requested, validity, purpose, priority
  - `csr_approval_workflow` — maker/checker IDs, remarks, decisions, timestamps
  - `certificate_request_transitions` — unchanged, full immutable history
- Tag: `[CHANGED v1 — C-06]`

### Endpoint Changes

**Lookup endpoints unified** — Tag: `[CHANGED v1 — C-08]`
```
Removed:
  GET /api/ra/requests/by-request-id/{reqId}
  GET /api/ra/requests/by-txn-id/{txnId}

Added:
  GET /api/ra/requests?requestId=RA-REQ-2026-00000042
  GET /api/ra/requests?clientTxnId=TXN-2026-06-25-00042
```

**History endpoint** — Tag: `[CHANGED v1 — C-09]`
```
Removed: GET /api/ra/requests/{id}/history  (internal DB id)
Added:   GET /api/ra/requests/{requestId}/history
Auth:    ROLE_ADMIN and ROLE_AUDITOR only
```

**Certificate lookup** — Tag: `[CHANGED v1 — C-10]`
```
Removed: GET /api/ra/certificates/{serial}

Added:
  GET /api/ra/certificates?issuerDn=...&serialNr=...  (RFC 5280 unique)
  GET /api/ra/certificates?requestId=RA-REQ-2026-00000042
```

**Merged endpoints** — Tag: `[CHANGED v1 — C-11, C-12]`
```
/pickup  → routes to Maker (SUBMITTED) or Checker (REVIEWED) server-side
/approve → routes by role and approval_mode_at_pickup server-side
PKI_APR_012 removed
```

### Configuration Changes — Tag: `[CHANGED v1 — C-13]`

**Per-profile workflow configuration model:**
- Admin defines named workflow configurations
- Each certificate profile mapped to one config
- Three new tables: `workflow_configurations`, `csr_profiles`, `profile_workflow_mapping`
- At pickup: `workflow_config_id` and `approval_mode_at_pickup` locked on request
- Config changes only affect future pickups — in-flight requests unaffected

Example mappings:
```
TLS_SERVER   → High Security DUAL Self-Pickup
CODE_SIGNING → High Security DUAL Self-Pickup
TLS_CLIENT   → Standard SINGLE Self-Pickup
SMIME        → Standard SINGLE Self-Pickup
```

### Request Payload Response

| Point | Decision |
|-------|----------|
| PKCS#10 vs CRMF | Deferred to `RA_CSR_Request_Payload_and_Validation.md` — both will be covered |
| clientTxnId | Made optional. If provided: 1:1 mapped with `requestId`. If not: client uses server-generated `requestId` |
| subjectAltNames | Field removed from payload — SANs must be inside CSR itself |
| requestorData + additionalAttributes | Deferred to `RA_CSR_Request_Payload_and_Validation.md` — AD-based auth (email only), allowed key definitions |

### CA Integration Changes — Tag: `[CHANGED v1 — C-14]`

- New intermediate statuses: `SENT_TO_CA`, `PENDING`
- Three CA outcomes: ISSUED (sync), PENDING (async), FAILED
- New callback: `POST /api/ra/ca/callback` for async delivery

---

## 3. V2 RFC Document

`RA_Approval_Workflow_Architecture_V2.md` created — RFC-style reformat of V1.

```
Internal Technical Specification                            Kablu Mandal
Document ID: PKI-RA-APPR-001                                   Magellan
Version: 2.0                                               30 June 2026
```

**22 sections:**

| Section | Content |
|---------|---------|
| 1–4 | Introduction, Conventions (RFC 2119), Pre-Approval Flow, Actors |
| 5–6 | Per-Profile Config (3 tables, Admin API, Runtime Resolution), State Machine |
| 7–8 | SINGLE flow with Self-Pickup, DUAL flow with Self-Pickup |
| 9 | Database Schema (4 tables) |
| 10 | REST API Reference (6 sub-sections) |
| 11–12 | Record Visibility, Transition History |
| 13 | CA Integration and Issuance States |
| 14 | Mode-Switch Rules |
| 15 | Role and Permission Matrix |
| 16–17 | Admin Dashboard, Audit Log Events |
| 18 | Error Codes |
| 19 | Security Considerations |
| 20 | Testing Plan (5 test suites) |
| 21–22 | References, Authors |

Uses RFC 2119 normative language — MUST, SHOULD, MAY throughout.

---

## 4. Email Reply to Sebastian

Full point-wise email reply drafted covering all 4 sections of Sebastian's feedback.

**Key decisions communicated:**
- All database and endpoint changes incorporated in V1
- Per-profile configuration model fully designed with 3 new tables
- Request payload points (PKCS#10/CRMF, subjectAltNames, requestorData) deferred to upcoming `RA_CSR_Request_Payload_and_Validation.md`
- clientTxnId made optional with clear behaviour documented
- subjectAltNames field removed from payload

File: `docs/final/develop/feedback.txt`

---

## 5. CSR Validation — John's Scenario

### The Scenario

**John** is a person (authenticated as `john@acme.com`) submitting a CSR for a server.

**Two sub-cases:**

**Case A — Wrong CN format:**
```
Authenticated user: john@acme.com
CSR Subject:        CN=John Doe, O=Acme Corp    ← person's name
Template:           TLS_SERVER
SAN:                dns:api.acme.com
```
→ Caught at **Layer 6, Step 6.10** — CN contains space, not a valid hostname  
→ Error: `PKI_POL_013`

**Case B — Right format, wrong authorization:**
```
Authenticated user: john@acme.com (ROLE_USER)
CSR Subject:        CN=api.acme.com             ← valid FQDN
Template:           TLS_SERVER
SAN:                dns:api.acme.com
```
→ Caught at **Layer 6, Step 6.7** — John not in `domain_authorizations` for `acme.com`  
→ Error: `PKI_POL_012`

### How It Gets Caught Layer by Layer

| Layer | What Happens |
|-------|-------------|
| Layer 2 | John authenticates via AD. `requestor_user_id` = John's id. Role = `ROLE_USER` |
| Layer 4 | Case A: `CN=John Doe` passes syntax check (valid DN). Case B: `CN=api.acme.com` passes FQDN syntax |
| Layer 5 | Self-signature verified — John possesses the private key (valid even for server certs) |
| Layer 6 Step 6.1 | Template auto-detected as TLS_SERVER (dNSName SAN present) |
| Layer 6 Step 6.10 | Case A: CN has space → `REJECT PKI_POL_013` |
| Layer 6 Step 6.7 | Case B: Domain `acme.com` — John not in `domain_authorizations` → `REJECT PKI_POL_012` |

### Decision Table

| Requestor | CSR Subject | Role | Domain Auth | Outcome |
|-----------|-------------|------|-------------|---------|
| john@acme | CN=John Doe | ROLE_USER | — | REJECT PKI_POL_013 — CN not hostname |
| john@acme | CN=api.acme.com | ROLE_USER | Not authorized | REJECT PKI_POL_012 — domain not authorized |
| john@acme | CN=api.acme.com | ROLE_USER | Authorized for acme.com | ALLOW — enters approval queue |
| john@acme | CN=api.acme.com | ROLE_RA_OFFICER | Any | ALLOW — RA Officer override, audit logged |

### New Table: domain_authorizations

```sql
CREATE TABLE domain_authorizations (
    id                BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id           BIGINT NOT NULL,      -- FK → users.id
    authorized_domain VARCHAR(253) NOT NULL, -- e.g. acme.com
    granted_by        BIGINT NOT NULL,      -- FK → users.id (Admin)
    granted_at        DATETIME(6) NOT NULL,
    expires_at        DATETIME(6) NULL,     -- NULL = no expiry
    is_active         BOOLEAN NOT NULL DEFAULT true
);
```

### New Error Codes Added

| Code | HTTP | Description |
|------|------|-------------|
| `PKI_POL_012` | 422 | Requester not authorized to obtain server certificate for this domain |
| `PKI_POL_013` | 422 | Subject CN format does not match certificate type — TLS Server requires a valid hostname, not a person's name |

### Recommended Workflow for John

1. Admin grants John domain authorization for `acme.com` in `domain_authorizations`
2. John generates key pair **on the server**
3. John creates CSR: `CN=api.acme.com`, `SAN=dns:api.acme.com`
4. John submits CSR via RA REST API with his AD credentials
5. Layer 2 authenticates John via AD. Layer 6.7 confirms domain authorization
6. Request enters `SUBMITTED` status → approval workflow
7. Operator reviews and approves → CA issues certificate
8. John downloads certificate and installs on server

---

## 6. 7-Layer Validation — Detailed Walkthrough (AD Context)

### Authentication Context

This project uses **Active Directory (AD)** for authentication — no internal password database.

```
John → REST API → Bearer JWT Token (issued by AD via OAuth2/OIDC)
RA verifies token signature using AD public key
Token contains: john@acme.com, display_name, AD group memberships

AD Groups → RA Roles mapping:
  CN=PKI-RA-Admins     → ROLE_ADMIN
  CN=PKI-RA-Operators  → ROLE_OPERATOR
  CN=PKI-RA-Officers   → ROLE_RA_OFFICER
  CN=PKI-Users         → ROLE_USER
  CN=PKI-Auditors      → ROLE_AUDITOR
```

### Layer 1 — Transport & Protocol

| Check | Detail |
|-------|--------|
| TLS mandatory | Plain HTTP rejected or redirected |
| TLS version | Minimum TLS 1.2 — TLS 1.0/1.1 rejected |
| mTLS | Only for machine-to-machine (SCEP, CMP) — not for human users |
| HTTP Method | POST only for CSR submission |
| Content-Type | `application/json` for REST, `application/pkcs10` for EST |
| Size limit | CSR max 64KB, body max 64KB |
| Rate limiting | Max 10 CSR/minute per client — 429 if exceeded |
| IP allowlist | Corporate network IPs only |

### Layer 2 — Authentication & Authorization (AD)

**Step 2.1 — Identify Requester (AD flow):**
```
REST API:
  John sends Bearer JWT token
  RA validates JWT signature against AD OIDC public keys
  Extracts: sub, email, groups, exp

LDAP bind alternative:
  RA binds to AD: ldap://ad.acme.com with John's credentials
  AD returns: SUCCESS + DN, email, memberOf groups
```

**Step 2.2 — Credential Verify:**
- JWT: signature valid? Not expired? Correct audience (RA's client_id)?
- LDAP: bind success = password correct

**Step 2.3 — Map to Internal User:**
```
AD authenticated john@acme.com
  ↓
SELECT * FROM users WHERE email = 'john@acme.com'
  Found    → use existing userId
  Not found → auto-provision (create from AD data, assign default role)
```

**Step 2.4 — Role Check:**
- AD groups extracted from JWT claims or LDAP `memberOf`
- Mapped to RA roles
- Template-level permission check: is John's role allowed for this certificate type?

**Step 2.5 — Audit:**
- Every auth attempt logged (success + failure)
- 5 consecutive failures → temporary lockout (15 min, RA-level — separate from AD lockout policy)

### Layer 3 — Request Envelope

| Protocol | Envelope Format |
|----------|----------------|
| REST JSON | `{ "pkcs10": "-----BEGIN..." }` |
| EST | Base64-encoded raw PKCS#10 in body |
| SCEP | PKCS#7 SignedData → EnvelopedData → PKCS#10 |
| CMP | PKIMessage → PKIHeader + PKIBody → CertRequest |

**Replay Protection:**
- CMP: `senderNonce` stored in DB, rejected if seen again within 24h
- SCEP: `senderNonce + transactionID` unique check
- REST: optional `clientTxnId` idempotency

**Output of Layer 3:** Clean PKCS#10 DER/PEM extracted regardless of protocol.

### Layer 4 — CSR Structure & Syntax

**PKCS#10 structure (RFC 2986):**
```asn1
CertificationRequest ::= SEQUENCE {
    certificationRequestInfo  CertificationRequestInfo,
    signatureAlgorithm        AlgorithmIdentifier,
    signature                 BIT STRING
}
```

Key checks:
- PEM headers correct (`CERTIFICATE REQUEST`)
- ASN.1 decodable, no trailing garbage
- version = 0 (only defined version)
- Subject DN: no null bytes, no control chars, no injection patterns, CN not IP address
- SAN: valid FQDN, valid email, valid IP (not loopback), max 100 SANs
- Signature algorithm: SHA1/MD5 rejected, SHA256+ accepted

### Layer 5 — Cryptographic Validation

**Proof of Possession (most critical check):**
```
CSR self-signature verify:
  Take certificationRequestInfo bytes from CSR
  Take signatureAlgorithm from CSR
  Take signature from CSR
  Take publicKey from CSR
  Verify: signature over certificationRequestInfo using publicKey
  FAIL → REJECT PKI_CRYPTO_001 "CSR signature verification failed"
```

Why critical: Without this, an attacker could embed someone else's public key in a CSR and get a certificate — even without the private key.

**Key strength:**

| Algorithm | Minimum | Reject |
|-----------|---------|--------|
| RSA | 2048 bits | RSA-1024, RSA-512 |
| ECDSA | P-256 | P-192, secp192r1 |
| Ed25519 | 256 (fixed) | Always accept |

**RSA quality checks:**
- Exponent `e=3` → REJECT (Hastad's broadcast attack)
- Modulus must be odd, no small factors
- Debian weak key list check (2006-2008 OpenSSL bug)
- ROCA check (CVE-2017-15361 Infineon TPM flaw)

### Layer 6 — Policy & Business Rules

**Template auto-detection:**
```
dNSName SAN present, no email SAN → TLS_SERVER
email SAN present                  → SMIME
CN = username, no SAN             → TLS_CLIENT
EKU = codeSigning                 → CODE_SIGNING
```

**Subject DN policy per template:**
- TLS_SERVER: CN must be FQDN (no spaces, has dots, not a person's name)
- S/MIME: email SAN must match `users.email` for requestor
- TLS_CLIENT: CN should resolve to a known user

**Key Usage check:**
- `keyCertSign` or `cRLSign` in CSR → REJECT (CA-only usage)
- `anyExtendedKeyUsage` → REJECT (too permissive)

**Requester scope (Step 6.7):**
- TLS_SERVER: John must be in `domain_authorizations` OR have `ROLE_RA_OFFICER`
- S/MIME: CSR email must match John's registered email
- TLS_CLIENT: CN must match John's own identity (unless RA_OFFICER)

**CN format cross-check (Step 6.10):**
```
TLS_SERVER + CN has space      → REJECT PKI_POL_013
TLS_SERVER + CN has no dot    → REJECT PKI_POL_013
TLS_SERVER + CN = display_name → REJECT PKI_POL_013
TLS_SERVER + CN is valid FQDN → PASS
```

### Layer 7 — Duplicate & Conflict Detection

| Check | Logic |
|-------|-------|
| Exact duplicate CSR | SHA-256 of raw CSR bytes — same hash + PENDING/APPROVED/ISSUED → REJECT |
| Same Subject DN | Active cert same user → ALLOW + INFO. Different user → WARN + FLAG |
| SAN overlap | Same user, expiring <30d → ALLOW (renewal). Different user → FLAG for RA review |
| Key reuse | Same key, different subject → REJECT. Same key, revoked for compromise → REJECT |
| Velocity anomaly | >20 unique subjects/hour from same requester → FLAG (not auto-reject) |

**Final verdict:**
```
All PASS, no flags  → SUBMITTED (approval queue)
All PASS, with WARNs → SUBMITTED (warnings shown to operator)
Any FLAG            → PENDING_REVIEW (RA Officer manual review)
Any REJECT          → REJECTED (immediate, with error code)
```

---

## 7. FQDN Validation in Code

### Rules (RFC 1123 + RFC 5280)

```
Valid:
  api.acme.com      — standard FQDN
  acme.com          — apex domain
  *.acme.com        — wildcard (leftmost label only)
  a.b.c.d.acme.com  — multi-level subdomain

Invalid:
  "John Doe"        — contains space (person's name)
  192.168.1.1       — IP address
  localhost         — reserved
  api..acme.com     — empty label (double dot)
  -api.acme.com     — label starts with hyphen
  api.acme.com.     — trailing dot
  *.*.acme.com      — double wildcard
  <64-char-label>.com — label > 63 chars
  com               — bare TLD (no dot)
```

### Java Implementation

```java
public class FqdnValidator {

    private static final Pattern FQDN_PATTERN = Pattern.compile(
        "^(?=.{1,253}$)"
        + "((?!-)[a-zA-Z0-9-]{1,63}(?<!-)\\.)+"
        + "(?!-)[a-zA-Z]{2,63}$"
    );

    private static final Pattern IPV4_PATTERN = Pattern.compile(
        "^((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}"
        + "(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$"
    );

    public static ValidationResult validate(String input) {

        if (input == null || input.isBlank())
            return ValidationResult.reject("FQDN is null or blank");

        String value = input.trim().toLowerCase();

        // 1. IP address check
        if (IPV4_PATTERN.matcher(value).matches())
            return ValidationResult.reject(
                "IP address is not a valid FQDN for TLS Server certificate");

        // 2. Space check — catches "John Doe"
        if (value.contains(" "))
            return ValidationResult.reject(
                "FQDN must not contain spaces -- "
                + "Subject CN appears to be a person's name, not a hostname");

        // 3. Reserved names
        if (value.equals("localhost") || value.endsWith(".localhost"))
            return ValidationResult.reject("localhost is not allowed");

        // 4. Trailing dot
        if (value.endsWith("."))
            return ValidationResult.reject("Trailing dot not allowed");

        // 5. Consecutive dots
        if (value.contains(".."))
            return ValidationResult.reject("Consecutive dots — empty label");

        // 6. Wildcard check
        if (value.startsWith("*")) {
            if (!value.startsWith("*.") || value.indexOf("*", 1) >= 0)
                return ValidationResult.reject(
                    "Wildcard only allowed as leftmost label: *.example.com");
            String base = value.substring(2);
            if (!FQDN_PATTERN.matcher(base).matches())
                return ValidationResult.reject("Wildcard base invalid: " + base);
            return ValidationResult.pass("Valid wildcard FQDN");
        }

        // 7. Regex match
        if (!FQDN_PATTERN.matcher(value).matches())
            return ValidationResult.reject(
                "'" + input + "' is not a valid FQDN");

        // 8. Per-label checks
        String[] labels = value.split("\\.");
        for (String label : labels) {
            if (label.length() > 63)
                return ValidationResult.reject(
                    "Label '" + label + "' exceeds 63 character limit");
            if (label.startsWith("-") || label.endsWith("-"))
                return ValidationResult.reject(
                    "Label '" + label + "' must not start or end with hyphen");
        }

        // 9. Minimum 2 labels
        if (labels.length < 2)
            return ValidationResult.reject(
                "FQDN must have at least two labels (e.g., acme.com)");

        return ValidationResult.pass("Valid FQDN");
    }

    public static class ValidationResult {
        public final boolean valid;
        public final String  message;

        private ValidationResult(boolean valid, String message) {
            this.valid = valid; this.message = message;
        }
        public static ValidationResult pass(String msg) {
            return new ValidationResult(true, msg);
        }
        public static ValidationResult reject(String msg) {
            return new ValidationResult(false, msg);
        }
    }
}
```

### Integration with Layer 6

```java
public void validateSubjectDnForTemplate(
        String cn, CertificateTemplate template, User requestor) {

    if (template.getType() == CertificateType.TLS_SERVER) {

        ValidationResult result = FqdnValidator.validate(cn);
        if (!result.valid) {
            throw new PolicyViolationException("PKI_POL_013",
                "Subject CN '" + cn + "' invalid for TLS Server: "
                + result.message);
        }

        // CN must not match requestor's personal identity
        if (cn.equalsIgnoreCase(requestor.getDisplayName())
                || cn.equalsIgnoreCase(requestor.getEmail())) {
            throw new PolicyViolationException("PKI_POL_013",
                "Subject CN matches requestor's personal identity. "
                + "TLS Server CN must be a hostname, not a person's name.");
        }
    }
}
```

### Key Insight

The **space check** (`value.contains(" ")`) catches `"John Doe"` immediately — before the regex even runs. Explicit, readable checks are preferred over relying solely on regex for security-critical validation.

---

*Document generated from architecture discussion session — June/July 2026*  
*Related files: RA_Approval_Workflow_Architecture_V1.md, V2.md, CSR_Validation_7_Layer_Guide.txt*
