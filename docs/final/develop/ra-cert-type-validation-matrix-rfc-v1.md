```text
Internal Specification                              Example Corp
Category: Standards Track (Internal)                     RA-SPEC-003-v1
Companion to: RA-SPEC-001, RA-SPEC-002                     10 July 2026


      RA Validation Plan by Certificate Type (Tabular: What / How / Why)
```

## What this document is

A tabular reformat of RA-SPEC-003. Every validation is listed in a table
with three columns: **Validation** (what is checked), **How to Validate**
(how the RA does it), and **Reason** (why it matters). Part 1 lists the
checks that run for every certificate; Part 2 lists the extra checks per
certificate type; Part 3 lists the post-validation / issuance checks.

> **Scope note (WLCA).** Applicant and certificate-subject identity is
> verified through **Active Directory only** — users, devices, and services
> are all AD objects; there is no separate CMDB, and no external KYC,
> government photo-ID, or video proofing.

## How the RA processes a CSR

```
CSR arrives
   1. Run the COMMON checks (Part 1) — same for every certificate.
   2. Look at the certificate type requested.
   3. Run the TYPE-SPECIFIC checks (Part 2) for that type.
   4. Confirm the CSR actually matches the type it claims.
   5. Vet identity + approve + send to CA (Part 3).
   Rule: any check fails → STOP. Never skip a check.
```

---

# PART 1 — COMMON CHECKS (every certificate)

## A. Request checks (before reading the CSR)

| # | Validation | How to Validate | Reason |
|---|-----------|-----------------|--------|
| A1 | Secure connection | Endpoint accepts TLS 1.2+ only (server / load-balancer config); verify with a periodic scan | Protect the CSR and credentials in transit |
| A2 | Caller authenticated | Authenticate the caller against Active Directory | No anonymous requests; everything must be traceable |
| A3 | Caller authorized for this type | Check the caller's AD group / role (RBAC) for the requested certificate type | Being logged in ≠ being allowed to order this certificate |
| A4 | Payload size capped | Reject oversized payloads before parsing | Stop memory-exhaustion DoS cheaply |
| A5 | Rate limit | Throttle requests per caller | Stop floods and abuse |
| A6 | Duplicate blocked | Unique client transaction ID, enforced by a DB constraint | Retries must not become duplicate certificates |
| A7 | Clean input | Strict schema validation; screen text fields for injection | These values are shown in consoles and stored in the DB |

## B. CSR format checks

