```text
Internal Specification                              Salman Technologies
Category: Standards Track (Internal)                        PKI-RA Team
                                                            RA-SPEC-001
                                                            4 July 2026


        Registration Authority (RA) Requirements for Validation of
              Client-Submitted Certificate Signing Requests
```

## Abstract

This document specifies the complete set of validations that an
enterprise Registration Authority (RA) MUST, SHOULD, or MAY perform
when a Certificate Signing Request (CSR) is received from a client,
before the request is forwarded to a Certification Authority (CA).
Requirements are specified for five certificate types: TLS Server,
TLS Client, S/MIME, Code Signing, and Document Signing. Each
requirement states the check, the requirement level, the rationale
(why), and the governing standard.

## Status of This Memo

Internal specification for architecture review. Aligned with:
RFC 2986, RFC 5280, RFC 6125, RFC 8555, RFC 8659, CA/Browser Forum
TLS Baseline Requirements (incl. Ballot SC-081v3, effective
2026-03-15), Code Signing Baseline Requirements (2026), S/MIME
Baseline Requirements, ETSI EN 319 411, CCA India IVG.

---

## Table of Contents

```
1.  Introduction
2.  Terminology and Conventions
3.  Validation Pipeline Model
4.  Common Validations (All Certificate Types)
    4.1  Transport and Request Layer
    4.2  Syntactic Validation (PKCS#10)
    4.3  Cryptographic Validation
    4.4  Subject Distinguished Name Validation
    4.5  Requested Extensions Validation
5.  Certificate-Type-Specific Validations
    5.1  TLS Server Certificates
    5.2  TLS Client Certificates
    5.3  S/MIME Certificates
    5.4  Code Signing Certificates
    5.5  Document Signing Certificates
6.  Identity Vetting and Risk Screening
7.  Workflow and Issuance Controls
8.  Security Considerations
9.  References
Appendix A.  Requirement Count Summary
Appendix B.  Certificate Type / Standard Cross-Reference
```

---

## 1. Introduction

An RA is the trust gatekeeper between subscribers and the CA. The CA
signs whatever the RA approves; therefore every mis-issued
certificate is an RA validation failure. This specification
enumerates all validations in execution order — from the cheapest
byte-level check to the most expensive human vetting — so that
invalid requests fail fast and expensive checks run only on requests
that deserve them.

**Design principle:** *Validate in increasing order of cost;
fail closed; log every verdict.*

## 2. Terminology and Conventions

The key words "MUST", "MUST NOT", "SHOULD", "SHOULD NOT", and "MAY"
are to be interpreted as described in RFC 2119.

