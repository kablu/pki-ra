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

## 8. Source Code — Full Implementation

### File: `CsrValidationResult.java`
**Path:** `subprojects/raservice/src/main/java/com/pki/ra/raservice/csr/validation/`

```java
package com.pki.ra.raservice.csr.validation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable result returned by CsrValidatorService.
 * Errors = hard failures. Warnings = soft issues that don't block.
 * Metadata fields are extracted during validation so the caller
 * does not need to re-parse the CSR.
 */
public final class CsrValidationResult {

    private final boolean passed;
    private final List<String> errors;
    private final List<String> warnings;
    private final String subjectDn;
    private final String keyAlgorithm;
    private final int    keySize;
    private final String signatureAlgorithm;
    private final String subjectAltNames;
    private final String commonName;

    private CsrValidationResult(Builder b) {
        this.passed             = b.errors.isEmpty();
        this.errors             = Collections.unmodifiableList(new ArrayList<>(b.errors));
        this.warnings           = Collections.unmodifiableList(new ArrayList<>(b.warnings));
        this.subjectDn          = b.subjectDn;
        this.keyAlgorithm       = b.keyAlgorithm;
        this.keySize            = b.keySize;
        this.signatureAlgorithm = b.signatureAlgorithm;
        this.subjectAltNames    = b.subjectAltNames;
        this.commonName         = b.commonName;
    }

    public static Builder builder() { return new Builder(); }

    public boolean isPassed()               { return passed; }
    public List<String> getErrors()         { return errors; }
    public List<String> getWarnings()       { return warnings; }
    public String getSubjectDn()            { return subjectDn; }
    public String getKeyAlgorithm()         { return keyAlgorithm; }
    public int getKeySize()                 { return keySize; }
    public String getSignatureAlgorithm()   { return signatureAlgorithm; }
    public String getSubjectAltNames()      { return subjectAltNames; }
    public String getCommonName()           { return commonName; }

    public static final class Builder {
        private final List<String> errors   = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        private String subjectDn;
        private String keyAlgorithm;
        private int    keySize;
        private String signatureAlgorithm;
        private String subjectAltNames;
        private String commonName;

        public Builder addError(String code, String detail) {
            errors.add("[" + code + "] " + detail);
            return this;
        }
        public Builder addWarning(String code, String detail) {
            warnings.add("[" + code + "] " + detail);
            return this;
        }
        public Builder subjectDn(String v)          { subjectDn = v;          return this; }
        public Builder keyAlgorithm(String v)       { keyAlgorithm = v;       return this; }
        public Builder keySize(int v)               { keySize = v;            return this; }
        public Builder signatureAlgorithm(String v) { signatureAlgorithm = v; return this; }
        public Builder subjectAltNames(String v)    { subjectAltNames = v;    return this; }
        public Builder commonName(String v)         { commonName = v;         return this; }
        public CsrValidationResult build()          { return new CsrValidationResult(this); }
    }
}
```

---

### File: `CsrValidationException.java`
**Path:** `subprojects/raservice/src/main/java/com/pki/ra/raservice/csr/validation/`

```java
package com.pki.ra.raservice.csr.validation;

import org.springframework.http.HttpStatus;
import com.pki.ra.common.exception.PkiRaException;
import java.util.List;

/**
 * Thrown when a submitted CSR fails one or more hard validation checks.
 * Extends PkiRaException → GlobalExceptionHandler returns HTTP 422.
 * Carries the full list of coded error messages so the user sees all
 * failures in one response.
 */
public class CsrValidationException extends PkiRaException {

    private final List<String> validationErrors;

    public CsrValidationException(List<String> validationErrors) {
        super(
            "CSR validation failed: " + String.join("; ", validationErrors),
            HttpStatus.UNPROCESSABLE_ENTITY
        );
        this.validationErrors = List.copyOf(validationErrors);
    }

    public List<String> getValidationErrors() {
        return validationErrors;
    }
}
```

---

### File: `CsrValidatorService.java` (key methods)
**Path:** `subprojects/raservice/src/main/java/com/pki/ra/raservice/csr/validation/`

