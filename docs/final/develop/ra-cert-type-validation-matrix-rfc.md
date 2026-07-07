```text
Internal Specification                              Salman Technologies
Category: Standards Track (Internal)                        PKI-RA Team
Companion to: RA-SPEC-001, RA-SPEC-002                      RA-SPEC-003
                                                            7 July 2026


         RA Validation Plan by Certificate Type (Simple, Point-Wise)
```

## What this document is

This is a plain, point-wise checklist written from the RA's point of
view: "a CSR arrived — exactly which checks do I run?" First it lists the
checks that run for **every** certificate. Then, for **each certificate
type**, it lists the extra checks specific to that type. Nothing is
skipped; it is just written as simple points instead of a matrix.

Detailed versions with code and reasons: RA-SPEC-001 (full requirements),
RA-SPEC-002 (why each check exists).

> **Scope note (WLCA).** In this project, applicant and certificate-subject
> identity is verified through **Active Directory only** (plus linked HR /
> CMDB records for services and devices). Wherever a check below says
> "verify identity", it means "verify the Active Directory identity" — the
> RA does not use external KYC, government photo-ID, video proofing, or
> eIDAS/QSCD identity schemes.

## How the RA processes a CSR

```
CSR arrives
   │
   1. Run the COMMON checks (Part 1) — same for every certificate.
   2. Look at the certificate type asked for.
   3. Run the TYPE-SPECIFIC checks (Part 2) for that type.
   4. Check the CSR actually matches the type it claims.
   5. Vet identity + approve + send to CA (Part 3).
   │
   Rule: any check fails → STOP. Never skip a check.
```

---

# PART 1 — COMMON CHECKS (every certificate)

These run for TLS Server, TLS Client, S/MIME, Code Signing, and Document
Signing — always.

## A. Request checks (before reading the CSR)

1. **Connection is secure** — endpoint accepts TLS 1.2 or higher only.
   *Why: protect the CSR and credentials in transit.*
2. **Caller is logged in** — authenticated against Active Directory.
   *Why: no anonymous requests; everything must be traceable.*
3. **Caller is allowed this type** — check AD group / role.
   *Why: logged-in is not the same as allowed to order this certificate.*
4. **Request size is capped** — reject oversized payloads before parsing.
   *Why: stop memory-exhaustion attacks cheaply.*
5. **Rate limit per caller** — too many requests are throttled.
   *Why: stop floods and abuse.*
6. **Duplicate request blocked** — unique transaction ID, DB-enforced.
   *Why: retries must not become duplicate certificates.*
7. **Request fields are clean** — strict schema; screen for injection.
   *Why: these values are shown in consoles and stored in the DB.*

## B. CSR format checks

8. **CSR is present** and within the size limit.
9. **Valid PEM** — correct BEGIN/END markers, exactly one block.
10. **Decodes and parses** — clean base64, valid PKCS#10 in strict DER.
    *Why: strict parsing means the RA and CA read the exact same bytes.*
11. **Version is 0** — the only valid PKCS#10 version.
12. **No private key pasted** — if a PRIVATE KEY block is found: reject
    AND blocklist that key forever.
    *Why: a key that travelled the network is compromised for good.*

## C. Key checks (cryptography)

13. **Proof of Possession** — verify the CSR's own signature with its
    public key.
    *Why: proves the sender holds the private key. Most important check.*
14. **Strong signature algorithm** — SHA-256 or better; reject MD5/SHA-1.
15. **Adequate key size** — RSA ≥ 2048 (≥ 3072 for code signing);
    EC only P-256 or P-384.
16. **Sensible RSA exponent** — odd, ≥ 65537.
17. **Not a weak/known-bad key** — screen against Debian, ROCA, Fermat,
    and leaked-key lists.
18. **Key not reused wrongly** — same key under a different name, or
    across certificate types → reject (and alert).

## D. Name (Subject) checks

19. **Field lengths and country** — CN ≤ 64, country is a real ISO code.
20. **No hidden tricks** — reject invisible / right-to-left / control
    characters; flag look-alike (mixed-script) names for review.
21. **No junk values** — reject "-", "N/A", "." and stray spaces.
22. **Organization matches records** — the O= field must equal a company
    name already verified for this requester.

## E. Requested-power (extension) checks

