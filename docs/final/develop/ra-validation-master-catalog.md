# Enterprise RA — Master Validation Catalog

> Deep-research based complete list of validations for building an enterprise Registration Authority.
> Sources: RFC 2986 (PKCS#10), RFC 5280, CA/Browser Forum TLS BR (incl. SC-081v3, effective Mar 2026),
> Code Signing BR (CSBR, incl. Feb 2026 1-year cap), S/MIME BR, ETSI EN 319 411, WebTrust for RA, NIST SP 800-57.
> v1.1 additions cross-checked against worldwide RA implementations: EJBCA (Keyfactor), Boulder
> (Let's Encrypt), Dogtag PKI, OpenXPKI, MS AD CS/NDES — plus 2025–26 CABF ballots SC-067 (MPIC),
> SMC-05 (CAA issuemail), SC-063 (short-lived certs).
> Last updated: 2026-07-05

## How to read this

Validations are ordered **small to big** — from the cheapest byte-level checks (fail fast, no crypto needed)
up to expensive human vetting and post-issuance verification. Har tier previous tier pass hone ke baad hi
chalna chahiye — isse DoS resistance milti hai aur error messages precise rehte hain.

```
Tier  0  Transport / HTTP           (microseconds, no parsing)
Tier  1  Authentication & Session   (who is calling?)
Tier  2  Authorization / RBAC       (are they allowed to ask this?)
Tier  3  Request Body / Schema      (is the envelope sane?)
Tier  4  CSR Syntactic              (does it parse?)
Tier  5  CSR Cryptographic          (is the math right?)
Tier  6  Subject DN Content         (are the names right?)
Tier  7  Extensions & Attributes    (are the X.509 asks right?)
Tier  8  Certificate-Type Policy    (does it match the profile?)
Tier  9  Identity Vetting           (is the applicant real?)
Tier 10  Risk & Reputation          (should we trust them?)
Tier 11  Business Rules & Workflow  (does our process allow it?)
Tier 12  CA Submission & Issuance   (send + verify what comes back)
Tier 13  Lifecycle (renew/rekey/revoke)
Tier 14  Cross-Cutting (audit, evidence, time)
```

---

## Tier 0 — Transport / HTTP Layer

| ID | Validation | Detail |
|----|-----------|--------|
| T0-01 | TLS version minimum | Endpoint only accepts TLS 1.2+; TLS 1.0/1.1 reject |
| T0-02 | mTLS client certificate (if API channel) | Client cert present, not expired, not revoked, chains to internal trust anchor |
| T0-03 | mTLS cert-to-account binding | Client cert subject/SAN maps to the registered API account |
| T0-04 | HTTP method allowed | Only POST on submit endpoints; 405 otherwise |
| T0-05 | Content-Type check | `application/json` (ya jo defined hai); reject others |
| T0-06 | Content-Length / payload cap | Total request ≤ configured max (e.g. 128 KB) — memory DoS se bachao |
| T0-07 | Rate limiting per client | Requests/min per API key + per IP; 429 on breach |
| T0-08 | Global throughput guard | Circuit breaker — downstream CA overload protection |
| T0-09 | Request timestamp skew | Signed requests: timestamp within ±5 min (replay window) |
| T0-10 | Duplicate request replay | Nonce/`clientTxnId` never seen before (idempotency store) |
| T0-11 | IP allowlist (optional, per tenant) | Enterprise clients often lock API source ranges |
| T0-12 | Header sanity | Reject oversized headers, suspicious encodings, HTTP smuggling patterns |

## Tier 1 — Authentication & Session

| ID | Validation | Detail |
|----|-----------|--------|
| T1-01 | Credential validity | AD/LDAP/OIDC token valid, signature verified, not expired |
| T1-02 | Token audience & issuer | `aud`/`iss` match this RA — token borrowed from another app reject |
| T1-03 | Account status | Not locked, not disabled, not deprovisioned in HR feed |
| T1-04 | MFA enforced for RA officers | Approval-capable accounts must have MFA-backed session |
| T1-05 | Session freshness for approvals | Re-auth ya step-up before approve/reject action |
| T1-06 | Service account restrictions | Service accounts sirf submit kar sakte hain, approve nahi |
| T1-07 | Concurrent session policy | Optional: one active officer session per user |

## Tier 2 — Authorization / RBAC

| ID | Validation | Detail |
|----|-----------|--------|
| T2-01 | Role permits operation | REQUESTER can submit; APPROVER can approve; AUDITOR read-only |
| T2-02 | Role permits certificate type | e.g. sirf release-engineering group CODE_SIGNING maang sakta hai |
| T2-03 | Tenant/org scoping | User apne org ke domains/identities ke liye hi request kar sakta hai |
| T2-04 | Domain authorization list | Requested FQDN org ke pre-approved domain list mein hai |
| T2-05 | Email domain authorization | S/MIME: rfc822Name domain org ka verified mail domain hai |
| T2-06 | Quota check | Per-user / per-org outstanding-request and issued-cert quotas |
| T2-07 | Enrollment eligibility | User onboarding complete, subscriber agreement signed & current |
| T2-08 | Delegation validity | Agar on-behalf-of request hai: delegation record exists & unexpired |

## Tier 3 — Request Body / Schema

| ID | Validation | Detail |
|----|-----------|--------|
| T3-01 | JSON well-formed | Parse failure → 400, no stack trace leak |
| T3-02 | Schema validation | Required fields present, unknown fields rejected (strict mode) |
| T3-03 | Field type & length limits | Every string field bounded; numbers range-checked |
| T3-04 | Enum values valid | certificateType ∈ {TLS_SERVER, TLS_CLIENT, SMIME, CODE_SIGNING, DOCUMENT_SIGNING} |
| T3-05 | clientTxnId format + uniqueness | UUID format; DB unique constraint |
| T3-06 | requestedValidityDays sane | Positive integer, ≤ policy max for the type |
| T3-07 | Injection-safe content | DN/free-text fields screened — cert fields UIs mein render hote hain (stored XSS via CN is real) |
| T3-08 | Character encoding | UTF-8 valid; no null bytes, no control characters |

---

## Tier 4 — CSR Syntactic

| ID | Validation | Detail |
|----|-----------|--------|
| T4-01 | CSR payload present | Non-empty after trim |
| T4-02 | CSR size limit | e.g. ≤ 64 KB standalone cap |
| T4-03 | PEM armor valid | `-----BEGIN CERTIFICATE REQUEST-----` / END markers match; `NEW CERTIFICATE REQUEST` legacy marker policy decision |
| T4-04 | Base64 decodes | Clean decode, no garbage trailing data |
| T4-05 | Single CSR only | Exactly one PEM block — multi-block reject |
| T4-06 | ASN.1/DER parse | Valid PKCS#10 `CertificationRequest` structure |
| T4-07 | DER strictness | BER/indefinite-length encodings reject (parser-differential attacks) |
| T4-08 | Version field = 0 | PKCS#10 version must be v1 (integer 0) |
| T4-09 | No embedded private key | Log/PEM paste accidents — agar `PRIVATE KEY` block bhi aa gaya to reject AND alert (key compromised, block that key forever) |

## Tier 5 — CSR Cryptographic

| ID | Validation | Detail |
|----|-----------|--------|
| T5-01 | Proof of Possession | CSR self-signature verifies against embedded public key |
| T5-02 | Signature algorithm allowlist | SHA-256/384/512 with RSA/ECDSA/Ed25519; MD5, SHA-1 reject |
| T5-03 | Signature/key algorithm consistency | sigAlg matches key type (RSA key + ECDSA sig = malformed) |
| T5-04 | RSA key size minimum | TLS ≥ 2048; CODE_SIGNING ≥ 3072 (CSBR); internal policy ceiling too (e.g. ≤ 8192 — CPU DoS) |
| T5-05 | RSA public exponent | e ≥ 65537, odd; e=3 reject |
| T5-06 | RSA modulus sanity | Odd modulus, not small-prime divisible, no shared factors with known corpus (batch GCD optional) |
| T5-07 | ROCA vulnerability check | Infineon fingerprint test on RSA modulus |
| T5-08 | Fermat-factorable keys | Close-prime check (p≈q) |
| T5-09 | Debian weak key blocklist | Historical OpenSSL PRNG bug keys |
| T5-10 | Compromised-key blocklist | pwnedkeys / internal compromise list — SPKI hash lookup |
| T5-11 | ECDSA curve allowlist | P-256, P-384 (P-521 policy decision); no secp256k1, no custom curves |
| T5-12 | EC point validity | Point on curve, not point at infinity |
| T5-13 | EdDSA policy | Ed25519 allowed? (CABF TLS: no; internal PKI: maybe) |
| T5-14 | PQC algorithm policy | ML-DSA (Dilithium/FIPS 204), SLH-DSA accepted for pilot profiles; hybrid CSR handling defined |
| T5-15 | Duplicate public key — same subject | Same SPKI already active for this subject? Renewal vs re-key policy |
| T5-16 | Duplicate public key — cross subject | Same SPKI under DIFFERENT subject = key sharing/theft indicator → reject + alert |
| T5-17 | Key reuse across cert types | TLS key ko S/MIME/code-signing mein reuse karna reject |
| T5-18 | Revoked-key resubmission | Key from a previously revoked (keyCompromise) cert → permanent reject |

## Tier 6 — Subject DN Content

| ID | Validation | Detail |
|----|-----------|--------|
| T6-01 | CN present (per profile) | Ya deliberately absent (modern TLS allows CN-less, SAN-only) — profile decides |
| T6-02 | RFC 5280 upper bounds | CN ≤ 64, O ≤ 64, OU ≤ 64, C = 2, email ≤ 255 chars |
| T6-03 | C is valid ISO 3166-1 alpha-2 | `IN`, `US` ✓; `XX`, `UK`(policy) ✗ |
| T6-04 | String type correctness | UTF8String/PrintableString as per RFC 5280; TeletexString/BMPString reject |
| T6-05 | No leading/trailing/double whitespace | Normalization before compare & store |
| T6-06 | No control/invisible characters | Zero-width space, RTL override (U+202E) — spoofing vectors |
| T6-07 | Homoglyph screening | Cyrillic 'а' in "Аcme Corp" — mixed-script detection |
| T6-08 | OU deprecated for TLS | CABF removed OU from TLS certs (2022) — strip or reject per profile |
| T6-09 | email in DN deprecated | emailAddress DN attribute → move to SAN rfc822Name |
| T6-10 | No metadata-only values | `CN=-`, `O=N/A`, `O=.` reject (BR prohibition) |
| T6-11 | DN attribute allowlist | Unknown/exotic OIDs in subject reject (e.g. dnQualifier unless profiled) |
| T6-12 | Internal name/reserved names | `localhost`, `*.local`, internal hostnames in public profiles reject |
| T6-13 | DN consistency with account | O matches the requester's vetted organization record |
| T6-14 | ST/L plausibility for O | Address fields match registered org record (OV/EV) |
| T6-15 | serialNumber attribute rules | EV: company registration number, matches registry |

---

## Tier 7 — Extensions & Requested Attributes

| ID | Validation | Detail |
|----|-----------|--------|
| T7-01 | Extension request attribute parse | `extensionRequest` (1.2.840.113549.1.9.14) well-formed |
| T7-02 | Extension allowlist | Unknown/unprofiled extension OIDs reject |
| T7-03 | basicConstraints cA=TRUE forbidden | End-entity CSR asking to be a CA = attack |
| T7-04 | pathLenConstraint absent | CA-only field |
| T7-05 | KeyUsage CA-only bits forbidden | keyCertSign, cRLSign reject |
| T7-06 | KeyUsage matches key type | RSA: digitalSignature/keyEncipherment; EC: no keyEncipherment (use keyAgreement rules) |
| T7-07 | EKU allowlist per profile | anyExtendedKeyUsage always reject; OCSPSigning/timeStamping only for dedicated profiles |
| T7-08 | EKU combination rules | serverAuth+codeSigning in one cert reject (scope separation) |
| T7-09 | SAN present & typed per profile | dNSName/iPAddress/rfc822Name/URI/otherName — sirf profile-allowed types |
| T7-10 | SAN count limit | e.g. ≤ 100 dNSNames — abuse/DoS cap |
| T7-11 | SAN duplicates | Duplicate entries collapse or reject |
| T7-12 | CN-in-SAN consistency | TLS: CN (if present) must also appear in SAN |
| T7-13 | Criticality flags | Extensions marked critical against profile rules |
| T7-14 | SKID/AKID not client-supplied | RA/CA compute these — client values ignore/reject |
| T7-15 | Certificate policies OID | Client shouldn't dictate policy OIDs — strip/reject |
| T7-16 | SCT / CT poison extension | Precert poison in CSR = malformed, reject |
| T7-17 | qcStatements (eIDAS profiles) | Only where qualified-cert profile allows |
| T7-18 | Challenge password attribute | PKCS#9 challengePassword: policy — ignore, ya SCEP flows mein verify |

## Tier 8 — Certificate-Type Policy Profiles

### 8A — TLS_SERVER

| ID | Validation | Detail |
|----|-----------|--------|
| T8A-01 | FQDN syntax (RFC 1035/5280) | Labels ≤ 63, total ≤ 253, LDH rule, no leading/trailing hyphen |
| T8A-02 | Public suffix / registrable domain | `*.com`, `co.in` bare public suffix reject (Mozilla PSL) |
| T8A-03 | Wildcard rules | Sirf leftmost label `*.example.com`; `*.*.x`, `a*.x`, wildcard-on-public-suffix reject |
| T8A-04 | IDN handling | Punycode `xn--` decode, homograph screening, U-label/A-label consistency |
| T8A-05 | IP address SAN policy | iPAddress allowed? Public profile: BR 3.2.2.5 IP control validation; private ranges (RFC 1918) public certs mein reject |
| T8A-06 | No underscore in dNSName | Policy per BR clarifications |
| T8A-07 | Domain Control Validation (DCV) | BR 3.2.2.4 approved method: DNS TXT change, HTTP file `/.well-known/pki-validation/`, ACME (RFC 8555) DNS-01/HTTP-01/TLS-ALPN-01, constructed email (admin@/webmaster@...), phone-to-DNS-registered number |
| T8A-08 | DCV scope | Wildcard needs DNS-based method (HTTP method wildcard ke liye invalid) |
| T8A-09 | DCV reuse window | **≤ 200 days as of 2026-03-15** (SC-081; 100 days from 2027, 10 days from 2029) |
| T8A-10 | CAA record check (RFC 8659) | `issue`/`issuewild` permits our CA; checked ≤ 8 hrs before issuance |
| T8A-11 | Validity cap | **≤ 200 days as of 2026-03-15** (was 398; 100 days 2027, 47 days 2029) |
| T8A-12 | High-value domain list | Google/bank/govt domains — manual review always |
| T8A-13 | Domain not on phishing/abuse lists | Google Safe Browsing, internal blocklist |
| T8A-14 | EKU = serverAuth | clientAuth optional per profile; nothing else |

### 8B — TLS_CLIENT

| ID | Validation | Detail |
|----|-----------|--------|
| T8B-01 | Identity binding | CN/SAN maps to real user (HR/AD) ya device (CMDB/MDM) record |
| T8B-02 | Requester owns the identity | Salman apne liye hi maang sakta hai — ya device-owner authorization |
| T8B-03 | EKU = clientAuth only | serverAuth forbidden — role separation |
| T8B-04 | SAN type | rfc822Name ya otherName UPN (smartcard logon: `1.3.6.1.4.1.311.20.2.3`) per profile |
| T8B-05 | UPN matches directory | AD userPrincipalName exact match |
| T8B-06 | Device cert: asset active | CMDB says device in service, not retired/lost |

### 8C — S/MIME (per S/MIME BR)

| ID | Validation | Detail |
|----|-----------|--------|
| T8C-01 | rfc822Name syntax (RFC 5321/5322) | Valid mailbox format |
| T8C-02 | Mailbox Control Validation | Random-value challenge email to the exact address, ya domain-validated + org attests mailbox |
| T8C-03 | Mail domain org-authorized | Domain on the org's verified list (MX/ownership evidence) |
| T8C-04 | Profile class check | SMBR profiles: mailbox-validated / organization-validated / sponsor-validated / individual-validated |
| T8C-05 | Generation: legacy vs multipurpose vs strict | Extension strictness per chosen generation |
| T8C-06 | Personal name verification | Sponsor/individual profiles: CN=person verified against ID/HR |
| T8C-07 | EKU = emailProtection | serverAuth/codeSigning forbidden |
| T8C-08 | Validity cap | SMBR max (multipurpose/strict: 825 days; check profile) |
| T8C-09 | Encryption cert escrow policy | Agar key escrow (encryption certs only) — dual control on recovery |

### 8D — CODE_SIGNING (per CSBR)

| ID | Validation | Detail |
|----|-----------|--------|
| T8D-01 | CN = vetted org/individual name | Publisher name displayed by OS |
| T8D-02 | O + C mandatory | And consistent with registry record |
| T8D-03 | No SAN (typically) | Presence = suspicious |
| T8D-04 | RSA ≥ 3072 / ECDSA P-256+ | CSBR hard floor |
| T8D-05 | KU = digitalSignature only | |
| T8D-06 | EKU = codeSigning | serverAuth/clientAuth/emailProtection forbidden |
| T8D-07 | Hardware key protection | FIPS 140-2 L2 / CC EAL4+ token/HSM; verify via **key attestation** ya audited-provisioning evidence (mandatory since 2023-06-01) |
| T8D-08 | Attestation chain verify | Attestation cert chains to HSM vendor root; nonce fresh |
| T8D-09 | Validity cap | **≤ 1 year (as of 2026-02-15)** — pehle 39 months tha |
| T8D-10 | EV: registration number | serialNumber = company reg no., verified in MCA/ROC/QIIS |
| T8D-11 | Timestamping guidance | Subscriber ko RFC 3161 TSA use karna chahiye (signatures outlive cert) |

### 8E — DOCUMENT_SIGNING

| ID | Validation | Detail |
|----|-----------|--------|
| T8E-01 | CN = verified person/org name | Legal identity on documents |
| T8E-02 | KU includes nonRepudiation | contentCommitment bit mandatory |
| T8E-03 | No TLS EKUs | Scope isolation |
| T8E-04 | India DSC rules (if applicable) | CCA guidelines: identity KYC (video/eKYC/in-person), validity ≤ 3 years, crypto token mandatory for signing certs |
| T8E-05 | eIDAS qualified profile (if EU) | QSCD requirement, qcStatements, face-to-face-equivalent identity proofing |

---

## Tier 9 — Identity Vetting (the RA's core job)

| ID | Validation | Detail |
|----|-----------|--------|
| T9-01 | Org legal existence | Govt registry: MCA/ROC (IN), Companies House (UK), state registry (US), ya QIIS/DUNS |
| T9-02 | Org name exact match | DN `O=` exactly matches registered legal name (ya verified assumed name/DBA) |
| T9-03 | Org operational status | Active — dissolved/struck-off reject |
| T9-04 | Physical address verification | Registered address via registry/QIIS/site visit (EV) |
| T9-05 | Verified phone number | Number sourced from registry/QIIS — NOT from applicant |
| T9-06 | Verified callback | Call/verified channel confirm karta hai: yeh org ne request ki hai |
| T9-07 | Requester employment/affiliation | Requester actually belongs to the org (HR feed / signed authorization) |
| T9-08 | Requester authority | Org ka authorized rep hai — authorization letter, contract signer verification (EV) |
| T9-09 | Approver independence | Verification org ke DIFFERENT contact se, requester se nahi |
| T9-10 | Individual identity proofing | Govt photo ID + liveness (video KYC) ya in-person; ID authenticity checks |
| T9-11 | Identity evidence freshness | Vetting data reuse window: BR limits (org data 825 days → shrinking per SC-081 schedule); expired evidence = re-vet |
| T9-12 | Subscriber agreement | Signed, current version, covers requested cert type |
| T9-13 | EV operational existence | 3+ years registered, ya bank/financial evidence |
| T9-14 | Insider-threat guard | RA officer cannot vet own org/own request (conflict of interest rule) |

## Tier 10 — Risk & Reputation Screening

| ID | Validation | Detail |
|----|-----------|--------|
| T10-01 | Internal denied-applicant list | Previous fraud/abuse by org or person |
| T10-02 | Sanctions screening | OFAC SDN, EU/UN lists, local embargo lists — org AND individuals |
| T10-03 | Malware association check | Code signing: applicant name/keys vs malware-signing databases, prior revoked-for-abuse certs |
| T10-04 | Typosquat/brand-impersonation | "Microsofft Corp", "PayPa1" — fuzzy match against brand list → manual review |
| T10-05 | High-risk jurisdiction rules | Extra review for configured country lists |
| T10-06 | Velocity anomaly | Sudden spike in requests from one org/user — fraud signal |
| T10-07 | Previous revocation history | keyCompromise/misuse revocations on account → heightened review |
| T10-08 | Threat-intel feed check | Domain/org appears in phishing/APT reporting |

## Tier 11 — Business Rules & Workflow

| ID | Validation | Detail |
|----|-----------|--------|
| T11-01 | Duplicate open request | Same subject+type pending already → reject/attach |
| T11-02 | Existing active cert conflict | Policy: overlap allowed for rotation window only |
| T11-03 | Separation of duties (maker-checker) | Submitter ≠ approver — DB-enforced, not UI-only |
| T11-04 | Dual approval for high-risk types | CODE_SIGNING/EV: two officers |
| T11-05 | Approval state machine | Only legal transitions: SUBMITTED→VALIDATED→APPROVED→SENT_TO_CA→ISSUED; illegal jump reject |
| T11-06 | Pending request expiry | Auto-expire after N days; stale approvals cannot be executed |
| T11-07 | Re-validation on modification | Agar request edit hui approval ke baad → approval void, restart |
| T11-08 | Officer authority scope | Approver ka scope covers this org/type/validity |
| T11-09 | Batch request integrity | Bulk submissions: per-item validation, partial-failure semantics defined |
| T11-10 | Fee/entitlement check | Commercial RA: billing/contract entitles this issuance |

## Tier 12 — CA Submission & Issuance Verification

| ID | Validation | Detail |
|----|-----------|--------|
| T12-01 | Status is APPROVED | Never send anything else |
| T12-02 | Not already sent | Idempotent CA submission — caTransactionId single-use |
| T12-03 | Profile mapping correct | Internal type → CA profile ID mapping table validated |
| T12-04 | Validity clamp | Requested days ≤ CA/profile max (200d TLS, 1y code signing…) — clamp or reject |
| T12-05 | Pre-issuance linting | Run zlint/cablint on the to-be-signed data — catches BR violations before CA does |
| T12-06 | CT submission (public TLS) | Precert to ≥ 2 qualified CT logs; SCTs collected |
| T12-07 | CA callback authentication | Callback signed/authenticated; requestId exists; caTransactionId matches |
| T12-08 | Issued cert — subject match | Cert subject/SAN == approved request |
| T12-09 | Issued cert — SPKI match | Public key == CSR public key |
| T12-10 | Issued cert — chain validates | Chains to expected CA, correct intermediates |
| T12-11 | Issued cert — dates sane | notBefore ≈ now, notAfter within policy, not expired |
| T12-12 | Issued cert — extensions match profile | EKU/KU/policies as approved |
| T12-13 | Serial number entropy | ≥ 64 bits CSPRNG output (BR) — verify non-sequential |

## Tier 13 — Lifecycle (Renewal / Re-key / Revocation)

| ID | Validation | Detail |
|----|-----------|--------|
| T13-01 | Renewal identity still valid | Vetting data within reuse window, else re-vet |
| T13-02 | Renewal DCV re-check | Domain validation within current reuse window (200d → 10d trajectory) |
| T13-03 | Re-key = new key | Renewal with same key allowed?; re-key must present genuinely new SPKI |
| T13-04 | Revocation request authentication | Requester is subscriber, proves key control, ya authorized org contact |
| T13-05 | Revocation reason validation | keyCompromise requires evidence handling + 24 h clock (BR 4.9.1.1) |
| T13-06 | Revocation timelines | 24 h (compromise class) / 5 days (other BR reasons) SLA tracking |
| T13-07 | Post-revocation key block | Revoked-for-compromise SPKI → permanent blocklist (feeds T5-18) |
| T13-08 | Suspension rules (if supported) | On-hold semantics per CP/CPS |

## Tier 14 — Cross-Cutting (always on)

| ID | Validation | Detail |
|----|-----------|--------|
| T14-01 | Audit log every decision | Who/what/when/evidence-ref for each validation verdict — WebTrust/ETSI auditable |
| T14-02 | Audit log integrity | Append-only, hash-chained ya WORM storage |
| T14-03 | Evidence retention | Vetting evidence ≥ 7 years (BR: 2 years min after expiry; CSBR longer) |
| T14-04 | Trusted time source | NTP-synced, monitored — validity windows depend on it |
| T14-05 | Configuration change control | Policy/profile changes dual-controlled + versioned; validations evaluate against the version active at submission |
| T14-06 | Error message hygiene | Precise error codes to client, no internals leaked |
| T14-07 | PII handling | Vetting docs encrypted at rest, access-controlled, GDPR/DPDP retention |
| T14-08 | Fail-closed defaults | Any validator error/timeout = reject, never skip |

---

## Summary

```
Tier  0  Transport/HTTP        12 checks
Tier  1  Authentication         7 checks
Tier  2  Authorization          8 checks
Tier  3  Body/Schema            8 checks
Tier  4  CSR Syntactic          9 checks
Tier  5  CSR Cryptographic     18 checks
Tier  6  Subject DN            15 checks
Tier  7  Extensions            18 checks
Tier  8  Type Profiles         45 checks  (TLS-S 14, TLS-C 6, S/MIME 9, CodeSign 11, DocSign 5)
Tier  9  Identity Vetting      14 checks
Tier 10  Risk & Reputation      8 checks
Tier 11  Workflow              10 checks
Tier 12  CA Submission         13 checks
Tier 13  Lifecycle              8 checks
Tier 14  Cross-Cutting          8 checks
──────────────────────────────────────────
Total: 201 validations across 15 tiers
```

**Build order suggestion:** Tier 4–8 pehle banao (pure functions, easily unit-testable — aapka 7-layer
service isi zone mein hai), phir Tier 0–3 (framework/filter layer), phir Tier 11–12 (workflow + CA
integration), aur Tier 9–10 sabse aakhri mein kyunki wahan external registries/manual steps lagte hain.

---

## Addendum v1.1 — Gap Additions from Worldwide RA Review

Yeh checks standard RA/CA projects (EJBCA, Boulder/Let's Encrypt, Dogtag, OpenXPKI, AD CS/NDES)
aur 2025–26 ke naye CABF ballots ke against review karne pe mile — original 201 mein nahi the.

### New mandatory industry rules (2025–2026)

> **Scope note (WLCA).** G-01 (MPIC) and G-02 (DNSSEC-on-DCV) are
> **public-trust CABF requirements only** — they apply when the CA issues
> publicly-trusted TLS certificates over public-internet DCV. **They are
> NOT in WLCA's current scope**, which validates internal domains through
> Active Directory (approved-domain-list / computer-object), not public DNS
> token challenges. Keep them here for reference; implement only if WLCA
> ever issues publicly-trusted certificates.

| ID | Validation | Detail | Fits in |
|----|-----------|--------|---------|
| G-01 | **MPIC — Multi-Perspective Issuance Corroboration** *(public-trust only; out of WLCA scope)* | DCV **aur** CAA checks kam se kam 2 geographically separate network perspectives (≥500 km apart) se corroborate hon; single-vantage validation ab BR-non-compliant hai (SC-067, enforced 2025-09-15; perspectives count 2026 mein badh raha hai, multiple RIR regions) | Tier 8A (DCV ke saath) |
| G-02 | **DNSSEC validation on CAA/DCV lookups** *(public-trust only; out of WLCA scope)* | Primary perspective ke CAA DNS queries pe IANA root tak DNSSEC validation MANDATORY (2026-03-15 se); Boulder yeh pehle se karta hai — DNS spoofing se DCV bypass band | Tier 8A |
| G-03 | **CAA `issuemail` for S/MIME (RFC 9495)** | Email domain ke CAA record mein `issuemail` tag check karo — hamari CA authorized hai? (SMBR via SMC-05, mandatory 2025-03-15 se) | Tier 8C |
| G-04 | **WHOIS-sourced contacts retired** | DCV ke liye WHOIS/RDAP se nikale email/phone use MAT karo — 2024 ke .mobi WHOIS-server takeover research ke baad industry ne Domain-Contact methods retire kar diye | Tier 8A (DCV method list se remove) |
| G-05 | **Short-lived certificate profile (SC-063)** | ≤7-day certs (2026-03-15 se; pehle 10) revocation-exempt hain — alag profile flag, full DCV still required, CRL entry optional | Tier 8A / 12 |

### Encoding strictness (zlint/Boulder practice)

| ID | Validation | Detail | Fits in |
|----|-----------|--------|---------|
| G-06 | **Explicit EC parameters forbidden** | SPKI mein curve sirf namedCurve OID se aaye (RFC 5480); explicit/specifiedCurve parameters reject — parser bloat + hidden weak curve risk | Tier 5 |
| G-07 | **AlgorithmIdentifier params strict** | RSA ke liye params = NULL exactly; ECDSA sig ke liye params absent; mismatch = malformed encoder | Tier 5 |
| G-08 | **HTTP-01 redirect discipline** | DCV HTTP token fetch: sirf http/https redirects, sirf ports 80/443, redirect chain cap (e.g. 10), IP-literal redirects reject (Boulder rules) | Tier 8A |

### Enrollment protocol validations (jab SCEP/EST/ACME/CMP channel ho)

| ID | Validation | Detail | Fits in |
|----|-----------|--------|---------|
| G-09 | **SCEP challengePassword one-time + expiry** | NDES/SCEP flow mein challenge single-use, short TTL, device-bound (RFC 8894); static shared secret = classic AD CS attack path | Tier 1/2 |
| G-10 | **ACME External Account Binding (EAB)** | ACME account ko pre-registered enterprise account se bind karo (RFC 8555 §7.3.4) — anonymous ACME account enterprise profile na le sake | Tier 1/2 |
| G-11 | **ACME account key ≠ certificate key** | CSR jo account key se hi signed ho → reject (Boulder rule) — account takeover aur cert issuance collapse na ho | Tier 5 |
| G-12 | **EST/CMP channel auth** | EST (RFC 7030): TLS client-cert ya HTTP auth binding; CMP (RFC 9483): shared-secret/cert-based message protection verify | Tier 0/1 |

### Operational/organizational (WebTrust/BR audit ke liye)

| ID | Validation | Detail | Fits in |
|----|-----------|--------|---------|
| G-13 | **24/7 problem-reporting channel** | Key-compromise/misuse reports round-the-clock accept karo; compromise proof verify karo (e.g. signed nonce with the compromised key) — 24h revocation clock yahin se start hota hai (BR 4.9.3) | Tier 13 |
| G-14 | **Validation Specialist qualification** | Vetting karne wale personnel documented training + skills verification ke saath (BR 5.3.3); EJBCA/commercial RAs mein role-gated validation queues isi liye hain | Tier 14 |
| G-15 | **Data-source reliability evaluation** | Kisi registry/QIIS pe rely karne se PEHLE uski accuracy/manipulation-resistance evaluate + document karo (BR 3.2.2.7) — har "government-looking" site QIIS nahi hoti | Tier 9 |
| G-16 | **Pluggable validator architecture** | EJBCA pattern: key blacklist validator, domain blocklist validator, lint validator, external command validator — sab profile se attach hote hain; naya check = config, code nahi | Cross-cutting |

```
Updated totals: 201 (v1.0) + 16 (v1.1 addendum) = 217 validations
```