| Term | Meaning |
|------|---------|
| CSR | Certification request per RFC 2986 (PKCS#10) |
| PoP | Proof of Possession of the private key |
| DCV | Domain Control Validation |
| MCV | Mailbox Control Validation |
| SPKI | SubjectPublicKeyInfo (the public key structure) |
| BR / CSBR / SMBR | CABF Baseline Requirements: TLS / Code Signing / S/MIME |

Requirement IDs are `R-<section>-<nn>`. Every requirement row states
**Why** — the risk that materialises if the check is skipped.

## 3. Validation Pipeline Model

```
   Client ──CSR──▶ ┌──────────────────────────────────────────┐
                   │ Stage 1  Transport/Request   (Sec 4.1)   │ fast,
                   │ Stage 2  Syntactic           (Sec 4.2)   │ cheap,
                   │ Stage 3  Cryptographic       (Sec 4.3)   │ automated
                   │ Stage 4  Subject DN          (Sec 4.4)   │
                   │ Stage 5  Extensions          (Sec 4.5)   │
                   │ Stage 6  Type-Specific       (Sec 5)     │
                   │ Stage 7  Identity & Risk     (Sec 6)     │ slow,
                   │ Stage 8  Workflow/Approval   (Sec 7)     │ human
                   └──────────────────┬───────────────────────┘
                                      ▼ only if ALL pass
                                     CA
```

A request MUST NOT proceed to stage N+1 with a failure in stage N.
A validator error or timeout MUST be treated as failure (fail-closed).

---

## 4. Common Validations (All Certificate Types)

### 4.1 Transport and Request Layer

| ID | Lvl | Validation | Why | Ref |
|----|-----|-----------|-----|-----|
| R-41-01 | MUST | Accept only TLS 1.2+ on the submission endpoint | Protect CSR + credentials in transit | RFC 8446 |
| R-41-02 | MUST | Authenticate the caller (mTLS / OIDC / API key) | Anonymous CSRs invite abuse and DoS | internal CP |
| R-41-03 | MUST | Authorize caller for the requested certificate type (RBAC) | A build server must not order S/MIME certs | RFC 3647 §4.1 |
| R-41-04 | MUST | Enforce payload size cap (e.g. 128 KB) before parsing | Memory-exhaustion DoS via giant "CSR" | — |
| R-41-05 | MUST | Enforce per-client rate limits | Enumeration and flood protection | — |
| R-41-06 | MUST | Enforce unique client transaction ID (idempotency) | Replay/duplicate processing | — |
| R-41-07 | MUST | Validate request schema strictly; reject unknown fields | Smuggled parameters bypass later checks | — |
| R-41-08 | SHOULD | Screen free-text fields for injection (XSS/SQLi) | DN values are rendered in consoles and stored in DBs | — |
| R-41-09 | MAY | Enforce per-tenant source-IP allowlists | Defense in depth for API channels | — |

### 4.2 Syntactic Validation (PKCS#10)

| ID | Lvl | Validation | Why | Ref |
|----|-----|-----------|-----|-----|
| R-42-01 | MUST | CSR payload present and non-empty | Fail fast, precise error | — |
| R-42-02 | MUST | Valid PEM armor `CERTIFICATE REQUEST`; exactly one block | Multi-block/garbage input hides content from review | RFC 7468 |
| R-42-03 | MUST | Base64 decodes cleanly, no trailing data | Trailing bytes = smuggling/parser confusion | RFC 7468 |
| R-42-04 | MUST | DER parses as `CertificationRequest`; reject BER/indefinite length | Parser-differential attacks: RA and CA seeing different content | RFC 2986 |
| R-42-05 | MUST | PKCS#10 `version` == 0 | Only defined version; anything else is malformed | RFC 2986 §4 |
| R-42-06 | MUST | Reject if a PRIVATE KEY block accompanies the CSR; permanently blocklist that key | The key is now compromised by definition | — |

### 4.3 Cryptographic Validation

| ID | Lvl | Validation | Why | Ref |
|----|-----|-----------|-----|-----|
| R-43-01 | MUST | Verify CSR self-signature (PoP) against embedded SPKI | Without PoP, an attacker binds someone else's public key to their name | RFC 2986 §3; BR 3.2.1 |
| R-43-02 | MUST | Signature algorithm ∈ {SHA-256/384/512 + RSA/ECDSA/EdDSA}; reject MD5, SHA-1 | Collision-broken hashes allow forged requests | NIST SP 800-131A |
| R-43-03 | MUST | Signature algorithm consistent with key type | RSA key with ECDSA sigAlg = malformed/hostile encoder | RFC 5280 |
| R-43-04 | MUST | RSA modulus ≥ 2048 (≥ 3072 for code signing); upper bound (e.g. 8192) | Weak keys factorable; oversized keys are a CPU-DoS vector | BR 6.1.5; CSBR 6.1.5 |
| R-43-05 | MUST | RSA exponent odd, ≥ 65537 | Small-exponent attacks (e=3) | FIPS 186-5 |
| R-43-06 | MUST | ECDSA curve ∈ {P-256, P-384}; point on curve, not at infinity | Invalid-curve attacks; non-standard curves unverifiable | RFC 5480; BR 6.1.5 |
| R-43-07 | MUST | Screen key against weak/compromised corpora: Debian PRNG, ROCA, Fermat-factorable, pwnedkeys/internal blocklist | Certs on broken or already-public keys are instantly exploitable | BR 6.1.1.3 |
| R-43-08 | MUST | Reject SPKI previously revoked for keyCompromise | Re-certifying a stolen key re-arms the thief | BR 4.9.1.1 |
| R-43-09 | SHOULD | Detect same SPKI submitted under a different subject | Strong signal of key theft or copy-paste key reuse | — |
| R-43-10 | SHOULD | Forbid key reuse across certificate types | Scope separation: TLS key ≠ signing key (non-repudiation dies) | NIST SP 800-57 §5.2 |
| R-43-11 | MAY | Accept ML-DSA / SLH-DSA (PQC) under a dedicated pilot profile | Controlled post-quantum migration path | FIPS 204/205 |

### 4.4 Subject Distinguished Name Validation

| ID | Lvl | Validation | Why | Ref |
|----|-----|-----------|-----|-----|
| R-44-01 | MUST | Enforce RFC 5280 upper bounds (CN ≤ 64, O ≤ 64, C = 2 …) | Oversized RDNs break relying-party software | RFC 5280 App. A |
| R-44-02 | MUST | C is a valid ISO 3166-1 alpha-2 code | Fake countries corrupt vetting and jurisdiction logic | BR 7.1.4.2 |
| R-44-03 | MUST | String types limited to UTF8String/PrintableString | TeletexString/BMPString cause display spoofing and interop bugs | RFC 5280 §4.1.2.4 |
| R-44-04 | MUST | Reject control chars, null bytes, RTL-override (U+202E), zero-width chars | Invisible-character name spoofing | — |
| R-44-05 | MUST | Reject metadata-only values ("-", "N/A", ".") | BR-prohibited placeholder junk in public certs | BR 7.1.4.2.2 |
| R-44-06 | MUST | Normalize whitespace; reject leading/trailing/double spaces | "Acme Corp " vs "Acme Corp" bypasses duplicate and vetting matching | — |
| R-44-07 | MUST | Subject O matches the requester's vetted organization record | Otherwise any authenticated user can claim any company | BR 3.2.2.1 |
| R-44-08 | SHOULD | Mixed-script / homoglyph screening on CN and O | "Аcme" (Cyrillic А) impersonation | — |
| R-44-09 | SHOULD | Reject deprecated attributes: OU (TLS), emailAddress in DN | CABF removed OU (2022); email belongs in SAN | BR 7.1.4.2; RFC 5280 |
| R-44-10 | MUST | Reject internal/reserved names in public profiles (localhost, RFC 1918 IPs, .local) | Publicly-trusted certs for unownable names | BR 7.1.4 |

### 4.5 Requested Extensions Validation

| ID | Lvl | Validation | Why | Ref |
|----|-----|-----------|-----|-----|
| R-45-01 | MUST | basicConstraints cA=TRUE → reject | End entity asking to become a CA = subordinate-CA forgery attempt | RFC 5280 §4.2.1.9 |
| R-45-02 | MUST | KeyUsage keyCertSign / cRLSign → reject | CA-only powers in an end-entity cert | RFC 5280 §4.2.1.3 |
| R-45-03 | MUST | EKU anyExtendedKeyUsage → reject | Unlimited-purpose cert defeats scope separation | RFC 5280 §4.2.1.12 |
| R-45-04 | MUST | EKU restricted to the profile of the requested type; cross-type combos (serverAuth+codeSigning) → reject | One compromised cert must not unlock multiple trust domains | BR 7.1.2 |
| R-45-05 | MUST | KeyUsage consistent with key algorithm (EC: no keyEncipherment) | Cryptographically meaningless bits confuse relying parties | RFC 5280 §4.2.1.3 |
| R-45-06 | MUST | Unknown/unprofiled extension OIDs → reject | Unreviewed extensions reach the signed cert | — |
| R-45-07 | MUST | Ignore client-supplied SKID/AKID/certificatePolicies/SCT values | These are RA/CA-authoritative fields | RFC 5280 |
| R-45-08 | SHOULD | Cap SAN entry count (e.g. 100) and reject duplicates | Abuse amplification and log bloat | — |

---

## 5. Certificate-Type-Specific Validations

### 5.1 TLS Server Certificates

*Purpose: prove server identity for HTTPS. Control-heavy, automatable.*

| ID | Lvl | Validation | Why | Ref |
|----|-----|-----------|-----|-----|
| R-51-01 | MUST | SAN dNSName present; CN (if any) repeated in SAN | Browsers ignore CN since 2017; SAN is the identity | RFC 6125; BR 7.1.4.5.2 |
| R-51-02 | MUST | FQDN syntax: LDH labels ≤ 63, total ≤ 253, no leading/trailing hyphen | Malformed names break resolvers and enable confusion | RFC 1035 §2.3 |
| R-51-03 | MUST | Name is registrable: not a bare public suffix (`*.com`, `co.in`) | A cert for `*.com` is a skeleton key for the internet | BR 3.2.2.4 (PSL) |
| R-51-04 | MUST | Wildcard only as entire leftmost label; never on a public suffix | `a*.x.com` / `*.co.in` are hostile patterns | BR 7.1.4.5.3 |
| R-51-05 | MUST | IDN: decode punycode, homograph-screen U-labels | `аpple.com` (Cyrillic) phishing | RFC 5890 |
| R-51-06 | MUST | **DCV per BR 3.2.2.4** for EVERY dNSName: DNS TXT change / HTTP token at `/.well-known/pki-validation/` / ACME challenges (RFC 8555) / constructed email (admin@…) | The single check that stops certificate-based phishing: requester must control the domain | BR 3.2.2.4 |
| R-51-07 | MUST | Wildcard SANs validated via DNS-based method only | HTTP file proves one host, not the whole namespace | BR 3.2.2.4 |
| R-51-08 | MUST | DCV evidence age ≤ 200 days (2026); ≤ 100 days from 2027-03; ≤ 10 days from 2029-03 | Domain ownership changes; stale proof = wrong owner | SC-081v3 |
| R-51-09 | MUST | CAA lookup on each domain ≤ 8h before issuance; our CA authorized | Domain owner's published issuance policy is binding | RFC 8659; BR 3.2.2.8 |
| R-51-10 | MUST | iPAddress SAN only with IP-control validation; no RFC 1918 IPs in public certs | Private IPs are unownable; public IPs need control proof | BR 3.2.2.5 |
| R-51-11 | MUST | Validity ≤ 200 days (2026-03-15 onward; 100d → 2027, 47d → 2029) | Shrinking exposure window for stolen certs — plan automation NOW | SC-081v3 |
| R-51-12 | MUST | EKU = serverAuth (+ clientAuth per profile only) | Scope separation | BR 7.1.2.7 |
| R-51-13 | SHOULD | High-value/phishing-list domain screening → manual review | Look-alike bank/brand domains pass DCV; risk check catches them | — |
| R-51-14 | MUST | OV/EV: org vetting per Section 6 in addition to DCV | Identity in the cert must be real, not just domain control | BR 3.2.2.1; EVG |

### 5.2 TLS Client Certificates

*Purpose: prove client identity for mTLS. Identity binding is the core.*

| ID | Lvl | Validation | Why | Ref |
|----|-----|-----------|-----|-----|
| R-52-01 | MUST | Subject identity exists in an authoritative registry: HR/AD (human), CMDB/MDM (service/device) | The cert is only as true as the registry lookup behind it | RFC 3647 §3.2.3 |
| R-52-02 | MUST | Requester owns or administers that identity | Blocks the #1 client-cert attack: requesting a colleague's/another service's name | — |
| R-52-03 | MUST | EKU = clientAuth only; serverAuth absent | A client cert must never be able to impersonate a server | RFC 5280 §4.2.1.12 |
| R-52-04 | MUST | UPN/rfc822Name SAN matches directory value exactly | Smartcard logon maps via UPN; mismatch = broken or spoofed logon | MS KB; RFC 4043 |
| R-52-05 | SHOULD | CN must not look like an FQDN | Type-confusion smell — likely a mis-routed server request | — |
| R-52-06 | SHOULD | Device certs: asset status ACTIVE in CMDB | Retired/lost devices must not get fresh credentials | — |
| R-52-07 | SHOULD | Validity ≤ 1 year (policy) | Client population churns faster than servers | internal CP |

### 5.3 S/MIME Certificates

*Purpose: email signing/encryption. Mailbox control + (sponsor) identity.*

| ID | Lvl | Validation | Why | Ref |
|----|-----|-----------|-----|-----|
| R-53-01 | MUST | SAN rfc822Name present, RFC 5321-valid mailbox syntax | Mail clients match on SAN, not CN | SMBR 7.1.4.2.1 |
| R-53-02 | MUST | **MCV**: random-value challenge to the exact mailbox, OR domain-validated + enterprise attestation of mailbox assignment | Without mailbox control, anyone gets a cert to read/sign as `salman@…` | SMBR 3.2.2 |
| R-53-03 | MUST | Mail domain on the org's verified domain list | Org may only sponsor mailboxes in domains it owns | SMBR 3.2.2.3 |
| R-53-04 | MUST | MCV/domain evidence ≤ 398 days old | Mailbox ownership changes (employee exits!) | SMBR 4.2.1 |
| R-53-05 | MUST | Profile = Strict or Multipurpose (Legacy retired July 2025) | New enrolments on a dead profile fail public trust | SMBR 7.1 |
| R-53-06 | MUST | Sponsor-validated: CN person verified against HR; identity evidence ≤ 825 days | The human name in the cert must be vetted, not self-asserted | SMBR 3.2.4 |
| R-53-07 | MUST | EKU = emailProtection; KU per key type (RSA: keyEncipherment; EC: keyAgreement) | Scope separation + working encryption | SMBR 7.1.2 |
| R-53-08 | MUST | Validity ≤ 824 days (Strict/Multipurpose) | SMBR hard cap | SMBR 6.3.2 |
| R-53-09 | MAY | Key escrow for encryption certs, dual-control recovery | Business continuity vs insider-abuse trade-off — signing keys NEVER escrowed | SMBR 6.2.1 |

### 5.4 Code Signing Certificates

*Purpose: OS-trusted publisher identity. Identity-heavy; misuse = signed malware.*

| ID | Lvl | Validation | Why | Ref |
|----|-----|-----------|-----|-----|
| R-54-01 | MUST | RSA ≥ 3072 / ECDSA P-256+ | CSBR floor is higher than TLS: signatures outlive the cert by years | CSBR 6.1.5 |
| R-54-02 | MUST | CN = verified legal name of org/individual; O and C mandatory and registry-consistent | This exact string becomes the OS "Publisher:" prompt users trust | CSBR 7.1.4.2 |
| R-54-03 | MUST | No SAN; EKU = codeSigning only; KU = digitalSignature only | A code-signing cert with serverAuth is a cross-domain skeleton key | CSBR 7.1.2 |
| R-54-04 | MUST | **Private key in FIPS 140-2 L2 / CC EAL4+ hardware, verified via key attestation** (attestation chain to HSM vendor root) or equivalent audited evidence | Since 2023-06-01 software keys are banned: stolen soft keys are how malware gets signed | CSBR 6.2.7.4 |
| R-54-05 | MUST | Org legal existence via govt registry/QIIS (MCA/ROC in India); operational status ACTIVE | Shell companies are the standard malware-signing front | CSBR 3.2.2 |
| R-54-06 | MUST | Verified callback via independently-sourced contact (never applicant-supplied) confirming request + requester authority | Applicant-supplied phone numbers verify only the attacker | CSBR 3.2.5 |
| R-54-07 | MUST | Malware/abuse screening: applicant + key fingerprints vs malware DBs, prior revoked-for-abuse certs, typosquat detection ("Microsofft") | Technically perfect CSRs from bad actors are the norm, not the exception | CSBR 4.2.1 |
| R-54-08 | MUST | Sanctions/denied-party screening (org and individuals) | Legal compliance; export control | — |
| R-54-09 | MUST | Validity ≤ 1 year (effective 2026-02-15; previously 39 months) | Industry shrank the blast radius of leaked signing certs | CSBR 6.3.2 |
| R-54-10 | MUST | EV: subject serialNumber = registry number, verified | EV promise = machine-checkable legal identity | CSBR/EVG |
| R-54-11 | SHOULD | Direct subscriber to RFC 3161 timestamping | Signatures must verify after cert expiry | RFC 3161 |

### 5.5 Document Signing Certificates

*Purpose: legally-binding personal/org signatures. Full KYC.*

| ID | Lvl | Validation | Why | Ref |
|----|-----|-----------|-----|-----|
| R-55-01 | MUST | CN = verified natural person / legal entity name | The name IS the legal signature on documents | ETSI EN 319 411-1 |
| R-55-02 | MUST | KU includes nonRepudiation (contentCommitment) | Without it, "I never signed this" remains legally arguable | RFC 5280 §4.2.1.3 |
| R-55-03 | MUST | No TLS/code-signing EKUs | A document cert must not authenticate servers | — |
| R-55-04 | MUST | India (CCA): identity per IVG — Aadhaar eKYC / PAN + attested docs / bank KYC, PLUS video verification ≤ 2 days before issuance | Regulator-mandated identity assurance; stale video = failed audit | CCA IVG |
| R-55-05 | MUST | India (CCA): key generated in FIPS 140-2 L2 crypto token; validity ≤ 3 years (1/2/3) | IT Act legal validity depends on CCA-compliant key custody | CCA CP §6.1.1 |
| R-55-06 | MUST | Org-person certs: employment/affiliation proof + org authorization | Person signs *on behalf of* org — both identities need vetting | ETSI EN 319 411-1 |
| R-55-07 | SHOULD | EU (eIDAS qualified): QSCD + qcStatements + face-to-face-equivalent proofing | Qualified signature = handwritten-equivalent; bar is highest | eIDAS; ETSI EN 319 411-2 |

---

## 6. Identity Vetting and Risk Screening (Cross-Type)

Applied per the depth demanded by the certificate type (Sec 5).

| ID | Lvl | Validation | Why | Ref |
|----|-----|-----------|-----|-----|
| R-60-01 | MUST | Org legal existence + exact legal-name match from authoritative registry | The O= field is a legal claim; RA is its guarantor | BR 3.2.2.1 |
| R-60-02 | MUST | Verification contacts sourced independently (registry/QIIS), never from the application | Applicant-supplied contacts only ever confirm the applicant | BR 3.2.2.1; CSBR 3.2.5 |
| R-60-03 | MUST | Requester affiliation + authority to request for the org | Authenticated ≠ authorized: an intern must not order the company's signing cert | RFC 3647 §3.2.5 |
| R-60-04 | MUST | Vetting-evidence freshness windows enforced (825d org identity, shrinking DCV per SC-081, 2d video-KYC for India DSC) | Stale evidence = certifying yesterday's truth | per-standard |
| R-60-05 | MUST | Signed, current subscriber agreement covering the cert type | Legal basis for obligations and revocation | RFC 3647 §4.5 |
| R-60-06 | MUST | Sanctions and internal denied-applicant screening | Legal exposure; repeat abusers return with clean CSRs | — |
| R-60-07 | SHOULD | Velocity/anomaly detection on request patterns | Compromised requester accounts order certs in bursts | — |
| R-60-08 | MUST | RA officer conflict-of-interest bar (cannot vet own org/request) | Insider threat is an audit-level finding | ETSI EN 319 411-1 |

## 7. Workflow and Issuance Controls

| ID | Lvl | Validation | Why | Ref |
|----|-----|-----------|-----|-----|
| R-70-01 | MUST | State machine with only legal transitions (SUBMITTED → VALIDATED → APPROVED → SENT_TO_CA → ISSUED) | Skipped states = skipped validations | — |
| R-70-02 | MUST | Separation of duties: submitter ≠ approver, DB-enforced | UI-only enforcement is bypassable | RFC 3647 §5.2.4 |
| R-70-03 | MUST | Dual officer approval for code signing / EV | Highest-impact types get two pairs of eyes | CSBR |
| R-70-04 | MUST | Any post-approval modification voids the approval | Approve-then-swap attack | — |
| R-70-05 | MUST | Pending requests expire; stale approvals unusable | Evidence behind old approvals decays | — |
| R-70-06 | MUST | Validity clamped to type cap at submission AND at CA-send | Rules change between request and issuance (SC-081!) | — |
| R-70-07 | MUST | Pre-issuance lint (zlint/cablint) of the to-be-signed profile | Catch BR violations before the CA signs them into history | — |
| R-70-08 | MUST | Verify CA response: subject/SPKI/extensions match approved request; chain valid; dates sane; serial ≥ 64-bit CSPRNG | The RA must confirm the CA issued exactly what was approved | BR 7.1 |
| R-70-09 | MUST | CT logging for public TLS (≥ 2 qualified logs) | Chrome/Safari reject un-logged certs; transparency detects mis-issuance | RFC 6962 |
| R-70-10 | MUST | Append-only audit log of every verdict + evidence reference; retention ≥ regulatory minimum (2y BR post-expiry; 7y CCA) | WebTrust/ETSI/CCA audits reconstruct decisions from logs alone | RFC 3647 §5.4/5.5 |

## 8. Security Considerations

1. **Fail closed.** A validator outage MUST reject, never skip — attackers time their requests to outages.
2. **Parser hygiene.** The RA and CA MUST parse identically (strict DER); parser differentials are a mis-issuance class.
3. **The RA is the target.** Compromise of one RA officer account defeats all Section 6 controls — hence MFA (R-41-02), maker-checker (R-70-02), and conflict bars (R-60-08).
4. **Automation pressure.** With TLS validity falling to 47 days by 2029, manual TLS workflows will collapse; the architecture MUST automate Sections 4 + 5.1 completely while keeping Sections 5.4/5.5 human-gated.
5. **Evidence is the product.** An RA that cannot *prove* a validation happened has, for audit purposes, not performed it.

## 9. References

**Normative:** RFC 2119, RFC 2986, RFC 5280, RFC 6125, RFC 7468,
RFC 8555, RFC 8659, RFC 6962, RFC 3161, RFC 5321;
CABF TLS BR (+ SC-081v3), CSBR, SMBR; ETSI EN 319 411-1/-2;
FIPS 186-5, FIPS 204/205; NIST SP 800-57, SP 800-131A; CCA India IVG & CP.

**Informative:** RFC 3647 (CP/CPS framework), CABF EV Guidelines,
Mozilla Public Suffix List.

---

## Appendix A. Requirement Count Summary

```
Section 4.1  Transport/Request         9   (8 MUST, 1 MAY)
Section 4.2  Syntactic                 6   (6 MUST)
Section 4.3  Cryptographic            11   (8 MUST, 2 SHOULD, 1 MAY)
Section 4.4  Subject DN               10   (8 MUST, 2 SHOULD)
Section 4.5  Extensions                8   (7 MUST, 1 SHOULD)
Section 5.1  TLS Server               14   (13 MUST, 1 SHOULD)
Section 5.2  TLS Client                7   (4 MUST, 3 SHOULD)
Section 5.3  S/MIME                    9   (8 MUST, 1 MAY)
Section 5.4  Code Signing             11   (10 MUST, 1 SHOULD)
Section 5.5  Document Signing          7   (6 MUST, 1 SHOULD)
Section 6    Identity & Risk           8   (7 MUST, 1 SHOULD)
Section 7    Workflow                 10   (10 MUST)
────────────────────────────────────────────────────────────
Total                                110 requirements
```

## Appendix B. Certificate Type / Standard Cross-Reference

```
+------------------+----------------------------+---------------------------+
| Certificate Type | Primary Standards          | 2026 Hard Numbers         |
+------------------+----------------------------+---------------------------+
| TLS Server       | RFC 6125, 8555, 8659;      | validity <= 200d;         |
|                  | CABF TLS BR + SC-081v3     | DCV reuse <= 200d;        |
|                  |                            | CAA <= 8h; RSA >= 2048    |
+------------------+----------------------------+---------------------------+
| TLS Client       | RFC 5280; internal CP;     | validity <= 1y (policy);  |
|                  | RFC 4043 (UPN)             | RSA >= 2048               |
+------------------+----------------------------+---------------------------+
| S/MIME           | CABF S/MIME BR; RFC 5321   | validity <= 824d;         |
|                  | (Strict/Multipurpose only) | MCV reuse <= 398d;        |
|                  |                            | identity <= 825d          |
+------------------+----------------------------+---------------------------+
| Code Signing     | CABF CSBR; RFC 3161        | validity <= 1y;           |
|                  |                            | RSA >= 3072;              |
|                  |                            | FIPS 140-2 L2 hardware    |
+------------------+----------------------------+---------------------------+
| Document Signing | CCA India IVG/CP;          | validity <= 3y (India);   |
|                  | ETSI EN 319 411; eIDAS     | video KYC <= 2 days;      |
|                  |                            | FIPS 140-2 L2 token       |
+------------------+----------------------------+---------------------------+
```

*End of RA-SPEC-001*