23. **Cannot ask to be a CA** — basicConstraints cA=TRUE → reject.
24. **No CA-only key usage** — keyCertSign / cRLSign → reject.
25. **No "do-everything" usage** — anyExtendedKeyUsage → reject.
26. **Only known extensions** — anything not on the allow-list → reject.
27. **Ignore RA-owned fields** — client-supplied SKID/AKID/policies/SCT
    are discarded; the RA/CA set these.

*(Detailed IDs for the above: R-41.., R-42.., R-43.., R-44.., R-45.. and
the AD checks R-AD-01..15 — see RA-SPEC-001.)*

---

# PART 2 — TYPE-SPECIFIC CHECKS (per certificate)

Each type below runs ALL of Part 1, PLUS the extra checks listed here.

## 1. TLS SERVER certificate

**Use:** a website/server proves it owns a domain (HTTPS).
**Example:** `CN=api.salmantech.com`, SAN `DNS:api.salmantech.com`,
EKU `serverAuth`.

Extra checks:
1. **Domain in SAN** — the domain name must be in the SAN field
   (CN alone is not enough).
2. **Domain name is valid** — proper FQDN; no bare `*.com`; wildcard only
   as `*.something.com`.
3. **Domain Control Validation (DCV)** — the requester must place an
   RA-given random token in the domain's DNS or web server; the RA checks
   it. *This proves they control the domain.*
4. **Wildcard uses the DNS method** — a file proves one host, DNS proves
   the whole domain.
5. **DCV checked from 2+ locations (MPIC)** and with **DNSSEC** — so a
   network hijack near the RA cannot fake it.
6. **DCV is fresh** — proof not older than 200 days (2026 rule; shrinks to
   10 days by 2029).
7. **CAA record allows our CA** — check the domain's CAA DNS record, ≤ 8h
   before issuing.
8. **No private/internal addresses** — no 10.x, localhost, or `.local`.
9. **EKU = serverAuth.**
10. **Validity ≤ 200 days.**
11. **Look-alike domain screen** — bank/brand look-alikes go to human
    review (a phisher can control `salrnantech.com` and pass DCV honestly).
12. **Approval:** may be automatic (DV) once DCV passes.

## 2. TLS CLIENT certificate

**Use:** a user, service, or device proves its identity (mutual TLS).
**Example:** `CN=service-a`, `O=Salman Technologies Pvt Ltd`,
EKU `clientAuth`, no server hostname.

Extra checks:
1. **Identity exists in our records** — the name must be a real person in
   AD/HR, or a real service/device in the CMDB inventory.
2. **Requester owns the identity** — you can request for yourself, or for
   a service your team owns; NOT for someone else. *This is the #1 check —
   the impersonation guard.*
3. **EKU = clientAuth only** — serverAuth must be ABSENT.
   *Why: a client cert that can also be a server turns one hacked laptop
   into a man-in-the-middle tool.*
4. **CN is not a hostname** — an FQDN in a client cert is suspicious
   (type-confusion); reject/flag.
5. **UPN / email in SAN matches the directory** exactly (for smartcard
   login).
6. **Device/service is active** — not retired or lost in the CMDB.
7. **Validity ≤ 1 year** — client identities change faster than servers.
8. **Leaver hook** — if the user/service is disabled in AD, revoke the
   certificate.
9. **Approval:** single officer, or automatic once identity binding passes.

## 3. S/MIME (email) certificate

**Use:** a person signs and encrypts email.
**Example:** `CN=Salman Khan`, SAN `email:salman@salmantech.com`,
EKU `emailProtection`.

Extra checks:
1. **Email in SAN** — a valid `rfc822Name` must be present.
2. **Mailbox Control Validation** — the RA emails a random code to that
   exact address; the requester enters it back. *Proves they can read that
   inbox.*
3. **Company owns the mail domain** — the domain after @ must be on the
   organization's verified list.
4. **CAA `issuemail` allows our CA** — check the mail domain's CAA record
   (RFC 9495).
5. **Person is verified** — for sponsored certificates, the name matches
   HR records.
6. **Evidence is fresh** — mailbox proof ≤ 398 days, identity proof
   ≤ 825 days.
7. **EKU = emailProtection**; KeyUsage includes keyEncipherment (RSA) for
   encryption.
8. **Profile is Strict or Multipurpose** (the old Legacy profile is
   retired) and **validity ≤ 824 days.**
