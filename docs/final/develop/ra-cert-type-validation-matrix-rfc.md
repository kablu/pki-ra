```text
Internal Specification                              Salman Technologies
Category: Standards Track (Internal)                        PKI-RA Team
Companion to: RA-SPEC-001, RA-SPEC-002                      RA-SPEC-003
                                                            7 July 2026


         Certificate-Type Validation Matrix for a Registration
                 Authority Processing Client CSRs
```

## Abstract

This document is written from the point of view of the Registration
Authority (RA) itself: "a CSR has arrived — which validations do I run,
and which of them depend on the certificate type requested?" It provides
a single exhaustive matrix mapping every RA validation to each of the
five certificate types (TLS Server, TLS Client, S/MIME, Code Signing,
Document Signing), followed by per-type validation profiles and a fully
worked TLS Client example. It is the operational companion to the
requirement text in RA-SPEC-001 and the threat rationale in RA-SPEC-002.

No validation is omitted: every requirement from RA-SPEC-001
(R-41…R-70, R-AD-*) and the master catalog (Tiers 0–14, addendum
G-01…G-16) appears in the matrix with its per-type applicability.

## Status

Internal specification for architecture review. Aligned with RFC 2986,
RFC 5280, RFC 6125, RFC 8555, RFC 8659, RFC 9495; CA/Browser Forum TLS
BR (SC-081 validity schedule, SC-067 MPIC), Code Signing BR, S/MIME BR;
ETSI EN 319 411; CCA India IVG.

---

## Table of Contents

```
1.  How the RA Uses This Document
2.  Notation
3.  RA Processing Model (order of validation)
4.  The Master Validation Matrix
    4.1  Stage A — Transport & Request
    4.2  Stage B — Syntactic (PKCS#10)
    4.3  Stage C — Cryptographic
    4.4  Stage D — Subject Distinguished Name
    4.5  Stage E — Key Usage / Extended Key Usage / Extensions
    4.6  Stage F — Type-Specific Control (the differentiator)
    4.7  Stage G — Identity Vetting
    4.8  Stage H — Workflow, Issuance & Lifecycle
5.  Per-Type Validation Profiles
6.  Worked Example — TLS Client CSR
7.  References
Appendix A.  One-Glance Applicability Summary
```

---

## 1. How the RA Uses This Document

When a CSR arrives, the RA runs a fixed sequence:

```
   CSR + declared certificateType
        │
        ▼
   ┌───────────────────────────────────────────────┐
   │ 1. Run ALL "common" validations (Stages A–E).  │
   │    These apply to EVERY type. Any failure = stop.│
   ├───────────────────────────────────────────────┤
   │ 2. Look up the declared certificateType.        │
   │    Load its profile (Stages D/E overrides +     │
   │    Stages F/G/H type rules).                     │
   ├───────────────────────────────────────────────┤
   │ 3. Run the type-specific validations.           │
   │    Cross-check: does the CSR's shape actually    │
   │    match the type it claims? (e.g. a CSR with    │
   │    EKU=serverAuth claiming TLS_CLIENT = reject.) │
   ├───────────────────────────────────────────────┤
   │ 4. Only if ALL pass → identity/workflow → CA.   │
   └───────────────────────────────────────────────┘
```

The matrix in Section 4 tells the RA, for each validation, whether it
is Mandatory, Should, Optional, Forbidden, or Not-Applicable **for the
specific type declared**. Stages A–C are almost entirely type-independent;
Stages D–H are where the type matters.

## 2. Notation

Matrix cells use these codes:

| Code | Meaning |
|------|---------|
| **M** | MUST perform / MUST be satisfied (reject on failure) |
| **S** | SHOULD perform (recommended; deviation logged) |
| **O** | MAY perform / optional per profile |
| **F** | FORBIDDEN — if present in the CSR, reject (MUST NOT appear) |
| **—** | Not applicable to this type |

Type columns:

| Col | Certificate type | Purpose in one line |
|-----|-----------------|---------------------|
| **SRV** | TLS_SERVER | Server proves it controls a domain |
| **CLI** | TLS_CLIENT | Client/device/user proves its identity (mTLS) |
| **MIME** | S/MIME | Person proves control of a mailbox |
| **CODE** | CODE_SIGNING | Organisation proves legal identity + key custody |
| **DOC** | DOCUMENT_SIGNING | Natural person proves legal identity (KYC) |

