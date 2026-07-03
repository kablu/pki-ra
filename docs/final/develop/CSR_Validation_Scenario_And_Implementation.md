# CSR Validation — User Scenario, Problem, Solution & Implementation

**Context:** Enterprise Registration Authority (pki-ra), Spring Boot + Bouncy Castle  
**Branch:** csr-approval-dev

---

## 1. User Case / Scenario

```
Actor:  Ravi Sharma (Server Administrator at Acme Corp)
Goal:   Request a TLS server certificate for api.acme.com
Tool:   REST client (Postman / curl) calling POST /api/ra/requests
Auth:   JWT from Active Directory (AD group: CERT_REQUESTORS)
```

### Ravi's Happy Path

```
1. Ravi generates a key pair on his server:
      openssl genrsa -out api.key 4096

2. Ravi creates a CSR:
      openssl req -new -key api.key \
        -subj "/CN=api.acme.com/O=Acme Corp/C=IN" \
        -addext "subjectAltName=DNS:api.acme.com" \
        -addext "keyUsage=critical,digitalSignature,keyEncipherment" \
        -addext "extendedKeyUsage=serverAuth" \
        -out api.csr

3. Ravi submits to RA:
      POST /api/ra/requests
      Authorization: Bearer <jwt-from-AD>
      {
        "pkcs10": "-----BEGIN CERTIFICATE REQUEST-----\n...\n-----END...",
        "clientTxnId": "TXN-2026-07-03-RAVI-001",
        "profile": "TLS_SERVER"
      }

4. RA validates the CSR (7 layers).

5. If all layers pass → status = SUBMITTED → enters approval queue.

6. Operator reviews → Maker approves → (optional Checker) → APPROVED.

7. RA sends to CA → certificate issued → Ravi downloads PEM.
```

---

## 2. Problem Statement

When the RA receives the raw CSR bytes, **it cannot trust the content**.
The user may (accidentally or intentionally) submit:

| # | Problem | Example | Impact |
|---|---------|---------|--------|
| P1 | Corrupted / forged CSR | Garbage bytes, wrong PEM header | CA crash or unexpected behavior |
| P2 | Wrong key in CSR | Different private key used to sign | Cert issued but Ravi can't use it (private key mismatch) |
| P3 | Weak algorithm | SHA1withRSA | Cert rejected by modern browsers/OS |
| P4 | Weak key | RSA-512 | Trivially breakable — security breach |
| P5 | Wrong CN format | CN = "Ravi Sharma" for TLS_SERVER | Browsers won't match hostname; HTTPS fails |
| P6 | Missing SAN | No dNSName extension | RFC 2818 violation; all modern browsers reject |
| P7 | CA bit set | BasicConstraints cA=TRUE | Attacker could sign certs with issued cert |
| P8 | Wrong person requesting server cert | john@acme.com requests api.acme.com | Domain not in john's authorized list |

**Core problem:** The RA is the last line of defense before the CA.
Once the CA issues the certificate, revocation is expensive and disruptive.
Catching errors here — before the operator spends time reviewing — saves
everyone time and prevents security incidents.

---

## 3. Solution: 7-Layer CSR Validation

Validation runs **synchronously** at submission time before the request
is persisted to the database as `SUBMITTED`. A failed request is saved
with status `VALIDATION_FAILED` so the user can see what went wrong.

```
POST /api/ra/requests
         │
         ▼
  ┌─────────────────────────────────────────────────────┐
  │              CsrValidatorService.validate()          │
  │                                                      │
  │  Layer 1 ── Parse (PEM / DER decode)                │
  │     └─ FAIL → PKI_VAL_002  "CSR parse failure"      │
  │                                                      │
  │  Layer 2 ── Proof of Possession (self-signature)    │
  │     └─ FAIL → PKI_CRYPTO_001 "Signature invalid"    │
  │                                                      │
  │  Layer 3 ── Signature Algorithm (no SHA1/MD5)       │
  │     └─ FAIL → PKI_CRYPTO_002 "Weak algorithm"       │
  │                                                      │
  │  Layer 4 ── Key Strength (RSA≥2048, EC≥P256)        │
  │     └─ FAIL → PKI_KEY_001   "Key too small"         │
  │                                                      │
  │  Layer 5 ── Subject DN (CN format per profile)      │
  │     └─ FAIL → PKI_POL_013  "CN has spaces"          │
  │              PKI_POL_014  "CN not FQDN"             │
  │                                                      │
  │  Layer 6 ── SAN (dNSName required for TLS_SERVER)   │
  │     └─ FAIL → PKI_POL_004  "Missing SAN"            │
  │              PKI_POL_005  "Invalid FQDN in SAN"     │
  │                                                      │
  │  Layer 7 ── Extensions (KeyUsage, EKU, BasicConst)  │
  │     └─ FAIL → PKI_POL_008  "cA=TRUE forbidden"      │
  │              PKI_POL_009  "keyCertSign forbidden"   │
  │              PKI_POL_012  "anyEKU forbidden"        │
  └─────────────────────────────────────────────────────┘
         │                          │
     All PASS                    Any FAIL
         │                          │
         ▼                          ▼
   status = SUBMITTED        status = VALIDATION_FAILED
   → approval queue          → 422 response with all errors
```