9. **Approval:** automatic once the mailbox challenge passes.

## 4. CODE SIGNING certificate

**Use:** a company signs software (.exe/.jar) so the OS trusts it.
This is the most dangerous type — a mistake becomes signed malware.
**Example:** `CN=Salman Technologies Pvt Ltd`, `O=...`, `C=IN`,
EKU `codeSigning`, no SAN.

Extra checks:
1. **Bigger key** — RSA ≥ 3072.
2. **CN = the verified legal company name**; O and C are mandatory.
   *This name is what users see as "Publisher".*
3. **No SAN** — its presence is suspicious.
4. **EKU = codeSigning only**; KeyUsage = digitalSignature only.
5. **Private key is in hardware** — proven by key attestation
   (FIPS 140-2 L2 / EAL4+ token). Software keys are rejected.
6. **Company is real** — looked up in the government registry ourselves
   (not from uploaded documents).
7. **Independent callback** — phone the company on a registry-sourced
   number (never the number on the application) to confirm the request and
   the requester's authority.
8. **Reputation screen** — check against malware databases, prior abuse,
   sanctions lists, and typosquat names ("Microsofft") → human review.
9. **Validity ≤ 1 year.**
10. **Approval:** NEVER automatic — two officers must both approve.

## 5. DOCUMENT SIGNING certificate

**Use:** a person legally signs PDFs/contracts (advanced / qualified
electronic signatures, eIDAS / ETSI).
**Example:** `CN=Salman Khan`, `O=...`, `C=IN`,
KeyUsage includes `nonRepudiation`.

Extra checks:
1. **Verify identity via Active Directory** — confirm the applicant is an
   active AD user and that the requester is authorized to obtain a
   document-signing certificate. In WLCA, identity **is** the applicant's
   verified AD identity.
   *WLCA scope: identity verification is Active Directory based only — no
   external KYC, government photo-ID, or video proofing.*
2. **CN matches the AD identity** — the name in the certificate matches the
   applicant's AD record.
3. **KeyUsage includes nonRepudiation** — this is what makes a signature
   legally undeniable in court.
4. **No TLS or code-signing EKUs** — the certificate's scope is signing
   documents only.
5. **Key protection** — the signing key is generated and held as required
   by the WLCA certificate policy (e.g. HSM / token).
6. **Organization link (if named)** — O= matches the applicant's
   organization in AD.
7. **Validity** — as set by the WLCA certificate policy (CP/CPS).
8. **Approval:** manual officer review; audit-logged.

---

# PART 3 — AFTER VALIDATION (every certificate)

1. **Separation of duties** — the submitter can never be the approver
   (enforced in the database).
2. **Editing voids approval** — any change to an approved request sends it
   back for re-approval.
3. **Re-clamp validity** — apply the type's validity cap again just before
   sending to the CA (rules can change between request and issue).
4. **Pre-issuance lint** — run zlint on the to-be-signed data.
5. **Verify what the CA returns** — the issued certificate must match the
   approved request exactly (name, key, extensions, dates).
6. **Log everything** — every decision (pass and fail) into an append-only,
   tamper-evident audit trail, kept for the required retention period.
7. **24/7 problem reporting** — accept key-compromise reports any time and
   verify the proof; the 24-hour revocation clock starts at the report.

---

# Quick comparison (one table)

| Question | TLS Server | TLS Client | S/MIME | Code Signing | Doc Signing |
|----------|-----------|-----------|--------|--------------|-------------|
| Main control check | Domain (DCV) | Identity binding | Mailbox challenge | Key attestation + callback | AD identity |
| Required EKU | serverAuth | clientAuth | emailProtection | codeSigning | (none; nonRepudiation KU) |
| serverAuth allowed? | Yes | **No** | No | No | No |
| SAN | domain (must) | optional | email (must) | none | optional |
| Key floor | RSA 2048 | RSA 2048 | RSA 2048 | **RSA 3072** | RSA 2048 |
| Identity depth | DV / OV | AD/CMDB record | mailbox + AD | full legal + callback | AD identity |
| Auto-approve? | Yes (DV) | Yes (after binding) | Yes | **No — 2 officers** | **No — manual** |
| Validity cap (2026) | 200 days | 1 year | 824 days | 1 year | 3 years |

*End of RA-SPEC-003*