Requirement IDs (e.g. R-43-01, G-01) cross-reference RA-SPEC-001 and the
master catalog so no validation is orphaned.

---

## 3. RA Processing Model

```
Stage A  Transport & Request     ── type-independent (same for all)
Stage B  Syntactic (PKCS#10)     ── type-independent
Stage C  Cryptographic           ── mostly common; key floor differs
Stage D  Subject DN              ── TYPE-DEPENDENT
Stage E  KU / EKU / Extensions   ── TYPE-DEPENDENT (biggest differences)
Stage F  Type-Specific Control   ── TYPE-DEFINING (DCV / binding / MCV / attestation / KYC)
Stage G  Identity Vetting        ── depth TYPE-DEPENDENT
Stage H  Workflow / Issuance     ── approval count TYPE-DEPENDENT
```

Rule: a request MUST NOT advance to stage N+1 while stage N has an
unresolved failure. A validator error or timeout is treated as failure
(fail-closed).

---

## 4. The Master Validation Matrix

### 4.1 Stage A — Transport & Request

*Type-independent: identical for all five types.*

| ID | Validation | SRV | CLI | MIME | CODE | DOC |
|----|-----------|:---:|:---:|:----:|:----:|:---:|
| R-41-01 | Endpoint accepts TLS 1.2+ only | M | M | M | M | M |
| R-41-02 | Caller authenticated (mTLS/OIDC/API key; here: AD) | M | M | M | M | M |
| R-41-03 | Caller authorized for this certificate type (RBAC) | M | M | M | M | M |
| R-41-04 | Payload size cap enforced before parsing | M | M | M | M | M |
| R-41-05 | Per-caller rate limiting | M | M | M | M | M |
| R-41-06 | Unique clientTxnId (idempotency, DB-enforced) | M | M | M | M | M |
| R-41-07 | Strict request-schema validation; unknown fields rejected | M | M | M | M | M |
| R-41-08 | Free-text fields injection-screened (XSS/SQLi) | S | S | S | S | S |
| T0-09 | Signed-request timestamp skew window | O | O | O | O | O |
| R-41-09 | Per-tenant source-IP allowlist | O | O | O | O | O |

**AD authentication sub-checks (R-AD-*), all types M:** token/ticket
crypto-valid (R-AD-01); LDAPS/StartTLS with DC cert verify (R-AD-02);
clock skew (R-AD-03); live account-state check at sensitive actions
(R-AD-04); human-vs-service distinction (R-AD-05); fresh group resolution
(R-AD-06); nested-group resolution (R-AD-07); dedicated change-controlled
role groups (R-AD-08); per-request SoD (R-AD-09); MFA claim for officers
(R-AD-10); LDAP-injection escape (R-AD-14); least-privilege bind account
(R-AD-15).

### 4.2 Stage B — Syntactic (PKCS#10)

*Type-independent: a malformed CSR is rejected regardless of type.*

| ID | Validation | SRV | CLI | MIME | CODE | DOC |
|----|-----------|:---:|:---:|:----:|:----:|:---:|
| R-42-01 | CSR payload present, non-empty | M | M | M | M | M |
| R-42-02 | Valid PEM armor, exactly one CERTIFICATE REQUEST block | M | M | M | M | M |
| R-42-03 | Base64 decodes cleanly, no trailing bytes | M | M | M | M | M |
| R-42-04 | Parses as CertificationRequest in strict DER (reject BER) | M | M | M | M | M |
| R-42-05 | PKCS#10 version == 0 | M | M | M | M | M |
| R-42-06 | PRIVATE KEY block in payload → reject + permanent key blocklist | M | M | M | M | M |
| G-06 | EC explicit curve params → reject (namedCurve OID only) | M | M | M | M | M |
| G-07 | AlgorithmIdentifier params strict (RSA=NULL, ECDSA=absent) | M | M | M | M | M |

### 4.3 Stage C — Cryptographic

*Mostly common; the RSA key floor is the one type difference.*