**Key design decisions:**
- **Collect all errors** (not fail-fast) so the user sees all problems in one response.
- **Errors = hard fail.** Warnings are logged and stored but do not block.
- **Metadata extracted during validation** (subjectDn, keyAlgorithm, keySize,
  signatureAlgorithm, subjectAltNames) is stored on the `csr_requests` row
  directly — no second parse needed.

---

## 4. Where Validation Fits in the Flow

```java
// CertificateLifecycleService.submitRequest() — pseudo-code

public CsrRequestDto submitRequest(String csrPem, CsrProfile profile, ...) {

    // Step 1: validate
    CsrValidationResult validation =
        csrValidatorService.validate(csrPem, profile);

    // Step 2: build entity regardless (saved for audit trail)
    CsrRequest entity = buildEntity(csrPem, validation, requestorUser);

    if (!validation.isPassed()) {
        entity.setStatus(CsrStatus.VALIDATION_FAILED);
        entity.setStatusReason(String.join("; ", validation.getErrors()));
        csrRepo.save(entity);
        throw new CsrValidationException(validation.getErrors());  // → 422
    }

    entity.setStatus(CsrStatus.SUBMITTED);
    csrRepo.save(entity);
    return toDto(entity);
}
```

---

## 5. Scenario Walkthroughs

### Scenario A — Happy Path (Ravi, correct CSR)

```
Input:
  CN = api.acme.com
  Profile = TLS_SERVER
  Key = RSA-4096
  SAN = DNS:api.acme.com
  Sig = SHA256withRSA

Layer 1: PASS — PEM parsed
Layer 2: PASS — self-signature valid (Ravi used his own private key)
Layer 3: PASS — SHA256withRSA is acceptable
Layer 4: PASS — RSA 4096 bits >= 2048 minimum
Layer 5: PASS — CN "api.acme.com" is valid FQDN
Layer 6: PASS — SAN has dNSName = api.acme.com
Layer 7: PASS — no cA=TRUE, no keyCertSign, EKU has serverAuth

Result: PASSED → status = SUBMITTED → goes to approval queue
```

---

### Scenario B — John requests server cert with person name as CN

```
Input:
  CN = John Doe          ← person's name, not FQDN
  Profile = TLS_SERVER
  Key = RSA-2048
  SAN = absent

Layer 1: PASS
Layer 2: PASS
Layer 3: PASS
Layer 4: PASS
Layer 5: FAIL
  → [PKI_POL_013] CN 'John Doe' contains spaces. TLS_SERVER CN must
    be a fully qualified domain name (FQDN), e.g. 'api.acme.com'.
    Person names are not valid server CNs.
Layer 6: FAIL
  → [PKI_POL_004] TLS_SERVER CSR has no subjectAltName extension.

Result: FAILED (2 errors) → status = VALIDATION_FAILED
HTTP 422 response:
{
  "errors": [
    "[PKI_POL_013] CN 'John Doe' contains spaces. ...",
    "[PKI_POL_004] TLS_SERVER CSR has no subjectAltName extension. ..."
  ]
}
```

---

### Scenario C — Tampered CSR (copied public key, different private key)

```
Input:
  CN = api.acme.com
  Profile = TLS_SERVER
  Key = RSA-4096 (attacker inserted his own public key)
  Signed with = attacker's private key (does NOT match embedded public key)

Layer 1: PASS — PEM structure is valid
Layer 2: FAIL
  → [PKI_CRYPTO_001] CSR self-signature verification failed —
    private key mismatch or CSR tampered

Result: FAILED → VALIDATION_FAILED immediately
```

---

### Scenario D — Weak key RSA-1024

```
Layer 4: FAIL
  → [PKI_KEY_001] RSA key size 1024 bits is below minimum 2048 bits
```

---

### Scenario E — SHA1 signature algorithm

```
Layer 3: FAIL
  → [PKI_CRYPTO_002] Signature algorithm 'SHA1WITHRSA' is not acceptable.
    Use SHA256withRSA, SHA384withRSA, SHA512withRSA, or ECDSA-with-SHA256+
```

---

### Scenario F — CSR has cA=TRUE (CA cert in disguise)

```
Layer 7: FAIL
  → [PKI_POL_008] CSR has BasicConstraints cA=TRUE.
    End-entity certificates must have cA=FALSE or omit BasicConstraints.
```

---

## 6. Implementation — Files Created

```
subprojects/raservice/src/main/java/com/pki/ra/raservice/csr/validation/
├── CsrValidationResult.java      ← result DTO (errors, warnings, extracted metadata)
├── CsrValidationException.java   ← HTTP 422 exception carrying all errors
└── CsrValidatorService.java      ← 7-layer Spring @Service (main implementation)
```