| # | Validation | How to Validate | Reason |
|---|-----------|-----------------|--------|
| B1 | CSR present | Non-empty, within the size limit | Fail fast on garbage |
| B2 | Valid PEM | Correct BEGIN/END markers; exactly one block | Know exactly what is being read |
| B3 | Decodes and parses | Clean base64; valid PKCS#10 in strict DER; **no trailing bytes** after the structure | RA and CA read the exact same bytes — no smuggled data or parser differentials |
| B4 | Version is 0 | Check the PKCS#10 version field == 0 | The only valid PKCS#10 version |
| B5 | No private key pasted | If a PRIVATE KEY block is present → reject AND blocklist that key forever | A key that travelled the network is compromised for good |
| B6 | Strict algorithm encoding | In the `AlgorithmIdentifier`, the `parameters` field must match the algorithm — RSA (PKCS#1 v1.5) = NULL, ECDSA = absent; reject malformed/extra. Check both `signatureAlgorithm` and `SubjectPublicKeyInfo` | Loose encoding enables parser-differential and signature-malleability; zlint flags it (CSBR 7.1.3.2 / RFC 4055 / 5758) |
| B7 | EC named curve only | EC public key must reference a named curve by OID (P-256/P-384), not explicit curve parameters (`SubjectPublicKeyInfo`) | Explicit parameters can define a custom, unauditable weak curve (RFC 5480; zlint) |

## C. Key checks (cryptography)

*A certificate is a public promise that the key is safe to trust for its
whole validity period. The RA validates ownership, strength, health, and
that the key is not reused wrongly.*

| # | Validation | How to Validate | Reason |
|---|-----------|-----------------|--------|
| C1 | Proof of Possession | Verify the CSR's own signature against its public key | Proves the sender holds the private key. The most important check |
| C2 | Strong signature algorithm | SHA-256 or better; reject MD5/SHA-1 | Broken hashes allow forged requests |
| C3 | Adequate key size | RSA ≥ 2048 (≥ 3072 for code signing); EC P-256 or P-384 only | Weak keys are factorable |
| C4 | Sensible RSA exponent | `e` must be odd and ≥ 65537 | Even `e` = broken RSA (no valid private key); small `e` is attack-prone; 65537 is safe and fast (CABF: odd ≥ 3 MUST, ≥ 65537 SHOULD) |
| C5 | Not a weak/known-bad key | Screen the key (by SPKI fingerprint) against Debian, ROCA, Fermat, and leaked-key lists | Some keys are broken before they arrive |
| C6 | No wrong key reuse | Same SPKI under a different subject, or across certificate types → reject (and alert) | No honest reason to share keys; signals theft/copy-paste; preserves non-repudiation |

## D. Name (Subject) checks

| # | Validation | How to Validate | Reason |
|---|-----------|-----------------|--------|
| D1 | Field lengths & country | CN ≤ 64; country is a real ISO 3166-1 code | Oversized fields break relying-party software; fake country corrupts vetting |
| D2 | No hidden tricks | Reject invisible / right-to-left / control characters; flag mixed-script look-alikes for review | Invisible-character name spoofing |
| D3 | No junk values | Reject "-", "N/A", "." and stray/double spaces | Placeholder junk is prohibited |
| D4 | Organization matches records | `O=` must equal a company name already verified for this requester (in AD) | Else any authenticated user could name any company |
| D5 | DN string type | Each DN value must be `UTF8String` or `PrintableString`; reject `TeletexString`/`BMPString` and other exotic types | Exotic types render differently across software (spoofing) and cause interop bugs (RFC 5280) |

## E. Requested-power (extension) checks

| # | Validation | How to Validate | Reason |
|---|-----------|-----------------|--------|
| E1 | Cannot ask to be a CA | `basicConstraints cA=TRUE` → reject | An end-entity becoming a CA is the biggest escalation |
| E2 | No CA-only key usage | `keyCertSign` / `cRLSign` → reject | CA-only powers |
| E3 | No "do-everything" usage | `anyExtendedKeyUsage` → reject | Dissolves all scope boundaries |
| E4 | Only known extensions | Anything not on the allow-list → reject | Unknown extensions can't be risk-rated |
| E5 | Ignore RA-owned fields | Discard client-supplied SKID/AKID/policies/SCT | The RA/CA compute these |
| E6 | KeyUsage matches key type | An EC key must not carry `keyEncipherment` (EC uses `keyAgreement`) → reject the mismatch | Cryptographically meaningless bits confuse relying parties |
| E7 | SAN capped & unique | Reject duplicate SAN entries; cap the count (e.g. ≤ 100) | Prevents abuse/DoS amplification and log bloat |

---

# PART 2 — TYPE-SPECIFIC CHECKS (per certificate)

*Each type runs ALL of Part 1, PLUS the checks below.*

## 1. TLS SERVER

**Use:** a server proves it controls a domain (HTTPS).
**Example:** `CN=api.example.com`, SAN `DNS:api.example.com`, EKU `serverAuth`.

| # | Validation | How to Validate | Reason |
|---|-----------|-----------------|--------|
| S1 | Domain in SAN | The domain must be in the SAN (CN alone is not enough) | Browsers match on SAN, not CN |
| S2 | Domain name valid | Proper FQDN; no bare public suffix (`*.com`); wildcard only as `*.something.com` | A cert for `*.com` would be an internet-wide skeleton key |
| S3 | Domain Control Validation (DCV) | RA gives a random token; requester places it in DNS TXT or an HTTP `/.well-known/` file; RA looks it up **from its own resolvers** and matches. WLCA internal: confirm the domain is on the org's AD approved-domain list | Proves the requester actually controls the domain |
| S4 | Wildcard uses DNS method | A wildcard is validated by the DNS method only, never the HTTP file method | HTTP proves one host; DNS proves the whole namespace, matching the wildcard's scope (BR 3.2.2.4) |
| S5 | DCV freshness | Re-check DCV at issuance time within the reuse window. WLCA: timestamp the AD domain-ownership evidence and periodically re-confirm | Domains change owners; stale proof issues to the wrong owner |
| S6 | CAA allows our CA | Read the domain's CAA record ≤ 8h before issuance; our CA must be listed. **WLCA: N/A** (single internal CA) | The domain owner's published authorization (defense in depth) |
| S7 | No private/internal addresses | Reject `10.x`, `localhost`, `.local` (public certs) | Nobody can own these names |
| S8 | EKU = serverAuth | `serverAuth` present; no cross-type EKU (`codeSigning`/`emailProtection`); no `anyExtendedKeyUsage` | The browser checks it at handshake; scope separation |
| S9 | Look-alike domain screen | Fuzzy-match the domain against a brand/high-value list → flagged requests go to maker-checker human review. **WLCA: mostly N/A** (approved-domain list blocks external look-alikes) | DCV proves *control*, not *honesty* — a phisher can own `exarnple.com` and pass DCV |
| S10 | Approval | May be automatic (DV) once DCV passes; suppressed to human review on any flag (OV/EV, look-alike) | DCV is deterministic/machine-checkable — no human judgment needed for plain DV |

*(Validity is set/enforced by the CA via the mapped profile; the RA only selects the profile.)*

## 2. TLS CLIENT

**Use:** a user, service, or device proves its identity (mutual TLS).
**Example:** `CN=service-a`, `O=Example Corp`, EKU `clientAuth`, no server hostname.

| # | Validation | How to Validate | Reason |
|---|-----------|-----------------|--------|
| C-1 | Identity exists in AD | CN/SAN maps to a real AD object — user object, computer object, or service account/gMSA — anchored on `objectGUID` | The cert is only as true as the AD lookup behind it |
| C-2 | Requester owns the identity | Self, or a service/device owned via AD `managedBy` / owning group; NOT someone else's | Impersonation guard — the #1 check; a cert *is* the identity in mTLS |
| C-3 | EKU = clientAuth only | `clientAuth` present; `serverAuth` and `anyEKU` absent | A client cert that can also be a server becomes a MITM tool if stolen |
| C-4 | CN is not a hostname | CN is an identity name (`jdoe`, `service-a`), not an FQDN → reject/flag | Type confusion; the client path skips DCV, so a server-style name could be misused |
| C-5 | UPN/email in SAN matches AD | Exact match to AD `userPrincipalName` / `mail` | Smartcard/AD logon maps the cert to the account via UPN |
| C-6 | AD object active | `userAccountControl` ACCOUNTDISABLE bit clear, `accountExpires` valid; LDAP filter `(!(userAccountControl:1.2.840.113556.1.4.803:=2))` | Retired/disabled objects must not get fresh credentials |
| C-7 | Leaver hook | Revoke the certificate when the user/service is disabled in AD | An ex-employee's valid cert is ghost access |
| C-8 | Approval | Single officer, or automatic once identity binding passes | — |

**SAN entry types (TLS client):**

| SAN type | ASN.1 tag | How to Validate |
|----------|:---------:|-----------------|
| otherName (UPN) | `[0]` | UPN format valid; **exact match to AD `userPrincipalName`** |
| rfc822Name (email) | `[1]` | Valid email; domain org-owned; matches AD `mail` |
| dNSName (device) | `[2]` | Matches AD computer object `dNSHostName`; not a public/server name |
| URI (service) | `[6]` | Valid SPIFFE URI; trust domain is ours; maps to a registered service |

## 3. S/MIME (email)

**Use:** a person signs and encrypts email.
**Example:** `CN=John Doe`, SAN `email:jdoe@example.com`, EKU `emailProtection`.

| # | Validation | How to Validate | Reason |
|---|-----------|-----------------|--------|
| M1 | Email in SAN | A valid `rfc822Name` (IA5String) must be present; match it to the AD `mail` attribute, or an `smtp:` entry in `proxyAddresses` (not `userPrincipalName`) | Mail clients match on the SAN; AD authoritatively owns the mailbox |
| M2 | Mailbox Control Validation (MCV) | **Optional in WLCA** — AD `mail` match already proves ownership; MCV (email a random code, requester echoes it back) only for non-AD mailboxes | Proves the requester can read the mailbox |
| M3 | Company owns the mail domain | Extract the domain after `@`; match against the org's verified list (AD accepted domains / UPN suffixes) | The company can only vouch for its own domains |
| M4 | CAA `issuemail` | **Public-trust only; N/A in WLCA.** (Read the mail domain's CAA `issuemail` tag) | Lets the domain owner authorize the CA (RFC 9495) |
| M5 | Person verified (AD) | Cert CN must equal the AD `displayName` (anchored on `objectGUID`) | A sponsored cert names a real, verified person |
| M6 | Evidence freshness | **Public-trust windows; N/A in WLCA** (AD is read live at each issuance) | Stale cached evidence lies |
| M7 | EKU + KeyUsage | `emailProtection` present; `serverAuth`/`clientAuth` absent; KU `digitalSignature` (sign) + `keyEncipherment` (RSA) / `keyAgreement` (EC) for encryption | S/MIME signs and receives encrypted mail; scope separation |
| M8 | Profile & validity | Enforced by the CA via the mapped profile; the RA selects it | — |
| M9 | Approval | Maker-checker required (Maker submits, separate Checker approves) | Not automatic even when the email matches AD |

## 4. CODE SIGNING

**Use:** a company signs software so the OS trusts it. The highest-risk type —
a mistake becomes signed malware.
**Example:** `CN=Example Corp`, `O=...`, `C=IN`, EKU `codeSigning`, no SAN.

| # | Validation | How to Validate | Reason |
|---|-----------|-----------------|--------|
| K1 | Bigger key | RSA ≥ 3072 / EC P-256/P-384; read the key from the CSR and check size | Code signatures are verified for 5–15+ years; the key must resist future attacks |
| K2 | CN = verified legal name; O, C mandatory | Match CSR `CN`/`O` against the imported AD organization name; `C` a valid ISO code; reject metadata-only values | This name is the "Publisher" users trust when installing |
| K3 | No SAN | If `subjectAltName` is present → flag/reject | The publisher identity is in the DN; a SAN means scope confusion |
| K4 | EKU codeSigning only; KU digitalSignature only | EKU must be `codeSigning` and nothing else; KU `digitalSignature` only (no `keyEncipherment`, no CA bits) | The OS loader checks the EKU; single-purpose = least privilege for the riskiest type |
| K5 | Private key in hardware | Confirm via key attestation (verify signed statement + attested key == CSR key) or a CA-provisioned token; reject software keys | The crown-jewel key; software keys are easily stolen |
| K6 | Company is real | At onboarding, look the customer up directly in the authoritative registry and store name/registration-ID/country in AD; at request time match CSR `O`/`serialNumber`/`C` against AD | Must be a real legal entity; uploaded documents can be forged |
| K7 | Approval | NEVER automatic — two officers must both approve | The blast radius of a mistake is signed malware |

*(Validity is set/enforced by the CA via the mapped profile; the RA only selects the profile.)*

## 5. DOCUMENT SIGNING

**Use:** a person legally signs PDFs/contracts (digital signatures).
**Example:** `CN=John Doe`, `O=...`, `C=IN`, EKU `documentSigning`, KU includes `nonRepudiation`.

| # | Validation | How to Validate | Reason |
|---|-----------|-----------------|--------|
| D-1 | Verify identity via AD | Confirm the applicant is an active AD user and is authorized (AD group) | AD-based identity only — no external KYC/photo-ID/video |
| D-2 | CN matches AD identity | Cert CN must equal the applicant's AD `displayName` | The name is the legal signer on the document |
| D-3 | KeyUsage nonRepudiation | The `nonRepudiation` (contentCommitment) bit must be set; reject if absent | Makes the signature legally undeniable in court |
| D-4 | EKU = documentSigning | `id-kp-documentSigning` (RFC 9336) present per the WLCA profile; `serverAuth`/`clientAuth`/`codeSigning` absent | Scope separation — document signing only |
| D-5 | Key protection | Generated/held per the WLCA certificate policy (HSM / token) | Non-repudiation depends on the key not being copyable |
| D-6 | Organization link (if named) | `O=` matches the applicant's organization in AD | The person signs on behalf of the org |
| D-7 | Approval | Manual officer review; audit-logged | Legally-binding signatures |

*(Validity is set by the WLCA certificate policy / CA profile.)*

---

# PART 3 — AFTER VALIDATION (every certificate)

| # | Validation | How to Validate | Reason |
|---|-----------|-----------------|--------|
| P1 | Separation of duties | Submitter ≠ approver, enforced in the database | No single person can push a request from start to certificate |
| P2 | Editing voids approval | Any change to an approved request resets it for re-approval | Blocks the approve-then-modify attack |
| P3 | Re-clamp validity | Apply the type's validity cap again just before CA send | Rules can change between request and issuance |
| P4 | Pre-issuance lint | Run zlint on the to-be-signed data | Catch BR violations before the CA signs them |
| P5 | Verify what the CA returns | The issued cert must match the approved request exactly (name, key, extensions, dates) | Confirm the CA issued exactly what was approved |
| P6 | Log everything | Append-only, tamper-evident audit of every verdict; retention per policy | Auditors reconstruct decisions from logs |
| P7 | 24/7 problem reporting | Accept key-compromise reports anytime and verify the proof | The 24-hour revocation clock starts at the report |

---

# Quick comparison (one table)

| Question | TLS Server | TLS Client | S/MIME | Code Signing | Doc Signing |
|----------|-----------|-----------|--------|--------------|-------------|
| Main control check | Domain (DCV) | Identity binding | Mailbox challenge | Key attestation | AD identity |
| Required EKU | serverAuth | clientAuth | emailProtection | codeSigning | documentSigning (+ nonRepudiation KU) |
| serverAuth allowed? | Yes | **No** | No | No | No |
| SAN | domain (must) | optional | email (must) | none | optional |
| Key floor | RSA 2048 | RSA 2048 | RSA 2048 | **RSA 3072** | RSA 2048 |
| Identity depth | DV / OV | AD record | mailbox + AD | full legal (AD) | AD identity |
| Auto-approve? | Yes (DV) | Yes (after binding) | No — maker-checker | **No — 2 officers** | **No — manual** |

*End of RA-SPEC-003-v1*