| ID | Validation | SRV | CLI | MIME | CODE | DOC |
|----|-----------|:---:|:---:|:----:|:----:|:---:|
| R-43-01 | Proof of Possession — verify CSR self-signature | M | M | M | M | M |
| R-43-02 | Signature algorithm allowlist (SHA-256+; reject MD5/SHA-1) | M | M | M | M | M |
| R-43-03 | Signature alg consistent with key type | M | M | M | M | M |
| R-43-04 | RSA modulus ≥ 2048 | M | M | M | — | M |
| R-43-04c | RSA modulus ≥ **3072** (higher floor) | — | — | — | **M** | S |
| R-43-04u | RSA modulus upper bound (~8192, anti-DoS) | M | M | M | M | M |
| R-43-05 | RSA exponent odd, ≥ 65537 | M | M | M | M | M |
| R-43-06 | ECDSA curve ∈ {P-256, P-384}; point on curve | M | M | M | M | M |
| R-43-07 | Weak/compromised key screen (Debian, ROCA, Fermat, pwnedkeys) | M | M | M | M | M |
| R-43-08 | Reject SPKI revoked for keyCompromise | M | M | M | M | M |
| R-43-09 | Same key under different subject → reject + alert | S | S | S | M | M |
| R-43-10 | Key reuse across certificate types → reject | S | S | M | M | M |
| R-43-11 | ML-DSA/SLH-DSA only under PQC pilot profile | O | O | O | O | O |

> Note on R-43-04c: CA/Browser Forum CSBR sets the RSA-3072 floor for
> code signing. Document-signing tokens (India DSC) commonly use 2048 but
> 3072 is recommended (S). All other types keep the 2048 floor.

### 4.4 Stage D — Subject Distinguished Name

*Type-dependent: what the CN means, and which RDNs are mandatory, changes
per type.*

**Common DN hygiene (all types M/S):**

| ID | Validation | SRV | CLI | MIME | CODE | DOC |
|----|-----------|:---:|:---:|:----:|:----:|:---:|
| R-44-01 | RFC 5280 length bounds (CN≤64, O≤64, C=2…) | M | M | M | M | M |
| R-44-02 | Country = valid ISO 3166-1 alpha-2 | M | M | M | M | M |
| R-44-03 | String types UTF8String/PrintableString only | M | M | M | M | M |
| R-44-04 | Reject control/invisible/bidi-override chars | M | M | M | M | M |
| R-44-05 | Reject placeholder values ("-", "N/A", ".") | M | M | M | M | M |
| R-44-06 | Whitespace normalized (no leading/trailing/double) | M | M | M | M | M |
| R-44-08 | Mixed-script/homoglyph screen → manual review | S | S | S | M | M |

**Per-type DN content rules:**

| ID | Validation | SRV | CLI | MIME | CODE | DOC |
|----|-----------|:---:|:---:|:----:|:----:|:---:|
| D-CN-host | CN is an FQDN (matches a SAN dNSName) | M | F | — | — | — |
| D-CN-pers | CN is a person/service/device identity name | — | M | M | O | M |
| D-CN-org | CN is the verified legal org name | — | — | O | M | O |
| R-44-07 | O= equals requester's **vetted** organization | S | S | M | M | M |
| D-O-req | O (Organization) MANDATORY | O | O | S | M | S |
| D-C-req | C (Country) MANDATORY | O | O | S | M | M |
| R-44-09 | OU deprecated (reject for TLS) | M | M | S | S | S |
| R-44-09e | emailAddress-in-DN deprecated (move to SAN) | S | S | M | S | S |
| R-44-10 | Reject internal/reserved names (localhost, RFC1918, .local) | M | S | — | — | — |
| D-serial | subject serialNumber = registry number (EV) | — | — | — | M | O |
| D-CN-nohost | CN MUST NOT look like an FQDN | — | S | S | M | S |

Reading example (CLI column): for a TLS_CLIENT CSR the CN is a person/
service/device name (**M**), it MUST NOT be an FQDN (D-CN-host is **F** —
forbidden, and D-CN-nohost is **S**), and reserved-name screening still
applies (**S**).

### 4.5 Stage E — KeyUsage / ExtendedKeyUsage / Extensions

*Type-dependent: this is where a CSR must "match the type it claims".*