```java
@Slf4j
@Service
public class CsrValidatorService {

    // ── Entry point ──────────────────────────────────────────────────────────
    public CsrValidationResult validate(String csrPem, CsrProfile profile) {
        CsrValidationResult.Builder result = CsrValidationResult.builder();

        PKCS10CertificationRequest csr = parse(csrPem, result);
        if (csr == null) return result.build();   // Layer 1 failed → stop

        checkSignature(csr, result);              // Layer 2
        checkSignatureAlgorithm(csr, result);     // Layer 3
        checkKeyStrength(csr, result, profile);   // Layer 4
        checkSubjectDn(csr, result, profile);     // Layer 5
        checkSan(csr, result, profile);           // Layer 6
        checkExtensions(csr, result, profile);    // Layer 7

        return result.build();
    }

    // ── Layer 1: Parse ───────────────────────────────────────────────────────
    private PKCS10CertificationRequest parse(String input,
                                              CsrValidationResult.Builder result) {
        try (PEMParser parser = new PEMParser(new StringReader(input.strip()))) {
            Object obj = parser.readObject();
            if (!(obj instanceof PKCS10CertificationRequest))  {
                result.addError("PKI_VAL_002", "Not a PKCS#10 CSR");
                return null;
            }
            return (PKCS10CertificationRequest) obj;
        } catch (Exception ex) {
            result.addError("PKI_VAL_002", "CSR parse failure: " + ex.getMessage());
            return null;
        }
    }

    // ── Layer 2: Proof of Possession ─────────────────────────────────────────
    private void checkSignature(PKCS10CertificationRequest csr,
                                 CsrValidationResult.Builder result) {
        try {
            ContentVerifierProvider verifier =
                new JcaContentVerifierProviderBuilder()
                    .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                    .build(csr.getSubjectPublicKeyInfo());

            if (!csr.isSignatureValid(verifier)) {
                result.addError("PKI_CRYPTO_001",
                    "Self-signature invalid — private key mismatch or CSR tampered");
            }
        } catch (Exception ex) {
            result.addError("PKI_CRYPTO_001",
                "Cannot verify CSR self-signature: " + ex.getMessage());
        }
    }

    // ── Layer 3: Signature Algorithm ─────────────────────────────────────────
    private static final Set<String> BANNED = Set.of(
        "MD5WITHRSA", "MD2WITHRSA", "SHA1WITHRSA",
        "SHA1WITHECDSA", "SHA1WITHDSA"
    );

    private void checkSignatureAlgorithm(PKCS10CertificationRequest csr,
                                          CsrValidationResult.Builder result) {
        String algName = resolveAlgName(
            csr.getSignatureAlgorithm().getAlgorithm().getId()
        ).toUpperCase();
        result.signatureAlgorithm(algName);
        if (BANNED.contains(algName)) {
            result.addError("PKI_CRYPTO_002",
                "Algorithm '" + algName + "' rejected. Use SHA256withRSA or ECDSA-SHA256+");
        }
    }

    // ── Layer 4: Key Strength ────────────────────────────────────────────────
    private void checkKeyStrength(PKCS10CertificationRequest csr,
                                   CsrValidationResult.Builder result,
                                   CsrProfile profile) {
        try {
            SubjectPublicKeyInfo spki = csr.getSubjectPublicKeyInfo();
            String oid = spki.getAlgorithm().getAlgorithm().getId();
            String jcaAlg = "1.2.840.113549.1.1.1".equals(oid) ? "RSA"
                          : "1.2.840.10045.2.1".equals(oid)    ? "EC" : null;

            if (jcaAlg == null) {
                result.keyAlgorithm(oid).keySize(0); // Ed25519/Ed448 always OK
                return;
            }
            java.security.PublicKey pk = java.security.KeyFactory
                .getInstance(jcaAlg, BouncyCastleProvider.PROVIDER_NAME)
                .generatePublic(new java.security.spec.X509EncodedKeySpec(spki.getEncoded()));

            if (pk instanceof RSAPublicKey rsa) {
                int bits = rsa.getModulus().bitLength();
                result.keyAlgorithm("RSA").keySize(bits);
                if (bits < 2048)
                    result.addError("PKI_KEY_001",
                        "RSA " + bits + " bits < minimum 2048 bits");
            } else if (pk instanceof ECPublicKey ec) {
                int bits = ec.getParams().getCurve().getField().getFieldSize();
                result.keyAlgorithm("EC").keySize(bits);
                if (bits < 256)
                    result.addError("PKI_KEY_001",
                        "EC " + bits + " bits < minimum P-256 (256 bits)");
            }
        } catch (Exception ex) {
            result.addWarning("PKI_KEY_002", "Key size check skipped: " + ex.getMessage());
        }
    }

    // ── Layer 5: Subject DN ──────────────────────────────────────────────────
    private void checkSubjectDn(PKCS10CertificationRequest csr,
                                  CsrValidationResult.Builder result,
                                  CsrProfile profile) {
        X500Name subject = csr.getSubject();
        result.subjectDn(subject.toString());
        RDN[] cnRdns = subject.getRDNs(BCStyle.CN);
        if (cnRdns == null || cnRdns.length == 0) {
            result.addError("PKI_POL_001", "CN missing from Subject DN"); return;
        }
        String cn = cnRdns[0].getFirst().getValue().toString().trim();
        result.commonName(cn);
        if (profile == CsrProfile.TLS_SERVER) {
            if (cn.contains(" "))
                result.addError("PKI_POL_013",
                    "CN '" + cn + "' contains spaces — must be FQDN, not a person name");
            else if (!FQDN_PATTERN.matcher(cn).matches() && !WILDCARD_FQDN.matcher(cn).matches())
                result.addError("PKI_POL_014", "CN '" + cn + "' is not a valid FQDN");
        }
    }

    // ── Layer 6: SAN ────────────────────────────────────────────────────────
    private void checkSan(PKCS10CertificationRequest csr,
                           CsrValidationResult.Builder result,
                           CsrProfile profile) {
        Extensions exts = extractExtensions(csr);
        if (exts == null) {
            if (profile == CsrProfile.TLS_SERVER)
                result.addError("PKI_POL_004",
                    "TLS_SERVER must have subjectAltName with dNSName (RFC 2818)");
            return;
        }
        Extension sanExt = exts.getExtension(Extension.subjectAlternativeName);
        if (sanExt == null) {
            if (profile == CsrProfile.TLS_SERVER)
                result.addError("PKI_POL_004", "No subjectAltName extension found");
            return;
        }
        List<String> sanList = new ArrayList<>();
        boolean hasDns = false, hasEmail = false;
        for (GeneralName gn : GeneralNames.getInstance(sanExt.getParsedValue()).getNames()) {
            String v = gn.getName().toString();
            if (gn.getTagNo() == GeneralName.dNSName) {
                hasDns = true; sanList.add("DNS:" + v);
                if (!FQDN_PATTERN.matcher(v).matches() && !WILDCARD_FQDN.matcher(v).matches())
                    result.addError("PKI_POL_005", "SAN dNSName '" + v + "' invalid FQDN");
            } else if (gn.getTagNo() == GeneralName.rfc822Name) {
                hasEmail = true; sanList.add("email:" + v);
            }
        }
        result.subjectAltNames(String.join(", ", sanList));
        if (profile == CsrProfile.TLS_SERVER && !hasDns)
            result.addError("PKI_POL_004", "SAN has no dNSName. Found: " + sanList);
        if (profile == CsrProfile.SMIME && !hasEmail)
            result.addError("PKI_POL_006", "S/MIME SAN must have rfc822Name (email)");
    }

    // ── Layer 7: Extensions ──────────────────────────────────────────────────
    private void checkExtensions(PKCS10CertificationRequest csr,
                                   CsrValidationResult.Builder result,
                                   CsrProfile profile) {
        Extensions exts = extractExtensions(csr);
        if (exts == null) { result.addWarning("PKI_POL_007", "No extensions in CSR"); return; }

        // BasicConstraints — block cA=TRUE
        Extension bcExt = exts.getExtension(Extension.basicConstraints);
        if (bcExt != null && BasicConstraints.getInstance(bcExt.getParsedValue()).isCA())
            result.addError("PKI_POL_008", "BasicConstraints cA=TRUE forbidden in end-entity CSR");

        // KeyUsage — block CA-only bits
        Extension kuExt = exts.getExtension(Extension.keyUsage);
        if (kuExt != null) {
            KeyUsage ku = KeyUsage.getInstance(kuExt.getParsedValue());
            if (ku.hasUsages(KeyUsage.keyCertSign))
                result.addError("PKI_POL_009", "KeyUsage keyCertSign is CA-only");
            if (ku.hasUsages(KeyUsage.cRLSign))
                result.addError("PKI_POL_010", "KeyUsage cRLSign is CA-only");
        }

        // EKU — block anyExtendedKeyUsage
        Extension ekuExt = exts.getExtension(Extension.extendedKeyUsage);
        if (ekuExt != null) {
            ExtendedKeyUsage eku = ExtendedKeyUsage.getInstance(ekuExt.getParsedValue());
            if (eku.hasKeyPurposeId(KeyPurposeId.anyExtendedKeyUsage))
                result.addError("PKI_POL_012", "anyExtendedKeyUsage forbidden in end-entity CSR");
        }
    }
}
```

---

*Related: RA_Approval_Workflow_Architecture_V2.md, PKCS10_CSR_Structure_Guide.md, Enterprise_RA_Validation_Guide.md*