### CsrValidationResult — Key Fields

| Field | Type | Purpose |
|-------|------|---------|
| `passed` | boolean | true if errors list is empty |
| `errors` | List<String> | hard failures (coded: "[PKI_XXX_NNN] message") |
| `warnings` | List<String> | soft issues (logged, stored, do not block) |
| `subjectDn` | String | extracted from CSR — stored on entity |
| `keyAlgorithm` | String | "RSA" / "EC" / "EdDSA" |
| `keySize` | int | RSA modulus bits or EC field bits |
| `signatureAlgorithm` | String | e.g. "SHA256WITHRSA" |
| `subjectAltNames` | String | comma-separated "DNS:..., email:..." |
| `commonName` | String | raw CN value |

### CsrValidatorService — Layer Method Map

| Layer | Method | Error Codes |
|-------|--------|-------------|
| 1 Parse | `parse()` | PKI_VAL_001, PKI_VAL_002 |
| 2 Signature | `checkSignature()` | PKI_CRYPTO_001 |
| 3 Sig Algorithm | `checkSignatureAlgorithm()` | PKI_CRYPTO_002 |
| 4 Key Strength | `checkKeyStrength()` | PKI_KEY_001 |
| 5 Subject DN | `checkSubjectDn()` | PKI_POL_013, PKI_POL_014 |
| 6 SAN | `checkSan()` | PKI_POL_004, PKI_POL_005, PKI_POL_006 |
| 7 Extensions | `checkExtensions()` | PKI_POL_008, PKI_POL_009, PKI_POL_012 |

### Integration Point

Wire `CsrValidatorService` into your lifecycle/submit service:

```java
@Service
@RequiredArgsConstructor
public class CsrSubmitService {

    private final CsrValidatorService csrValidatorService;
    private final CsrRequestRepository csrRepo;

    @Transactional
    public CsrRequestDto submit(String csrPem, CsrProfile profile,
                                 User requestor, String ip) {

        CsrValidationResult val = csrValidatorService.validate(csrPem, profile);

        CsrRequest entity = CsrRequest.builder()
            .csrPem(csrPem)
            .subjectDn(val.getSubjectDn())
            .keyAlgorithm(val.getKeyAlgorithm())
            .keySize(val.getKeySize())
            .signatureAlgorithm(val.getSignatureAlgorithm())
            .subjectAltNames(val.getSubjectAltNames())
            .csrProfile(profile)
            .requestorUser(requestor)
            .validationPassed(val.isPassed())
            .status(val.isPassed() ? CsrStatus.SUBMITTED : CsrStatus.VALIDATION_FAILED)
            .statusReason(val.isPassed() ? null
                : String.join("; ", val.getErrors()))
            .build();

        csrRepo.save(entity);

        if (!val.isPassed()) {
            throw new CsrValidationException(val.getErrors());
        }

        return toDto(entity);
    }
}
```

---

## 7. Error Code Reference

| Code | Layer | Severity | Description |
|------|-------|----------|-------------|
| PKI_VAL_001 | 1 | ERROR | CSR payload null or empty |
| PKI_VAL_002 | 1 | ERROR | CSR parse failure (malformed PEM/DER) |
| PKI_CRYPTO_001 | 2 | ERROR | Self-signature invalid (key mismatch / tampered) |
| PKI_CRYPTO_002 | 3 | ERROR | Weak signature algorithm (SHA1, MD5) |
| PKI_KEY_001 | 4 | ERROR | Key size below minimum |
| PKI_KEY_002 | 4 | WARN | Cannot determine key size (unusual key type) |
| PKI_POL_001 | 5 | ERROR | CN missing from Subject DN |
| PKI_POL_004 | 6 | ERROR | TLS_SERVER: no SAN / no dNSName in SAN |
| PKI_POL_005 | 6 | ERROR | dNSName in SAN is not a valid FQDN |
| PKI_POL_006 | 6 | ERROR | S/MIME: no rfc822Name in SAN |
| PKI_POL_007 | 7 | WARN | No X.509v3 extensions in CSR |
| PKI_POL_008 | 7 | ERROR | BasicConstraints cA=TRUE (CA cert in disguise) |
| PKI_POL_009 | 7 | ERROR | KeyUsage has keyCertSign (CA-only bit) |
| PKI_POL_010 | 7 | ERROR | KeyUsage has cRLSign (CA-only bit) |
| PKI_POL_011 | 7 | WARN | Recommended KeyUsage bit missing |
| PKI_POL_012 | 7 | ERROR | EKU contains anyExtendedKeyUsage |
| PKI_POL_013 | 5 | ERROR | TLS_SERVER CN contains spaces (person name used instead of FQDN) |
| PKI_POL_014 | 5 | ERROR | TLS_SERVER CN is not a valid FQDN |

---

*Related: RA_Approval_Workflow_Architecture_V2.md, PKCS10_CSR_Structure_Guide.md*