**Forbidden asks (all types — reject if present):**

| ID | Validation | SRV | CLI | MIME | CODE | DOC |
|----|-----------|:---:|:---:|:----:|:----:|:---:|
| R-45-01 | basicConstraints cA=TRUE → reject | F | F | F | F | F |
| R-45-02 | KeyUsage keyCertSign / cRLSign → reject | F | F | F | F | F |
| R-45-03 | EKU anyExtendedKeyUsage → reject | F | F | F | F | F |
| R-45-06 | Unknown/unprofiled extension OID → reject | M | M | M | M | M |
| R-45-07 | Client-supplied SKID/AKID/policies/SCT → ignore | M | M | M | M | M |

**ExtendedKeyUsage — the defining per-type control:**

| EKU (OID) | SRV | CLI | MIME | CODE | DOC |
|-----------|:---:|:---:|:----:|:----:|:---:|
| serverAuth (1.3.6.1.5.5.7.3.1) | **M** | F | F | F | F |
| clientAuth (1.3.6.1.5.5.7.3.2) | O | **M** | F | F | F |
| emailProtection (…3.4) | F | F | **M** | F | F |
| codeSigning (…3.3) | F | F | F | **M** | F |
| (doc-signing: no serverAuth/clientAuth/codeSigning) | F | F | O | F | **M**-absent |

**KeyUsage bits per type:**

| KeyUsage bit | SRV | CLI | MIME | CODE | DOC |
|--------------|:---:|:---:|:----:|:----:|:---:|
| digitalSignature | M | M | M | M | M |
| keyEncipherment (RSA) | M | O | M | F | F |
| keyAgreement (EC) | O | O | O | F | F |
| nonRepudiation / contentCommitment | — | — | S | F | **M** |
| R-45-05 KU consistent with key algorithm | M | M | M | M | M |

**Subject Alternative Name (SAN):**

| ID | Validation | SRV | CLI | MIME | CODE | DOC |
|----|-----------|:---:|:---:|:----:|:----:|:---:|
| SAN present & required | M (dNSName) | O | M (rfc822Name) | F | O |
| SAN dNSName | M | O | F | F | F |
| SAN rfc822Name (email) | F | O | M | F | O |
| SAN otherName UPN (…311.20.2.3) | — | O | — | — | — |
| SAN iPAddress | O | O | F | F | F |
| R-45-08 SAN count cap + no duplicates | M | M | M | M | M |
| R-45-04 EKU/type combination consistency (no cross-type) | M | M | M | M | M |

Reading example (CLI column): a TLS_CLIENT CSR MUST carry EKU=clientAuth
(**M**), MUST NOT carry serverAuth (**F**), SAN is optional (rfc822Name or
UPN otherName), and nonRepudiation is not applicable. If the CSR the user
submitted has serverAuth, the RA rejects it as a type mismatch even though
every byte is otherwise valid.

### 4.6 Stage F — Type-Specific Control (the differentiator)

*This is what makes each type a different job for the RA. Each type has
exactly one dominant "control" question.*

| ID | Control validation | SRV | CLI | MIME | CODE | DOC |
|----|-------------------|:---:|:---:|:----:|:----:|:---:|
| R-51-06 | **Domain Control Validation** (DNS/HTTP/ACME) for every dNSName | **M** | — | — | — | — |
| R-51-07 | Wildcard validated via DNS method only | M* | — | — | — | — |
| R-51-08 | DCV evidence freshness (≤200d 2026 → ≤10d 2029, SC-081) | M | — | — | — | — |
| R-51-09 | CAA `issue`/`issuewild` check ≤8h pre-issuance | M | — | — | — | — |
| G-01 | **MPIC** — DCV+CAA corroborated from ≥2 perspectives (≥500km) | M | — | — | — | — |
| G-02 | DNSSEC validation on CAA/DCV lookups | M | — | — | — | — |
| R-51-10 | IP-SAN control validation; no RFC1918 in public | M | — | — | — | — |
| R-52-01 | **Identity binding** — subject exists in AD/HR (human) or CMDB/MDM (svc/device) | — | **M** | — | — | — |
| R-52-02 | **Requester owns the identity** (impersonation guard) | — | **M** | — | — | — |
| R-52-04 | UPN/rfc822Name SAN matches directory exactly | — | M | — | — | — |
| R-52-06 | Device/service asset status = ACTIVE in CMDB | — | S | — | — | — |
| R-53-02 | **Mailbox Control Validation** (random challenge to exact address) | — | — | **M** | — | — |
| R-53-03 | Mail domain on org's verified list | — | — | M | — | — |
| G-03 | CAA `issuemail` check (RFC 9495) | — | — | M | — | — |
| R-53-04 | MCV/domain evidence ≤398 days | — | — | M | — | — |
| R-54-04 | **Key attestation** — private key in FIPS 140-2 L2 / CC EAL4+ HW | — | — | — | **M** | S |
| R-54-04a | Attested public key == CSR public key | — | — | — | M | S |
| R-55-05 | Key in CCA-compliant crypto token (India DSC) | — | — | — | — | **M** |
| G-04 | WHOIS-sourced validation contacts NOT used | M | — | — | M | — |

