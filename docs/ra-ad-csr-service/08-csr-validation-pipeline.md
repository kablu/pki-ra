# RA AD CSR Service — CSR Validation Pipeline

**Document type:** Validation Design
**Module:** `pki-ra/subprojects/ra-ad-csr-service`

---

## 1. Overview

Every CSR submission passes through a sequential validation pipeline before it is accepted and persisted. The pipeline is designed as a series of clearly ordered gates — each gate must pass before the next one is entered. The first failure immediately aborts the pipeline and returns a structured error response with the appropriate error code.

The pipeline runs inside the `CsrSubmissionService.submit()` method after the `AdIdentityFilter` has already authenticated the caller and the Spring MVC framework has validated the request body's structural shape.

There are **nine validation stages** in total, divided into two groups: pre-parse checks (performed before the CSR bytes are decoded) and post-parse checks (performed after the CSR is decoded into a PKCS#10 structure).

---

## 2. Pipeline Overview

| Stage | Name | Group | Error Code on Failure |
|---|---|---|---|
| 1 | Request Structure Validation | Pre-parse | RA-CSR-001 |
| 2 | CSR Size Check | Pre-parse | RA-CSR-006 |
| 3 | PEM Format Check | Pre-parse | RA-CSR-001 |
| 4 | PKCS#10 Parse and Decode | Pre-parse | RA-CSR-001 |
| 5 | Signature Verification | Post-parse | RA-CSR-002 |
| 6 | Algorithm Policy Check | Post-parse | RA-CSR-003 |
| 7 | Key Size and Curve Check | Post-parse | RA-CSR-003 |
| 8 | CN vs AD Identity Match | Post-parse | RA-IDN-001 |
| 9 | Duplicate CSR Detection | Post-parse | RA-DUP-001 |

---

## 3. Stage Descriptions

### Stage 1 — Request Structure Validation

**What is checked:**
The incoming JSON request body is validated by Jakarta Bean Validation before the service layer is even invoked. This checks that the `csr_pem` field is present and not blank, and that the `certificate_profile` field contains a recognised profile name. If either field fails, Spring MVC rejects the request with a 400 before the pipeline begins.

**Who performs it:**
Spring MVC `@Valid` annotation on the controller method parameter, backed by `@NotBlank` and `@NotNull` constraints on the request DTO.

**Failure behaviour:**
Spring MVC returns a 400 with a validation error response. The `GlobalExceptionHandler` converts the `MethodArgumentNotValidException` into the standard error response format using error code `RA-CSR-001`.

---

### Stage 2 — CSR Size Check

**What is checked:**
The raw PEM string length in bytes is compared against the `csr.max.size.bytes` configuration value loaded from `RaConfigCacheService`. The default maximum is 8 192 bytes. This check prevents excessively large inputs from reaching the CPU-intensive PKCS#10 parse stage.

**Why this matters:**
PKCS#10 parsing involves ASN.1 decoding and cryptographic signature verification — both are relatively expensive. A malicious or malformed client sending a multi-megabyte payload would waste server resources. The size check is a cheap O(1) guard placed before any expensive work.

**Failure behaviour:**
Throws `CsrValidationException` with error code `RA-CSR-006`. Returns HTTP 400.

---

### Stage 3 — PEM Format Check

**What is checked:**
The PEM string must begin with the header `-----BEGIN CERTIFICATE REQUEST-----` and end with the footer `-----END CERTIFICATE REQUEST-----`. The content between the markers must be valid Base64. This check catches immediately obvious format errors before attempting ASN.1 parsing.

**Why this matters:**
Without this guard, an improperly formatted string would produce a cryptic Bouncy Castle parse exception, which is harder to translate into a meaningful user error.

**Failure behaviour:**
Throws `CsrValidationException` with error code `RA-CSR-001`. Returns HTTP 400.

---

### Stage 4 — PKCS#10 Parse and Decode

**What is checked:**
The Base64 content of the PEM is decoded to DER bytes and parsed as a PKCS#10 `CertificationRequest` structure using Bouncy Castle. This confirms that the ASN.1 structure is internally valid — all mandatory fields (subject, public key info, signature algorithm, signature) are present and correctly encoded.

**Failure behaviour:**
Any parse exception is caught and wrapped in `CsrValidationException` with error code `RA-CSR-001`. Returns HTTP 400.

---

### Stage 5 — Signature Verification

**What is checked:**
The PKCS#10 self-signature is verified using the public key embedded in the CSR itself. This confirms that the submitter possesses the corresponding private key — they did not forge the CSR or copy a public key from somewhere else.

**Why this matters:**
This is the most critical cryptographic check. A CSR whose self-signature does not verify means either the CSR has been tampered with in transit or was constructed by someone who does not hold the private key. Both scenarios must be rejected.

**How it works:**
Bouncy Castle's `ContentVerifierProvider` is used to verify the signature over the certification request info bytes using the algorithm identified in the CSR's signature algorithm field.

**Failure behaviour:**
Throws `CsrValidationException` with error code `RA-CSR-002`. Returns HTTP 400.

---

### Stage 6 — Algorithm Policy Check

**What is checked:**
The signature algorithm reported in the CSR (e.g. SHA256withRSA) is compared against the list of allowed algorithms from `RaConfigCacheService` key `csr.allowed.signature.algorithms`. Similarly, the public key algorithm (RSA or EC) is checked against policy.

**Why this matters:**
Weak algorithms (MD5withRSA, SHA1withRSA) must be explicitly blocked. The policy list is configurable in `ra_config` so the RA administrator can tighten or relax allowed algorithms without a code change or redeployment.

**Failure behaviour:**
Throws `CsrValidationException` with error code `RA-CSR-003`. Returns HTTP 400.

---

### Stage 7 — Key Size and Curve Check

**What is checked:**
For RSA keys: the key size in bits (extracted from the public key info) is verified against the `csr.allowed.key.sizes` list (default: 2048, 3072, 4096). RSA keys smaller than 2048 bits are always rejected.

For EC keys: the named curve (P-256, P-384, P-521) is verified against the `csr.allowed.ec.curves` list. Non-standard or unknown curves are always rejected.

**Why this matters:**
RSA-1024 and RSA-512 are cryptographically broken. EC keys on non-standard curves may not be supported by downstream CA software. Enforcing key size policy here prevents weak certificates from being issued.

**Failure behaviour:**
Throws `CsrValidationException` with error code `RA-CSR-003`. Returns HTTP 400.

---

### Stage 8 — Common Name vs AD Identity Match

**What is checked:**
The Common Name (CN) value extracted from the CSR's subject distinguished name is compared against the AD `displayName` (or `sAMAccountName`) extracted from the request headers by `AdIdentityFilter`. The comparison is case-insensitive after trimming whitespace.

**Why this matters:**
This check ensures a user cannot submit a CSR with someone else's name in the CN field. Without this check, user A could submit a CSR containing user B's name and potentially obtain a certificate impersonating B.

**Match rules:**
- The CN in the CSR must equal the AD display name OR the AD sAMAccountName
- If the CSR contains `uid_data.poi.name` (identity data flow), the name in POI is used instead of CN
- Partial matches and substring matches are not accepted — equality only

**Failure behaviour:**
Throws `CsrIdentityMismatchException` with error code `RA-IDN-001`. Returns HTTP 422.

---

### Stage 9 — Duplicate CSR Detection

**What is checked:**
A SHA-256 hash of the DER-encoded CSR bytes is computed. The database is queried for any existing `csr_submission` record with the same `csr_hash` and a `created_at` timestamp within the configured duplicate window (default 24 hours, from `csr.duplicate.window.hours`).

**Why this matters:**
Without duplicate detection, a user could accidentally or deliberately submit the same CSR multiple times in quick succession, producing multiple certificate issuance requests for identical keys. Duplicate submissions are a strong indicator of a replay attack or a client bug.

**Failure behaviour:**
Throws `DuplicateCsrException` with error code `RA-DUP-001`. Returns HTTP 409 Conflict. The response includes the `submission_id` of the earlier matching submission so the caller can look it up.

---

## 4. Validation Pipeline Flow — Step by Step

A request arriving at `POST /api/ra/ad/csr/submit` passes through the following ordered steps. Each step either passes and moves to the next, or fails and immediately returns an error response.

1. Spring MVC validates the JSON request body — confirms `csr_pem` is present and not blank, and `certificate_profile` is a recognised value. Failure returns HTTP 400 with error code `RA-CSR-001`.
2. The raw PEM byte length is measured against the `csr.max.size.bytes` limit. Failure returns HTTP 400 with error code `RA-CSR-006`.
3. The PEM header and footer markers are verified to be present and the Base64 content between them is confirmed to be syntactically valid. Failure returns HTTP 400 with error code `RA-CSR-001`.
4. Bouncy Castle parses the decoded DER bytes into a PKCS#10 `CertificationRequest` object. Any structural ASN.1 error returns HTTP 400 with error code `RA-CSR-001`.
5. The CSR's self-signature is verified using the embedded public key. A verification failure returns HTTP 400 with error code `RA-CSR-002`.
6. The signature algorithm is checked against the allowed algorithm list from the configuration cache. A disallowed algorithm returns HTTP 400 with error code `RA-CSR-003`.
7. The RSA key size or EC named curve is checked against the allowed values from the configuration cache. A disallowed key returns HTTP 400 with error code `RA-CSR-003`.
8. The Common Name from the CSR subject is compared against the AD display name and username from the request headers. A mismatch returns HTTP 422 with error code `RA-IDN-001`.
9. The SHA-256 hash of the DER-encoded CSR is computed and checked against existing `csr_submission` records within the duplicate window. A duplicate returns HTTP 409 with error code `RA-DUP-001`.
10. All stages pass — the submission is persisted and HTTP 201 is returned with the `submission_id`.

---

## 5. CSR Hash Computation

The duplicate detection hash is computed as follows:

- The PKCS#10 structure is re-encoded to its canonical DER representation using Bouncy Castle
- SHA-256 is applied to the raw DER bytes
- The result is hex-encoded to a 64-character lowercase string
- This string is stored in `csr_submission.csr_hash`

Using canonical DER encoding (not the raw PEM input) ensures that trivial formatting differences — extra whitespace, different line endings in the PEM — do not bypass duplicate detection. Two CSRs generated from the same private key and subject will always produce the same DER and therefore the same hash.

---

## 6. Error Code Map

| Stage | Exception | Error Code | HTTP Status |
|---|---|---|---|
| 1 | MethodArgumentNotValidException | RA-CSR-001 | 400 |
| 2 | CsrValidationException | RA-CSR-006 | 400 |
| 3 | CsrValidationException | RA-CSR-001 | 400 |
| 4 | CsrValidationException | RA-CSR-001 | 400 |
| 5 | CsrValidationException | RA-CSR-002 | 400 |
| 6 | CsrValidationException | RA-CSR-003 | 400 |
| 7 | CsrValidationException | RA-CSR-003 | 400 |
| 8 | CsrIdentityMismatchException | RA-IDN-001 | 422 |
| 9 | DuplicateCsrException | RA-DUP-001 | 409 |

---

## 7. Audit Events Emitted by Pipeline

| Stage | Outcome | Audit Action |
|---|---|---|
| Stage 1 | FAIL | Not audited — pre-service rejection |
| Stages 2–7 | FAIL | `CSR_SUBMIT_INVALID` with FAILURE outcome |
| Stage 8 | FAIL | `CSR_SUBMIT_IDENTITY_MISMATCH` with FAILURE outcome |
| Stage 9 | FAIL | `CSR_SUBMIT_DUPLICATE` with FAILURE outcome |
| All stages pass | SUCCESS | `CSR_SUBMIT` with SUCCESS outcome |

All audit events are written via `AuditLogService` with `REQUIRES_NEW` propagation, meaning the audit record is always committed to the database regardless of whether the main transaction succeeds or rolls back.
