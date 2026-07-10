# Enterprise RA Validation Guide

**Project:** pki-ra — Enterprise Registration Authority  
**Stack:** Spring Boot 3, Java 21, Bouncy Castle, Active Directory (LDAP/OIDC)  
**Branch:** csr-approval-dev  
**Version:** 1.0 | Date: 2026-07-03

---

## Table of Contents

1. [Overview & Philosophy](#1-overview--philosophy)
2. [Validation Architecture](#2-validation-architecture)
3. [Layer 1 — HTTP Request Validation](#3-layer-1--http-request-validation)
4. [Layer 2 — Identity & AD Authentication Validation](#4-layer-2--identity--ad-authentication-validation)
5. [Layer 3 — CSR Cryptographic Validation](#5-layer-3--csr-cryptographic-validation)
6. [Layer 4 — CSR Policy Validation](#6-layer-4--csr-policy-validation)
7. [Layer 5 — Duplicate Detection](#7-layer-5--duplicate-detection)
8. [Layer 6 — Workflow & Business Rule Validation](#8-layer-6--workflow--business-rule-validation)
9. [Layer 7 — CA Submission Validation](#9-layer-7--ca-submission-validation)
10. [Master Error Code Reference](#10-master-error-code-reference)
11. [Validation Testing Guide](#11-validation-testing-guide)

---

## 1. Overview & Philosophy

### What is an Enterprise RA?

A Registration Authority (RA) sits between certificate requestors and the
Certificate Authority (CA). It does **not** issue certificates — it validates,
approves, and forwards requests. The RA's job is to ensure that only
correct, authorized, policy-compliant CSRs ever reach the CA.

```
  User (AD-authenticated)
         │
         │  POST /api/ra/requests
         ▼
  ┌─────────────────────────────┐
  │   Enterprise RA (pki-ra)    │
  │                             │
  │   Layer 1: HTTP             │
  │   Layer 2: Identity (AD)    │
  │   Layer 3: CSR Crypto       │
  │   Layer 4: CSR Policy       │
  │   Layer 5: Duplicate Check  │
  │   Layer 6: Workflow Rules   │
  │   Layer 7: CA Pre-Submit    │
  └─────────────────────────────┘
         │
         │  Approved request only
         ▼
  Certificate Authority (CA)
         │
         ▼
  Issued Certificate → User
```

### Core Principles

| Principle | Meaning |
|-----------|---------|
| **Defense in Depth** | Multiple independent layers — one bypass doesn't compromise the system |
| **Fail Loud** | Every validation failure returns a coded error. Nothing fails silently. |
| **Collect All Errors** | Do not stop at first failure — return all problems in one response |
| **Audit Everything** | Every validation attempt (pass or fail) is written to the audit log |
| **Config-Driven Policy** | Key thresholds (key size, allowed algorithms) are in `ra_config` table — no code change needed to tighten policy |
| **No Trust at Boundary** | Even authenticated AD users get their CSR fully validated — AD auth proves identity, not CSR correctness |

### When Each Layer Runs

```
POST /api/ra/requests
  │
  ├─► Layer 1: HTTP (Spring MVC @Valid, size, content-type)       [before service]
  ├─► Layer 2: Identity (JWT/AD token, LDAP lookup, role check)  [security filter]
  ├─► Layer 3: CSR Crypto (parse, signature, algo, key size)      [CsrValidatorService]
  ├─► Layer 4: CSR Policy (DN, SAN, extensions, profile match)    [CsrValidatorService]
  ├─► Layer 5: Duplicate (hash check, time window)                [CsrSubmitService]
  │
  [saved to DB as SUBMITTED]
  │
  ├─► Layer 6: Workflow (status transitions, maker-checker rules) [ApprovalWorkflowService]
  │
  [APPROVED → sent to CA]
  │
  └─► Layer 7: CA Pre-Submit (payload, endpoint, timeout)         [CaIntegrationService]
```

---

## 2. Validation Architecture

### Component Map

```
raservice/
└── csr/
    ├── validation/
    │   ├── CsrValidatorService.java      ← Layers 3 + 4 (crypto + policy)
    │   ├── CsrValidationResult.java      ← Result DTO (errors, warnings, metadata)
    │   └── CsrValidationException.java   ← HTTP 422 exception
    ├── ApprovalWorkflowService.java      ← Layer 6 (workflow rules)
    └── CaIntegrationService.java         ← Layer 7 (CA pre-submit)

common/
└── exception/
    ├── PkiRaException.java               ← Base exception (status + message)
    ├── AppException.java                 ← Coded exception (error_catalog lookup)
    └── ExceptionFactory.java             ← Creates AppException from RaErrorCode

raservice/
└── error/
    └── RaErrorCode.java                  ← All error code constants (enum)

raservice/
└── config/
    └── RaSecurityConfig.java             ← Spring Security (JWT filter, role mapping)
```

### Validation Result Flow

```
                       ┌──────────────────┐
                       │ CsrValidatorSvc  │
                       │   .validate()    │
                       └────────┬─────────┘
                                │
                    ┌───────────▼───────────┐
                    │  CsrValidationResult  │
                    │  ─────────────────    │
                    │  passed: true/false   │
                    │  errors: List<String> │
                    │  warnings: List<Str>  │
                    │  subjectDn: String    │
                    │  keyAlgorithm: String │
                    │  keySize: int         │
                    │  signatureAlgorithm   │
                    │  subjectAltNames      │
                    │  commonName           │
                    └───────────┬───────────┘
                                │
                   ┌────────────┴────────────┐
                   │                         │
               passed=true             passed=false
                   │                         │
                   ▼                         ▼
          entity.status =           entity.status =
            SUBMITTED             VALIDATION_FAILED
                   │                         │
                   ▼                         ▼
           csrRepo.save()          csrRepo.save()
                   │               throw CsrValidationException
                   ▼                    → HTTP 422
           return CsrRequestDto
```

---

## 3. Layer 1 — HTTP Request Validation

**Who:** Spring MVC framework + `GlobalExceptionHandler`  
**When:** Before service layer is invoked  
**Purpose:** Reject malformed HTTP requests immediately — cheap, before any crypto work

### 3.1 Checks Performed

#### 3.1.1 JSON Structure & Required Fields

Jakarta Bean Validation (`@Valid`) on the controller parameter validates:

| Field | Constraint | Error if violated |
|-------|-----------|-------------------|
| `pkcs10` | `@NotBlank` | PKI_VAL_001 |
| `clientTxnId` | `@NotBlank`, max 100 chars | PKI_VAL_001 |
| `profile` | `@NotNull`, must be valid `CsrProfile` enum | PKI_VAL_001 |
| `validityDays` | `@Min(1)`, `@Max(3650)` if present | PKI_VAL_001 |

```java
// DTO with validation annotations
public class CsrSubmitRequest {

    @NotBlank(message = "pkcs10 CSR is required")
    private String pkcs10;

    @NotBlank(message = "clientTxnId is required")
    @Size(max = 100)
    private String clientTxnId;

    @NotNull(message = "profile is required")
    private CsrProfile profile;

    @Min(1) @Max(3650)
    private Integer validityDays;
}
```

**Failure:** `MethodArgumentNotValidException` → `GlobalExceptionHandler` → HTTP 400

#### 3.1.2 Content-Type

Spring MVC requires `Content-Type: application/json`. Missing or wrong type returns HTTP 415 Unsupported Media Type.

#### 3.1.3 CSR Payload Size Guard

Before parsing, raw PEM string length is checked against max size (default 8 KB):

```java
private static final int MAX_CSR_BYTES = 8192;

if (csrPem.length() > MAX_CSR_BYTES) {
    throw new CsrValidationException(List.of(
        "[PKI_VAL_003] CSR payload exceeds maximum size of " + MAX_CSR_BYTES + " bytes"
    ));
}
```

**Why:** PKCS#10 parse + signature verify are CPU-intensive. A 100 MB malicious payload would exhaust server resources before the parse even starts.

#### 3.1.4 PEM Header Check

```java
if (!trimmed.startsWith("-----BEGIN CERTIFICATE REQUEST-----")) {
    result.addError("PKI_VAL_002",
        "CSR must begin with '-----BEGIN CERTIFICATE REQUEST-----'. " +
        "Received content starts with: " + trimmed.substring(0, Math.min(40, trimmed.length())));
}
```

### 3.2 HTTP Status Codes for Layer 1

| Scenario | HTTP Status | Error Code |
|----------|-------------|------------|
| Missing required field | 400 Bad Request | PKI_VAL_001 |
| Wrong Content-Type | 415 Unsupported Media Type | — |
| CSR too large | 400 Bad Request | PKI_VAL_003 |
| Wrong PEM header | 400 Bad Request | PKI_VAL_002 |
| Auth token missing | 401 Unauthorized | PKI_AUTH_001 |

---

## 4. Layer 2 — Identity & AD Authentication Validation

**Who:** Spring Security filter chain (`RaSecurityConfig`) + AD/LDAP  
**When:** After HTTP, before service layer  
**Purpose:** Verify the caller is who they say they are, and that they are
allowed to submit CSRs

### 4.1 Authentication Flow (AD-based, no internal password DB)

```
Client sends:
  Authorization: Bearer <JWT>
         │
         ▼
  Spring Security JwtAuthenticationFilter
         │
         ├─ Validate JWT signature (RS256, using AD JWKS endpoint)
         ├─ Validate exp, iss, aud claims
         ├─ Extract username (sub claim) and AD groups (groups claim)
         │
         ▼
  RaSecurityConfig: map AD groups → Spring roles
         │
         │  e.g.:
         │  AD group "PKI_CERT_REQUESTORS"  → ROLE_REQUESTOR
         │  AD group "PKI_RA_OPERATORS"     → ROLE_OPERATOR
         │  AD group "PKI_RA_ADMINS"        → ROLE_ADMIN
         │
         ▼
  SecurityContextHolder populated
         │
         ▼
  Controller method reached — authentication confirmed
```

### 4.2 Authorization Checks

| Endpoint | Required Role | AD Group |
|----------|--------------|----------|
| `POST /api/ra/requests` (submit CSR) | ROLE_REQUESTOR | PKI_CERT_REQUESTORS |
| `POST /api/ra/csr/{id}/pickup` | ROLE_OPERATOR | PKI_RA_OPERATORS |
| `POST /api/ra/csr/{id}/approve` | ROLE_OPERATOR | PKI_RA_OPERATORS |
| `POST /api/ra/csr/{id}/accept` | ROLE_OPERATOR | PKI_RA_OPERATORS |
| `POST /api/ra/csr/{id}/assign` | ROLE_ADMIN | PKI_RA_ADMINS |
| `POST /api/ra/csr/{id}/close` | ROLE_ADMIN | PKI_RA_ADMINS |
| `GET /api/admin/**` | ROLE_ADMIN | PKI_RA_ADMINS |

### 4.3 Identity Validation Checks

After authentication, the service layer performs additional identity checks:

#### 4.3.1 User Exists in RA Database

The JWT proves AD identity, but the RA also maintains a local user table
(populated on first login via AD sync). The user must exist locally:

```java
private User findUser(String username) {
    return userRepo.findByUsername(username)
        .orElseThrow(() -> exceptionFactory.create(RaErrorCode.AUTH_FAILED, username));
}
```

**Why:** The RA needs local user records to link CSRs to requestors, track
assignments, and enforce separation of duties. AD is the source of truth
for authentication; the RA DB is the source of truth for workflow state.

#### 4.3.2 Requester Cannot Be Operator (Separation of Duties)

A user who submitted a CSR cannot also be the operator who approves it:

```java
private void requireNotRequester(CsrRequest request, User operator) {
    if (request.getRequestorUser() != null &&
            request.getRequestorUser().getId().equals(operator.getId())) {
        throw exceptionFactory.create(RaErrorCode.ACCESS_DENIED,
            "Requester cannot be Maker or Checker");
    }
}
```

**Error:** PKI_AUTH_002 — HTTP 403

#### 4.3.3 Maker Cannot Be Checker (DUAL mode)

In DUAL approval mode, the operator who reviewed (Maker) cannot also accept (Checker):

```java
private void requireNotMaker(CsrRequest request, User checker) {
    if (request.getAssignedTo() != null &&
            request.getAssignedTo().getId().equals(checker.getId())) {
        throw exceptionFactory.create(RaErrorCode.ACCESS_DENIED,
            "The operator who reviewed cannot accept. A different operator must be Checker.");
    }
}
```

**Error:** PKI_APR_005 — HTTP 403

### 4.4 AD Validation Failures

| Check | Exception | Error Code | HTTP |
|-------|-----------|------------|------|
| JWT missing | AuthenticationException | PKI_AUTH_001 | 401 |
| JWT expired | AuthenticationException | PKI_AUTH_003 | 401 |
| JWT invalid signature | AuthenticationException | PKI_AUTH_001 | 401 |
| User not in required AD group | AccessDeniedException | PKI_AUTH_002 | 403 |
| User not found in RA DB | AppException | PKI_AUTH_001 | 401 |
| Requester = Operator | AppException | PKI_AUTH_002 | 403 |
| Maker = Checker | AppException | PKI_APR_005 | 403 |

---

## 5. Layer 3 — CSR Cryptographic Validation

**Who:** `CsrValidatorService` (Layers 1–2 inside `validate()`)  
**When:** Immediately after HTTP + auth pass, before persisting to DB  
**Purpose:** Verify the CSR is cryptographically sound — not tampered,
not using weak crypto, key is strong enough

### 5.1 Stage 1 — Parse (PEM / DER Decode)

```
Input: raw String from request body
         │
         ├─ starts with "-----" ?
         │       YES → PEMParser (Bouncy Castle)
         │       NO  → Base64 decode → DER bytes → PKCS10CertificationRequest
         │
         ▼
  PKCS10CertificationRequest object
  (or addError PKI_VAL_002 if fails)
```

**What BC parses:**
- Outer SEQUENCE: `CertificationRequest`
- Inner SEQUENCE: `CertificationRequestInfo` (version + subject + SPKI + attrs)
- `signatureAlgorithm` AlgorithmIdentifier
- `signature` BIT STRING

**Implementation:**
```java
try (PEMParser parser = new PEMParser(new StringReader(input.strip()))) {
    Object obj = parser.readObject();
    if (!(obj instanceof PKCS10CertificationRequest)) {
        result.addError("PKI_VAL_002", "PEM block is not a PKCS#10 CSR");
        return null;
    }
    return (PKCS10CertificationRequest) obj;
} catch (Exception ex) {
    result.addError("PKI_VAL_002", "CSR parse failure: " + ex.getMessage());
    return null;
}
```

**On failure:** Returns `null` immediately. All subsequent layers are skipped.
No half-parsed state is used.

---

### 5.2 Stage 2 — Proof of Possession (Self-Signature Verification)

This is the **most critical** cryptographic check in the entire pipeline.

**What it verifies:**
The CSR contains a `signature` field — computed by the submitter using their
private key over the `CertificationRequestInfo` bytes (DER-encoded). The RA
verifies this signature using the **public key embedded in the same CSR**.

If the signature is valid → the submitter provably holds the private key.
If invalid → the public key was copied from elsewhere, or the CSR was tampered.

```
CertificationRequestInfo bytes (DER)
         │
         ▼
  SHA-256 hash
         │
         ▼
  RSA_Verify / ECDSA_Verify / Ed25519_Verify
  using SubjectPublicKeyInfo from the same CSR
         │
         ├─ VALID   → submitter holds private key  ✓
         └─ INVALID → key mismatch / tampered      ✗  PKI_CRYPTO_001
```

**Implementation (Bouncy Castle):**
```java
ContentVerifierProvider verifier =
    new JcaContentVerifierProviderBuilder()
        .setProvider(BouncyCastleProvider.PROVIDER_NAME)
        .build(csr.getSubjectPublicKeyInfo());   // use public key FROM the CSR

if (!csr.isSignatureValid(verifier)) {
    result.addError("PKI_CRYPTO_001",
        "CSR self-signature verification failed — private key mismatch or CSR tampered");
}
```

**Why `JcaContentVerifierProviderBuilder` + `isSignatureValid()`:**
- BC handles all algorithm OID resolution internally
- Works for RSA, ECDSA, Ed25519, Ed448 — same code
- Thread-safe; no shared state

---

### 5.3 Stage 3 — Signature Algorithm Enforcement

**Blocked algorithms (always rejected):**

| Algorithm | OID | Why Blocked |
|-----------|-----|-------------|
| MD2withRSA | 1.2.840.113549.1.1.2 | MD2 broken since 1995 |
| MD5withRSA | 1.2.840.113549.1.1.4 | MD5 collision attacks since 2004 |
| SHA1withRSA | 1.2.840.113549.1.1.5 | SHA-1 deprecated by CA/Browser Forum 2017 |
| SHA1withECDSA | 1.2.840.10045.4.3.1 | Same — SHA-1 forbidden |
| SHA1withDSA | 1.2.840.10040.4.3 | DSA + SHA-1 both deprecated |

**Allowed algorithms:**

| Algorithm | OID | Notes |
|-----------|-----|-------|
| SHA256withRSA | 1.2.840.113549.1.1.11 | Standard RSA — recommended |
| SHA384withRSA | 1.2.840.113549.1.1.12 | Higher security margin |
| SHA512withRSA | 1.2.840.113549.1.1.13 | Maximum RSA security |
| SHA256withECDSA | 1.2.840.10045.4.3.2 | EC — recommended |
| SHA384withECDSA | 1.2.840.10045.4.3.3 | EC high security |
| SHA512withECDSA | 1.2.840.10045.4.3.4 | EC maximum |
| Ed25519 | 1.3.101.112 | Always SHA-512 internally — no OID choice |
| Ed448 | 1.3.101.113 | Same — always safe |

---

### 5.4 Stage 4 — Key Strength Validation

**RSA minimum: 2048 bits**

| Key Size | Status | Reason |
|----------|--------|--------|
| 512 bits | REJECTED | Trivially factorable with commodity hardware |
| 1024 bits | REJECTED | NIST deprecated 2010; broken by GNFS |
| 2048 bits | ACCEPTED | Minimum — safe until ~2030 per NIST SP 800-131A |
| 3072 bits | ACCEPTED | Recommended for new certs after 2028 |
| 4096 bits | ACCEPTED | High security; slightly slower TLS handshake |

**EC minimum: P-256 (256-bit field)**

| Curve | OID | Status |
|-------|-----|--------|
| secp192r1 (P-192) | 1.2.840.10045.3.1.1 | REJECTED — too small |
| secp224r1 (P-224) | 1.3.132.0.33 | REJECTED — below minimum |
| secp256r1 (P-256) | 1.2.840.10045.3.1.7 | ACCEPTED — minimum |
| secp384r1 (P-384) | 1.3.132.0.34 | ACCEPTED — recommended |
| secp521r1 (P-521) | 1.3.132.0.35 | ACCEPTED — maximum standard |
| brainpool / non-standard | various | REJECTED — CA compatibility risk |

**Ed25519/Ed448:** Always accepted — fixed 256/448-bit equivalent security.

**Implementation:**
```java
java.security.PublicKey pk = java.security.KeyFactory
    .getInstance(jcaAlg, BouncyCastleProvider.PROVIDER_NAME)
    .generatePublic(new X509EncodedKeySpec(spki.getEncoded()));

if (pk instanceof RSAPublicKey rsa) {
    int bits = rsa.getModulus().bitLength();
    if (bits < 2048)
        result.addError("PKI_KEY_001",
            "RSA " + bits + " bits below minimum 2048 bits");
}
if (pk instanceof ECPublicKey ec) {
    int bits = ec.getParams().getCurve().getField().getFieldSize();
    if (bits < 256)
        result.addError("PKI_KEY_001",
            "EC " + bits + " bits below P-256 minimum");
}
```

---

## 6. Layer 4 — CSR Policy Validation

**Who:** `CsrValidatorService` (Layers 3–5 inside `validate()`)  
**When:** After crypto validation passes  
**Purpose:** Ensure the CSR content matches the requested certificate profile
and enterprise policy — not just "is it valid crypto" but "is it the right
kind of request"

### 6.1 Stage 5 — Subject Distinguished Name (DN) Validation

#### 6.1.1 CN Must Be Present

```java
RDN[] cnRdns = subject.getRDNs(BCStyle.CN);
if (cnRdns == null || cnRdns.length == 0)
    result.addError("PKI_POL_001", "CN missing from Subject DN");
```

#### 6.1.2 Per-Profile CN Rules

| Profile | CN Rule | Example Valid | Example Invalid |
|---------|---------|---------------|-----------------|
| TLS_SERVER | Must be valid FQDN. No spaces. | `api.acme.com` | `John Doe`, `acme`, `192.168.1.1` |
| TLS_CLIENT | Person name or service name. Spaces OK. | `John Doe`, `svc-payments` | (max length 128 chars) |
| SMIME | Person's full display name. | `John Doe` | (IP address not allowed) |
| CODE_SIGNING | Organization or developer name. No IP. | `Acme Corp`, `John Doe` | `192.168.1.1` |
| DOCUMENT_SIGNING | Person or organization name. | `Acme Corp Legal` | — |

#### 6.1.3 FQDN Validation (TLS_SERVER CN)

```
FQDN must satisfy ALL of:
  ✓ Total length: 1–253 characters
  ✓ Labels (parts between dots): 1–63 characters each
  ✓ Labels: only [a-zA-Z0-9-]
  ✓ Labels: must not start or end with hyphen
  ✓ At least 2 labels (hostname.tld minimum)
  ✓ TLD (last label): letters only, 2–63 chars (no all-numeric TLD)
  ✓ Wildcard: only *.hostname.tld — single leading asterisk in leftmost label
  ✗ IP addresses: rejected (use iPAddress SAN instead)
  ✗ Spaces: rejected
  ✗ Consecutive dots: rejected
  ✗ Reserved names (localhost): rejected
```

**Regex pattern:**
```java
private static final Pattern FQDN_PATTERN = Pattern.compile(
    "^(?=.{1,253}$)" +
    "((?!-)[a-zA-Z0-9-]{1,63}(?<!-)\\.)+" +
    "(?!-)[a-zA-Z]{2,63}$"
);
private static final Pattern WILDCARD_FQDN = Pattern.compile(
    "^\\*\\.((?!-)[a-zA-Z0-9-]{1,63}(?<!-)\\.)+(?!-)[a-zA-Z]{2,63}$"
);
```

**Test cases:**

| Input | Valid? | Reason |
|-------|--------|--------|
| `api.acme.com` | YES | Standard FQDN |
| `*.acme.com` | YES | Wildcard |
| `api-v2.acme.com` | YES | Hyphen in label |
| `John Doe` | NO | Spaces |
| `acme` | NO | Single label — no dot |
| `api..acme.com` | NO | Consecutive dots |
| `-api.acme.com` | NO | Label starts with hyphen |
| `192.168.1.1` | NO | IP address |
| `localhost` | NO | Reserved name |
| `api.acme.123` | NO | TLD is numeric |

---

### 6.2 Stage 6 — Subject Alternative Name (SAN) Validation

#### 6.2.1 Why SAN Matters (RFC 2818, RFC 5280)

Since 2017, all major browsers and TLS stacks **ignore the CN** and only
use SAN for hostname matching. A TLS server certificate without a SAN
`dNSName` will be rejected by Chrome, Firefox, Edge, Safari — regardless
of what the CN says.

#### 6.2.2 Per-Profile SAN Requirements

| Profile | SAN Requirement |
|---------|----------------|
| TLS_SERVER | `dNSName` MUST be present. FQDN-validated. |
| TLS_CLIENT | `rfc822Name` (email) or `otherName` (UPN) OPTIONAL |
| SMIME | `rfc822Name` MUST be present |
| CODE_SIGNING | SAN usually absent — OPTIONAL |
| DOCUMENT_SIGNING | SAN absent or optional |

#### 6.2.3 dNSName Validation Rules

Each `dNSName` in SAN is independently validated as a valid FQDN or
wildcard FQDN (same rules as CN for TLS_SERVER, see §6.1.3).

**Additional wildcard rules:**
- `*.acme.com` — valid (single level)
- `*.*.acme.com` — REJECTED (multi-level wildcard, not RFC 5280 compliant)
- `*acme.com` — REJECTED (wildcard not in leftmost label only)

#### 6.2.4 rfc822Name (Email) Format Check

Email SANs must be in the form `localpart@domain.tld`:
```java
if (!value.contains("@") || !value.contains("."))
    result.addWarning("PKI_POL_020",
        "rfc822Name '" + value + "' does not look like a valid email address");
```

---

### 6.3 Stage 7 — Extensions Validation

#### 6.3.1 Basic Constraints

| Value | Allowed in CSR? | Reason |
|-------|----------------|--------|
| Absent | YES | CA applies cA=FALSE from template |
| `cA=FALSE` | YES | Explicit end-entity declaration |
| `cA=TRUE` | **NEVER** | Would allow cert holder to sign other certs |
| `cA=TRUE, pathLen=0` | **NEVER** | Same — CA bit is always forbidden |

```java
if (bc.isCA())
    result.addError("PKI_POL_008",
        "BasicConstraints cA=TRUE is forbidden in end-entity CSR. " +
        "This CSR is attempting to obtain a CA certificate.");
```

#### 6.3.2 Key Usage Forbidden Bits

| Bit | Allowed? | Reason |
|-----|----------|--------|
| `digitalSignature` | YES | Normal end-entity use |
| `nonRepudiation` | YES | S/MIME, document signing |
| `keyEncipherment` | YES | RSA key exchange |
| `keyAgreement` | YES | EC key exchange |
| `dataEncipherment` | YES | Data encryption (rare) |
| `keyCertSign` | **NEVER** | CA-only — signs certificates |
| `cRLSign` | **NEVER** | CA-only — signs CRL |
| `encipherOnly` | Conditional | Only meaningful with keyAgreement |
| `decipherOnly` | Conditional | Only meaningful with keyAgreement |

#### 6.3.3 Extended Key Usage Checks

| OID | Name | Allowed? |
|-----|------|----------|
| 1.3.6.1.5.5.7.3.1 | serverAuth | YES |
| 1.3.6.1.5.5.7.3.2 | clientAuth | YES |
| 1.3.6.1.5.5.7.3.3 | codeSigning | YES |
| 1.3.6.1.5.5.7.3.4 | emailProtection | YES |
| 1.3.6.1.5.5.7.3.9 | OCSPSigning | Restricted — RA admin must approve |
| 2.5.29.37.0 | **anyExtendedKeyUsage** | **NEVER** — effectively no EKU restriction |

#### 6.3.4 Profile ↔ Extension Cross-Check

| Profile | Expected Key Usage | Expected EKU |
|---------|--------------------|--------------|
| TLS_SERVER | digitalSignature + keyEncipherment(RSA) / keyAgreement(EC) | serverAuth |
| TLS_CLIENT | digitalSignature | clientAuth |
| SMIME | digitalSignature + nonRepudiation + keyEncipherment | emailProtection |
| CODE_SIGNING | digitalSignature | codeSigning |
| DOCUMENT_SIGNING | digitalSignature + nonRepudiation | — |

Mismatches produce **warnings** (not errors), because the CA template
can enforce EKU — RA cannot always know which subset the CA will apply.

---

## 7. Layer 5 — Duplicate Detection

**Who:** `CsrSubmitService` (before saving to DB)  
**When:** After Layers 3+4 pass — only valid CSRs are duplicate-checked  
**Purpose:** Prevent the same CSR from generating multiple pending requests

### 7.1 Why Duplicate Detection Matters

Without it:
- User accidentally double-clicks Submit → 2 identical requests in queue
- Client retry logic (network timeout) → same request submitted 3 times
- Malicious replay attack → flood the approval queue

### 7.2 CSR Hash Computation

```
1. Re-encode CSR to canonical DER bytes (Bouncy Castle)
   → strips PEM headers, normalizes encoding
   → eliminates differences in whitespace, line endings

2. SHA-256(DER bytes)
   → 32 bytes = 64-char lowercase hex string

3. Store in csr_requests.csr_hash
```

```java
private String computeCsrHash(PKCS10CertificationRequest csr) {
    try {
        byte[] der = csr.getEncoded();  // canonical DER
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        byte[] hash = sha256.digest(der);
        return HexFormat.of().formatHex(hash);   // Java 17+ HexFormat
    } catch (Exception ex) {
        throw new CsrValidationException(List.of(
            "[PKI_VAL_004] Failed to compute CSR hash: " + ex.getMessage()
        ));
    }
}
```

**Why canonical DER (not raw PEM):**
Two identical CSRs submitted by the same user — one with Windows line endings
(`\r\n`), one with Unix (`\n`) — produce different PEM strings but identical
DER encoding. Hashing DER prevents trivial bypass by reformatting PEM.

### 7.3 Duplicate Window Check

```java
Instant windowStart = Instant.now().minus(duplicateWindowHours, ChronoUnit.HOURS);

Optional<CsrRequest> existing = csrRepo
    .findByHashAndCreatedAtAfter(csrHash, windowStart);

if (existing.isPresent()) {
    throw exceptionFactory.create(RaErrorCode.APR_ALREADY_PICKED_UP,
        "Duplicate CSR detected. Existing request ID: " +
        existing.get().getRequestId() +
        " submitted at " + existing.get().getCreatedAt());
}
// → HTTP 409 Conflict
```

**Configuration:**
```yaml
# ra_config table
csr.duplicate.window.hours = 24     # default: check last 24 hours
```

### 7.4 Duplicate Check Scope

The hash check covers **all non-terminal statuses**:

```sql
SELECT * FROM csr_requests
WHERE csr_hash = :hash
  AND status NOT IN ('CLOSED', 'ISSUED', 'FAILED')
  AND created_at > :windowStart
LIMIT 1;
```

Terminal statuses (`CLOSED`, `ISSUED`, `FAILED`) are excluded — if a
previous request for the same key/subject was closed, the user is allowed
to submit a new one.

### 7.5 clientTxnId Uniqueness (Separate from Hash)

The `clientTxnId` sent by the client is a separate uniqueness key — it must
be globally unique across all requests (no time window):

```java
if (csrRepo.existsByClientTxnId(request.getClientTxnId())) {
    throw exceptionFactory.create(RaErrorCode.VALIDATION_INVALID_CSR,
        "clientTxnId '" + request.getClientTxnId() + "' already used");
}
// → HTTP 422
```

**Difference:** CSR hash checks for duplicate *certificate content*.
`clientTxnId` checks for duplicate *client transaction IDs*. Both are
enforced independently.

---

## 8. Layer 6 — Workflow & Business Rule Validation

**Who:** `ApprovalWorkflowService`  
**When:** During every approval-workflow action (pickup, review, approve, accept, reject, return, close)  
**Purpose:** Enforce the maker-checker model, status machine, and separation of duties

### 8.1 Status Machine

A `CsrRequest` has exactly one `CsrStatus` at any moment. Transitions are
strictly controlled — no arbitrary jumps allowed.

```
RECEIVED
    │
    ├─► VALIDATION_FAILED  (if validation fails — terminal for this submission)
    │
    └─► SUBMITTED
            │
            ├─► IN_REVIEW  (operator picks up or admin assigns)
            │       │
            │       ├─► RETURNED (operator returns to admin — re-enters SUBMITTED pool)
            │       │
            │       ├─► REJECTED (SINGLE mode: operator rejects directly)
            │       │       └─► CLOSED (admin closes permanently)
            │       │
            │       └─► REVIEWED (DUAL mode: Maker submits review)
            │               │
            │               ├─► REJECTED (Checker rejects)
            │               │       └─► CLOSED
            │               │
            │               └─► APPROVED (Checker accepts)
            │
            └─► APPROVED
                    │
                    └─► SENT_TO_CA
                            │
                            ├─► ISSUED   (CA callback: success)
                            ├─► FAILED   (CA callback: failure)
                            └─► PENDING  (CA: async, waiting)
```

### 8.2 Status Transition Validation

Every action first checks the current status is valid for that action:

```java
private void requireStatus(CsrRequest request, CsrStatus expected) {
    if (request.getStatus() != expected) {
        throw exceptionFactory.create(RaErrorCode.APR_INVALID_STATUS,
            "Expected status " + expected + " but found " + request.getStatus());
    }
}
```

| Action | Allowed Current Status | Resulting Status |
|--------|----------------------|-----------------|
| `pickup` | SUBMITTED | IN_REVIEW |
| `assign` (admin) | SUBMITTED, RETURNED, REJECTED | IN_REVIEW |
| `approve` (SINGLE) | IN_REVIEW | APPROVED |
| `reject` (SINGLE) | IN_REVIEW | REJECTED |
| `review` (DUAL Maker) | IN_REVIEW | REVIEWED |
| `accept` (DUAL Checker) | REVIEWED | APPROVED |
| `rejectByChecker` | REVIEWED | REJECTED |
| `returnToAdmin` | IN_REVIEW | RETURNED |
| `close` (admin) | RETURNED, REJECTED | CLOSED |

### 8.3 Approval Mode Validation (SINGLE vs DUAL)

The approval mode is determined at **pickup time** from the `ApprovalMatrix`
for the CSR's profile. Once set (`approvalModeAtPickup`), config changes
do not affect the in-flight request.

```java
// Locked at pickup
request.setApprovalModeAtPickup(matrixService.getApprovalMode(request.getCsrProfile()));
```

**SINGLE mode:** One operator can approve or reject directly.
**DUAL mode:** One Maker reviews, a *different* Checker accepts or rejects.

```java
private void requireSingleMode(Long requestId) {
    if (resolveMode(request) == ApprovalMode.DUAL) {
        throw exceptionFactory.create(RaErrorCode.APR_MODE_BLOCKED,
            "DUAL mode active — use /review first, then Checker /accept");
    }
}

private void requireDualMode(Long requestId) {
    if (resolveMode(request) == ApprovalMode.SINGLE) {
        throw exceptionFactory.create(RaErrorCode.APR_MODE_BLOCKED,
            "SINGLE mode active — use /approve or /reject directly");
    }
}
```

### 8.4 Separation of Duties Checks

| Rule | Enforced By | Error Code |
|------|-------------|------------|
| Requester ≠ Maker/Checker | `requireNotRequester()` | PKI_APR_006 |
| Maker ≠ Checker (DUAL mode) | `requireNotMaker()` | PKI_APR_005 |
| Only assigned operator can act | `requireAssignedOperator()` | PKI_APR_004 |
| Only admin can assign/close | `@PreAuthorize("ROLE_ADMIN")` | PKI_APR_003 |

### 8.5 Remarks Validation

Remarks are required (configurable). Minimum length is per-profile:

```java
private void requireRemarks(CsrRequest request, String remarks) {
    int minLength = matrixService.getMinRemarksLength(request.getCsrProfile());

    if (workflowConfig.isRequireRemarks() &&
            (remarks == null || remarks.isBlank())) {
        throw exceptionFactory.create(RaErrorCode.APR_REMARKS_REQUIRED,
            "Remarks are required for this action");
    }
    if (minLength > 0 && remarks != null && remarks.length() < minLength) {
        throw exceptionFactory.create(RaErrorCode.APR_REMARKS_REQUIRED,
            "Remarks must be at least " + minLength + " characters");
    }
}
```

**Configuration (ra_config table):**
```
workflow.require.remarks = true
workflow.min.remarks.length.TLS_SERVER = 20
workflow.min.remarks.length.CODE_SIGNING = 50
```

### 8.6 Workflow Validation Error Summary

| Scenario | Error Code | HTTP |
|----------|------------|------|
| Wrong current status for action | PKI_APR_001 | 409 |
| Requester tries to approve own request | PKI_APR_006 | 403 |
| Maker tries to be Checker | PKI_APR_005 | 403 |
| Non-assigned operator tries to act | PKI_APR_004 | 403 |
| Non-admin tries admin-only action | PKI_APR_003 | 403 |
| Wrong approval mode (SINGLE vs DUAL) | PKI_APR_012 | 422 |
| Remarks missing when required | PKI_APR_008 | 422 |
| Remarks too short | PKI_APR_008 | 422 |
| Request already picked up | PKI_APR_009 | 409 |
| Return without reason | PKI_APR_010 | 422 |

---

## 9. Layer 7 — CA Submission Validation

**Who:** `CaIntegrationService`  
**When:** After APPROVED — before sending payload to the CA REST endpoint  
**Purpose:** Ensure the CA receives a well-formed request and handle failures gracefully

### 9.1 Pre-Submission Checks

Before posting to the CA:

```java
// 1. CSR PEM still present and non-blank
if (request.getCsrPem() == null || request.getCsrPem().isBlank()) {
    transition(request, CsrStatus.FAILED, "CSR PEM missing on approved request");
    return;
}

// 2. Request is in correct status
if (request.getStatus() != CsrStatus.APPROVED) {
    log.error("CA submission called on non-APPROVED request: {}", request.getRequestId());
    return;
}

// 3. Not already sent
if (request.getSentToCaAt() != null) {
    log.warn("Request {} already sent to CA at {}", request.getRequestId(), request.getSentToCaAt());
    return;
}
```

### 9.2 CA Payload Validation

The payload sent to the CA is validated before the HTTP call:

```java
CaSubmissionRequest payload = CaSubmissionRequest.builder()
    .raRequestId(request.getRequestId())
    .pkcs10(request.getCsrPem())
    .profile(request.getCsrProfile().name())
    .validityDays(request.getRequestedValidityDays())
    .subjectAltNames(request.getSubjectAltNames())
    .callbackUrl(caProperties.getCallbackUrl() + "/" + request.getRequestId())
    .build();

// Validity days must be in CA-allowed range
if (payload.getValidityDays() != null &&
        payload.getValidityDays() > caProperties.getMaxValidityDays()) {
    payload.setValidityDays(caProperties.getMaxValidityDays());
    log.warn("Requested validity {} days capped to CA max {}",
        payload.getValidityDays(), caProperties.getMaxValidityDays());
}
```

### 9.3 CA Response Handling & Status Transitions

```
CA Response
    │
    ├─ HTTP 200/201 + certificate PEM → ISSUED
    │       └─ store certificate, update serial, notify user
    │
    ├─ HTTP 200 + status=PENDING → PENDING (async)
    │       └─ wait for CA callback webhook
    │
    ├─ HTTP 4xx (bad request from CA)  → FAILED
    │       └─ log CA error, store reason, alert RA admin
    │
    ├─ HTTP 5xx (CA server error)      → FAILED (retryable)
    │       └─ exponential backoff retry (max 3 attempts)
    │
    └─ Connection timeout              → FAILED (retryable)
            └─ alert: PKI_NET_001
```

### 9.4 CA Callback Validation (`/api/ra/ca/callback`)

When the CA calls back with the issued certificate:

```java
@PostMapping("/api/ra/ca/callback/{requestId}")
public ResponseEntity<Void> caCallback(
        @PathVariable String requestId,
        @RequestBody CaCallbackRequest body) {

    // 1. requestId must exist
    CsrRequest req = csrRepo.findByRequestId(requestId)
        .orElseThrow(() -> new ResourceNotFoundException("Request not found: " + requestId));

    // 2. Must be in SENT_TO_CA or PENDING status
    if (req.getStatus() != CsrStatus.SENT_TO_CA && req.getStatus() != CsrStatus.PENDING) {
        log.warn("CA callback for request {} in unexpected status {}", requestId, req.getStatus());
        return ResponseEntity.ok().build();  // Idempotent — don't error on duplicates
    }

    // 3. caTransactionId must match
    if (!req.getCaTransactionId().equals(body.getCaTransactionId())) {
        log.error("CA txn ID mismatch for {}: expected={} got={}",
            requestId, req.getCaTransactionId(), body.getCaTransactionId());
        return ResponseEntity.badRequest().build();
    }

    // 4. If certificate present — validate it's real PEM
    if (body.getCertificatePem() != null) {
        validateCertificatePem(body.getCertificatePem(), req);
    }

    // 5. Transition to ISSUED or FAILED
    handleCaCallback(req, body);
    return ResponseEntity.ok().build();
}
```

### 9.5 Issued Certificate Validation

When a certificate PEM arrives from the CA, the RA validates it before storing:

```java
private void validateCertificatePem(String pem, CsrRequest req) {
    try (PEMParser p = new PEMParser(new StringReader(pem))) {
        X509CertificateHolder holder = (X509CertificateHolder) p.readObject();

        // Subject must match the original CSR subject
        if (!holder.getSubject().equals(new X500Name(req.getSubjectDn()))) {
            log.warn("Certificate subject {} does not match CSR subject {}",
                holder.getSubject(), req.getSubjectDn());
        }

        // Must not be expired
        if (holder.getNotAfter().before(new Date())) {
            log.error("CA returned already-expired certificate for {}", req.getRequestId());
            transition(req, CsrStatus.FAILED, "CA returned expired certificate");
        }

        // Serial number must be present and non-zero
        if (holder.getSerialNumber().equals(BigInteger.ZERO)) {
            log.error("CA returned certificate with serial=0 for {}", req.getRequestId());
        }

    } catch (Exception ex) {
        log.error("CA returned invalid certificate PEM for {}: {}",
            req.getRequestId(), ex.getMessage());
        transition(req, CsrStatus.FAILED, "Invalid certificate PEM from CA");
    }
}
```

---

## 10. Master Error Code Reference

All error codes used across all 7 validation layers.

### 10.1 Authentication & Authorization (PKI_AUTH_*)

| Code | Layer | HTTP | Description |
|------|-------|------|-------------|
| PKI_AUTH_001 | 2 | 401 | Authentication failed (missing/invalid JWT, user not found) |
| PKI_AUTH_002 | 2 | 403 | Access denied (insufficient role, separation of duties) |
| PKI_AUTH_003 | 2 | 401 | Session/token expired |

### 10.2 Validation — Basic (PKI_VAL_*)

| Code | Layer | HTTP | Description |
|------|-------|------|-------------|
| PKI_VAL_001 | 1 | 400 | Required field missing or blank (JSON structure) |
| PKI_VAL_002 | 1/3 | 400 | CSR parse failure — malformed PEM, wrong header, bad ASN.1 |
| PKI_VAL_003 | 1 | 400 | CSR payload exceeds maximum size |
| PKI_VAL_004 | 5 | 422 | CSR hash computation failed |

### 10.3 Cryptographic (PKI_CRYPTO_*)

| Code | Layer | HTTP | Description |
|------|-------|------|-------------|
| PKI_CRYPTO_001 | 3 | 422 | CSR self-signature invalid — key mismatch or tampered |
| PKI_CRYPTO_002 | 3 | 422 | Weak/forbidden signature algorithm (SHA1, MD5) |

### 10.4 Key Policy (PKI_KEY_*)

| Code | Layer | HTTP | Description |
|------|-------|------|-------------|
| PKI_KEY_001 | 3 | 422 | Key size below minimum (RSA<2048, EC<P-256) |
| PKI_KEY_002 | 3 | 422 | Key type not supported or size undetermined |

### 10.5 Certificate Policy (PKI_POL_*)

| Code | Layer | HTTP | Description |
|------|-------|------|-------------|
| PKI_POL_001 | 4 | 422 | CN missing from Subject DN |
| PKI_POL_004 | 4 | 422 | TLS_SERVER: no SAN or no dNSName in SAN |
| PKI_POL_005 | 4 | 422 | dNSName in SAN is not a valid FQDN |
| PKI_POL_006 | 4 | 422 | S/MIME: no rfc822Name in SAN |
| PKI_POL_007 | 4 | 200/WARN | No X.509v3 extensions in CSR (warning only) |
| PKI_POL_008 | 4 | 422 | BasicConstraints cA=TRUE (CA cert attempted) |
| PKI_POL_009 | 4 | 422 | KeyUsage bit keyCertSign present (CA-only) |
| PKI_POL_010 | 4 | 422 | KeyUsage bit cRLSign present (CA-only) |
| PKI_POL_011 | 4 | 200/WARN | Recommended KeyUsage bit missing for profile |
| PKI_POL_012 | 4 | 422 | EKU contains anyExtendedKeyUsage |
| PKI_POL_013 | 4 | 422 | TLS_SERVER CN contains spaces (person name, not FQDN) |
| PKI_POL_014 | 4 | 422 | TLS_SERVER CN is not a valid FQDN |
| PKI_POL_015 | 4 | 200/WARN | CN exceeds 128 characters |
| PKI_POL_016 | 4 | 422 | CODE_SIGNING CN is an IP address |
| PKI_POL_020 | 4 | 200/WARN | rfc822Name SAN format looks invalid |

### 10.6 Certificate Lifecycle (PKI_CERT_*)

| Code | Layer | HTTP | Description |
|------|-------|------|-------------|
| PKI_CERT_001 | — | 404 | Certificate / request not found |
| PKI_CERT_002 | — | 422 | Certificate expired |
| PKI_CERT_003 | — | 500 | Certificate revocation failed |
| PKI_CERT_004 | — | 409 | Certificate already revoked |
| PKI_CERT_005 | 7 | 500 | Certificate generation failed |

### 10.7 Approval Workflow (PKI_APR_*)

| Code | Layer | HTTP | Description |
|------|-------|------|-------------|
| PKI_APR_001 | 6 | 409 | Invalid status for this action |
| PKI_APR_002 | 6 | 403 | Self-processing (requester = operator) |
| PKI_APR_003 | 6 | 403 | Admin-only action |
| PKI_APR_004 | 6 | 403 | Not the assigned operator |
| PKI_APR_005 | 6 | 403 | Maker = Checker (separation of duties) |
| PKI_APR_006 | 6 | 403 | Requester cannot be Maker or Checker |
| PKI_APR_007 | 6 | 404 | Operator not found |
| PKI_APR_008 | 6 | 422 | Remarks missing or too short |
| PKI_APR_009 | 6 | 409 | Request already picked up |
| PKI_APR_010 | 6 | 422 | Return reason required |
| PKI_APR_011 | 6 | 422 | Invalid workflow configuration |
| PKI_APR_012 | 6 | 422 | Wrong approval mode (SINGLE vs DUAL) |
| PKI_APR_013 | 6 | 429 | Max pending requests reached |

### 10.8 Network & Integration (PKI_NET_*)

| Code | Layer | HTTP | Description |
|------|-------|------|-------------|
| PKI_NET_001 | 7 | 503 | CA unreachable (timeout, connection refused) |
| PKI_NET_002 | 2 | 503 | LDAP/AD unreachable |

### 10.9 System (PKI_SYS_*)

| Code | Layer | HTTP | Description |
|------|-------|------|-------------|
| PKI_SYS_001 | — | 500 | Unexpected system error |
| PKI_SYS_002 | — | 503 | Service unavailable |

### 10.10 Duplicate Detection (no dedicated prefix — uses PKI_APR_009)

| Scenario | Code | HTTP |
|----------|------|------|
| Duplicate CSR hash within window | PKI_APR_009 | 409 |
| Duplicate clientTxnId | PKI_VAL_002 | 422 |

---

## 11. Validation Testing Guide

### 11.1 Layer 1 — HTTP Tests

```java
@Test
void submit_missingPkcs10_returns400() {
    given()
        .body("{\"clientTxnId\":\"T001\",\"profile\":\"TLS_SERVER\"}")
        .contentType(ContentType.JSON)
        .when().post("/api/ra/requests")
        .then().statusCode(400)
        .body("errorCode", equalTo("PKI_VAL_001"));
}

@Test
void submit_oversizeCsr_returns400() {
    String hugeCsr = "A".repeat(9000);
    given()
        .body(Map.of("pkcs10", hugeCsr, "clientTxnId", "T002", "profile", "TLS_SERVER"))
        .contentType(ContentType.JSON)
        .when().post("/api/ra/requests")
        .then().statusCode(400)
        .body("errorCode", equalTo("PKI_VAL_003"));
}
```

### 11.2 Layer 3 — Crypto Tests

```java
@Test
void submit_tamperedCsr_failsSignatureCheck() {
    // Build a valid CSR then replace the signature bytes
    String tamperedPem = buildTamperedCsr();

    CsrValidationResult result =
        csrValidatorService.validate(tamperedPem, CsrProfile.TLS_SERVER);

    assertThat(result.isPassed()).isFalse();
    assertThat(result.getErrors())
        .anyMatch(e -> e.contains("PKI_CRYPTO_001"));
}

@Test
void submit_sha1Algorithm_isRejected() {
    String sha1Csr = generateCsrWithAlgorithm("SHA1withRSA");

    CsrValidationResult result =
        csrValidatorService.validate(sha1Csr, CsrProfile.TLS_SERVER);

    assertThat(result.getErrors())
        .anyMatch(e -> e.contains("PKI_CRYPTO_002"));
}

@Test
void submit_rsa1024_isRejected() {
    String weakCsr = generateRsaCsr(1024);

    CsrValidationResult result =
        csrValidatorService.validate(weakCsr, CsrProfile.TLS_SERVER);

    assertThat(result.getErrors())
        .anyMatch(e -> e.contains("PKI_KEY_001"));
}
```

### 11.3 Layer 4 — Policy Tests

```java
@Test
void tlsServer_personNameAsCn_isRejected() {
    String csr = buildCsr("CN=John Doe,O=Acme,C=IN", CsrProfile.TLS_SERVER);

    CsrValidationResult result =
        csrValidatorService.validate(csr, CsrProfile.TLS_SERVER);

    assertThat(result.getErrors())
        .anyMatch(e -> e.contains("PKI_POL_013"));   // spaces in CN
    assertThat(result.getErrors())
        .anyMatch(e -> e.contains("PKI_POL_004"));   // no SAN
}

@Test
void tlsServer_validFqdn_passes() {
    String csr = buildCsrWithSan(
        "CN=api.acme.com,O=Acme,C=IN",
        "DNS:api.acme.com",
        CsrProfile.TLS_SERVER
    );

    CsrValidationResult result =
        csrValidatorService.validate(csr, CsrProfile.TLS_SERVER);

    assertThat(result.isPassed()).isTrue();
    assertThat(result.getCommonName()).isEqualTo("api.acme.com");
}

@Test
void csr_withCaEqualTrue_isRejected() {
    String csr = buildCsrWithExtension("basicConstraints=CA:TRUE");

    CsrValidationResult result =
        csrValidatorService.validate(csr, CsrProfile.TLS_SERVER);

    assertThat(result.getErrors())
        .anyMatch(e -> e.contains("PKI_POL_008"));
}
```

### 11.4 Layer 5 — Duplicate Tests

```java
@Test
void submit_sameCsrTwice_returns409() {
    String csr = generateValidTlsServerCsr("api.acme.com");

    // First submission succeeds
    given().body(Map.of("pkcs10", csr, "clientTxnId", "T100", "profile", "TLS_SERVER"))
           .when().post("/api/ra/requests")
           .then().statusCode(201);

    // Second submission with same CSR bytes (different clientTxnId) → 409
    given().body(Map.of("pkcs10", csr, "clientTxnId", "T101", "profile", "TLS_SERVER"))
           .when().post("/api/ra/requests")
           .then().statusCode(409);
}
```

### 11.5 Layer 6 — Workflow Tests

```java
@Test
void requester_cannotPickup_ownRequest() {
    Long requestId = submitCsrAs("alice");

    // Alice tries to pick up her own request
    given().auth().oauth2(tokenFor("alice"))
           .when().post("/api/ra/csr/" + requestId + "/pickup")
           .then().statusCode(403)
           .body("errorCode", equalTo("PKI_APR_006"));
}

@Test
void maker_cannotBeChecker_inDualMode() {
    Long requestId = submitAndPickupAs("alice", "bob");  // bob is maker
    reviewAs("bob", requestId, "Looks good");

    // Bob tries to accept his own review
    given().auth().oauth2(tokenFor("bob"))
           .when().post("/api/ra/csr/" + requestId + "/accept")
           .then().statusCode(403)
           .body("errorCode", equalTo("PKI_APR_005"));
}
```

---

*Related documents:*
- *RA_Approval_Workflow_Architecture_V2.md* — full workflow RFC
- *PKCS10_CSR_Structure_Guide.md* — complete CSR field reference
- *CSR_Validation_Scenario_And_Implementation.md* — scenario walkthroughs + Java source
- *CSR_Validation_7_Layer_Guide.txt* — original 7-layer plain-text guide

*Implementation files:*
- `subprojects/raservice/src/main/java/com/pki/ra/raservice/csr/validation/CsrValidatorService.java`
- `subprojects/raservice/src/main/java/com/pki/ra/raservice/csr/validation/CsrValidationResult.java`
- `subprojects/raservice/src/main/java/com/pki/ra/raservice/csr/validation/CsrValidationException.java`
- `subprojects/raservice/src/main/java/com/pki/ra/raservice/csr/ApprovalWorkflowService.java`
- `subprojects/raservice/src/main/java/com/pki/ra/raservice/csr/CaIntegrationService.java`