\* R-51-07 applies only when the TLS_SERVER CSR requests a wildcard SAN.

### 4.7 Stage G — Identity Vetting

*Depth scales from "none" (TLS server DV) to "full KYC" (document signing).*

| ID | Vetting validation | SRV | CLI | MIME | CODE | DOC |
|----|-------------------|:---:|:---:|:----:|:----:|:---:|
| R-60-01 | Org legal existence via authoritative registry | O(OV/EV) | — | S | M | S |
| R-60-02 | Verification contacts sourced independently (not applicant) | O | — | S | M | M |
| R-51-14 | OV/EV org vetting in addition to DCV | O | — | — | — | — |
| R-54-05 | Org registry lookup + operational status ACTIVE | — | — | — | M | S |
| R-54-06 | Verified callback (registry-sourced number) | — | — | — | M | S |
| R-60-03 | Requester affiliation + authority to request | S | S | M | M | M |
| R-55-04 | Personal KYC (Aadhaar/PAN/bank) + video ≤2 days (India DSC) | — | — | — | — | **M** |
| R-53-06 | Sponsor-validated: person verified vs HR; ID evidence ≤825d | — | S | M | — | — |
| R-60-04 | Vetting-evidence freshness windows enforced | M | S | M | M | M |
| R-60-05 | Signed, current subscriber agreement | M | M | M | M | M |
| R-60-06 | Sanctions + internal denied-applicant screening | S | O | S | M | M |
| R-54-07 | Malware/abuse + typosquat screening | — | — | — | M | — |
| R-60-07 | Velocity/anomaly detection | S | S | S | S | S |
| R-60-08 | RA officer conflict-of-interest bar | M | M | M | M | M |
| G-15 | Data-source reliability evaluated before reliance | O | — | S | M | M |

### 4.8 Stage H — Workflow, Issuance & Lifecycle

| ID | Validation | SRV | CLI | MIME | CODE | DOC |
|----|-----------|:---:|:---:|:----:|:----:|:---:|
| R-70-01 | Legal state-machine transitions only | M | M | M | M | M |
| R-70-02 | Separation of duties (submitter ≠ approver, DB-enforced) | M | M | M | M | M |
| R-70-03 | Dual independent officer approval | O | O | O | **M** | M |
| auto | Auto-approval permitted (no human) | O(DV) | S | S | F | F |
| R-70-04 | Post-approval modification voids approval | M | M | M | M | M |
| R-70-05 | Pending requests expire | M | M | M | M | M |
| R-70-06 | Validity clamped to type cap at submit AND CA-send | M | M | M | M | M |
| — | Validity cap value | ≤200d | ≤1y | ≤824d | ≤1y | ≤3y |
| R-70-07 | Pre-issuance lint (zlint/cablint) | M | M | M | M | S |
| R-70-08 | Verify-back: issued cert matches approved request | M | M | M | M | M |
| R-70-09 | CT logging (≥2 qualified logs) | M | O | O | O | — |
| G-05 | Short-lived (≤7d) revocation-exempt profile | O | O | — | — | — |
| R-70-10 | Append-only, hash-chained audit of every verdict | M | M | M | M | M |
| R-AD-16 | Leaver hook → auto-revoke on AD disable | S | M | M | S | S |
| R-AD-17 | Mover hook → re-evaluate entitlement / void pending | S | M | M | S | S |
| G-13 | 24/7 problem-reporting + compromise-proof verification | M | M | M | M | M |
| G-14 | Validation-specialist qualification (trained staff) | S | S | M | M | M |

Reading example (CODE column): code signing is auto-approval **F**
(forbidden), dual approval **M**, malware screening **M**, key attestation
**M** — the strictest profile. TLS_CLIENT (CLI) may auto-approve (**S**)
once identity binding passes, single approver, and leaves must trigger
revocation (**M**).

---

## 5. Per-Type Validation Profiles (profile cards)

Each card lists the type's *defining* validations on top of the common
Stages A–C (which every type runs identically).

**TLS_SERVER** — control-heavy, automatable.
Defining: SAN dNSName (M) · DCV per dNSName (M) · MPIC+DNSSEC (M) ·
CAA issue (M) · EKU serverAuth (M) · validity ≤200d · auto-approve (DV OK).

**TLS_CLIENT** — identity-binding.
Defining: subject in AD/HR or CMDB (M) · requester owns identity (M) ·
EKU clientAuth only, serverAuth forbidden (M/F) · UPN matches directory (M)
· validity ≤1y · single approver · leaver auto-revoke (M).

**S/MIME** — mailbox control + sponsor identity.
Defining: SAN rfc822Name (M) · Mailbox Control Validation (M) · CAA
issuemail (M) · mail domain org-verified (M) · Strict/Multipurpose profile
· EKU emailProtection (M) · validity ≤824d.

**CODE_SIGNING** — identity + custody, strictest.
Defining: RSA≥3072 (M) · CN=vetted legal name, O+C mandatory (M) · no SAN
(F) · EKU codeSigning only (M) · hardware key attestation (M) · registry +
independent callback (M) · malware/sanctions screen (M) · dual manual
approval (M) · validity ≤1y.

**DOCUMENT_SIGNING** — legal KYC.
Defining: full personal KYC + video ≤2 days (M) · nonRepudiation KU (M) ·
no TLS/code EKUs (F) · CCA crypto token (M) · validity ≤3y · manual officer
approval (M).

---

## 6. Worked Example — TLS Client CSR (the active request)

The RA receives this CSR, declared `certificateType = TLS_CLIENT`:

```bash
openssl req -new -key client.key \
  -subj "/CN=service-a/O=Salman Technologies Pvt Ltd/C=IN" \
  -addext "keyUsage=critical,digitalSignature" \
  -addext "extendedKeyUsage=clientAuth" \
  -out client.csr
```

The RA runs the following ordered checks. **Every applicable matrix cell is
executed** — this is the "nothing skipped" list for TLS_CLIENT.

**Stage A — Transport & Request**
1. TLS 1.2+ endpoint (R-41-01) ✓
2. Caller authenticated via AD (R-41-02); token/ticket valid, LDAPS, clock
   skew, live account state, fresh + nested group resolution (R-AD-01..07) ✓
3. Caller authorized for TLS_CLIENT via AD role group (R-41-03, R-AD-08) ✓
4. Size cap (R-41-04), rate limit (R-41-05), unique clientTxnId (R-41-06),
   strict schema (R-41-07), injection screen (R-41-08) ✓

**Stage B — Syntactic**
5. PEM present, single block, base64 clean, strict DER, version 0
   (R-42-01..05) ✓
6. No PRIVATE KEY block (R-42-06); EC params / algorithm params strict
   (G-06, G-07) ✓

**Stage C — Cryptographic**
7. Proof of Possession — verify CSR self-signature (R-43-01) ✓
8. SHA-256+ signature (R-43-02), alg/key consistent (R-43-03) ✓
9. RSA ≥2048 (R-43-04) or ECDSA P-256/384 (R-43-06); exponent (R-43-05) ✓
10. Weak/compromised-key screen (R-43-07); not a revoked-compromise key
    (R-43-08) ✓
11. Same key under different subject → alert (R-43-09); key not reused
    across cert types (R-43-10) ✓

**Stage D — Subject DN**
12. Length bounds, ISO country `IN`, string types, no invisible/placeholder
    chars, whitespace normalized (R-44-01..06) ✓
13. CN `service-a` is a service identity name, **not** an FQDN
    (D-CN-pers M; D-CN-host F; D-CN-nohost S) ✓
14. `O=Salman Technologies Pvt Ltd` equals the requester's vetted org
    (R-44-07) ✓
15. Homoglyph screen on CN/O (R-44-08); no reserved names (R-44-10) ✓

**Stage E — Extensions**
16. cA=TRUE forbidden, keyCertSign/cRLSign forbidden, anyEKU forbidden
    (R-45-01..03) ✓
17. **EKU = clientAuth present (M); serverAuth ABSENT (F)** — the key
    TLS_CLIENT check (R-45-04) ✓
18. KeyUsage = digitalSignature (M); consistent with key algorithm
    (R-45-05) ✓
19. SAN optional; if UPN otherName present it must match directory later;
    SAN count cap + no duplicates (R-45-08) ✓
20. Unknown extension OIDs rejected; client SKID/AKID ignored (R-45-06/07) ✓

**Stage F — Type-Specific Control (identity binding)**
21. `service-a` exists in the service inventory / CMDB (R-52-01) ✓
22. **Requester owns `service-a`** — registered owner/deployer team; NOT
    another team's service (R-52-02) — the impersonation guard ✓
23. If UPN/rfc822Name SAN present, exact directory match (R-52-04);
    asset status ACTIVE (R-52-06) ✓

**Stage G — Identity Vetting**
24. Requester affiliation + authority (R-60-03); current subscriber
    agreement (R-60-05); evidence freshness (R-60-04) ✓
25. Officer conflict-of-interest bar (R-60-08); velocity check (R-60-07) ✓

**Stage H — Workflow & Issuance**
26. State machine (R-70-01); separation of duties (R-70-02); single-officer
    approval (auto-approve S once binding passes) ✓
27. Validity clamped ≤1 year (R-70-06); modification voids approval
    (R-70-04); pending-expiry (R-70-05) ✓
28. Pre-issuance lint (R-70-07); send to CA; verify-back subject/SPKI/
    EKU/dates match approval (R-70-08) ✓
29. Leaver/mover hooks armed for `service-a`'s owner (R-AD-16/17);
    append-only audit of every verdict (R-70-10) ✓

**Reject example for this exact CSR:** if `service-a` were changed to
`CN=payments-gateway` (another team's service), Stage F step 22 fails
(R-52-02, impersonation) → **PKI_AUTHZ_003**, even though Stages A–E all
pass. If EKU included `serverAuth`, step 17 fails (type mismatch). If CN
were `api.salmantech.com` (FQDN), step 13 fails (D-CN-host forbidden).

---

## 7. References

**Normative:** RFC 2119, 2986, 5280, 6125, 7468, 8555, 8659, 9495, 6962,
3161; CA/Browser Forum TLS BR (SC-081, SC-067/MPIC), Code Signing BR,
S/MIME BR; ETSI EN 319 411-1/-2; FIPS 186-5, 204/205; NIST SP 800-57,
800-131A; CCA India IVG & CP.

**Companion internal specs:** RA-SPEC-001 (requirement text + Java/BC/
Spring examples), RA-SPEC-002 (problem/solution threat analysis),
master validation catalog (Tiers 0–14 + addendum G-01..16).

---

## Appendix A. One-Glance Applicability Summary

```
                         SRV    CLI    MIME   CODE   DOC
Common A–C (parse/PoP/key) M     M      M      M      M
Dominant control          DCV   ident  MCV    attest KYC
                          +MPIC  bind   +CAA-  +call- +video
                          +CAA   (own)  mail   back    ≤2d
Defining EKU             srvAuth cliAuth email  code   (none)+
                                              signing  nonRep
Identity vetting depth    DV/OV  regis  sponsor full   full
                                 -try          legal   KYC
Approval                  auto   1 off  auto/1 2 off   1 off
                          (DV)                (manual) (manual)
Validity cap (2026)       200d   1y     824d   1y      3y
Key floor                 2048   2048   2048   3072    2048
SAN                       dNS(M) opt    email  none(F) opt
Auto-approve allowed?     yes    yes    yes    NO      NO
```

*End of RA-SPEC-003*
