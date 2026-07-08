```text
Internal Specification                              Example Corp
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
> identity is verified through **Active Directory only** — users, devices,
> and services are all AD objects, so there is **no separate CMDB**.
> Wherever a check below says "verify identity", it means "verify the Active
> Directory identity" — the RA does not use external KYC, government
> photo-ID, video proofing, or eIDAS/QSCD identity schemes.

## Abbreviations

| Abbr. | Full form |
|-------|-----------|
| ACME | Automatic Certificate Management Environment (RFC 8555) |
| AD | Active Directory |
| AKID / SKID | Authority / Subject Key Identifier |
| BER / DER | Basic / Distinguished Encoding Rules (ASN.1) |
| BR / CSBR / SMBR | (CA/Browser Forum) Baseline Requirements / Code Signing BR / S/MIME BR |
| CA | Certification Authority |
| CAA | Certification Authority Authorization (DNS record) |
| CABF | CA/Browser Forum |
| CN | Common Name (a Subject DN attribute) |
| CP / CPS | Certificate Policy / Certification Practice Statement |
| CSR | Certificate Signing Request (PKCS#10) |
| CT | Certificate Transparency (RFC 6962) |
| DCV | Domain Control Validation |
| DN | Distinguished Name |
| DNS | Domain Name System |
| DNSSEC | DNS Security Extensions |
| DV / OV / EV | Domain / Organization / Extended Validated |
| EC / ECDSA | Elliptic Curve / EC Digital Signature Algorithm |
| eIDAS | EU electronic IDentification, Authentication and trust Services |
| EKU | Extended Key Usage |
| ETSI | European Telecommunications Standards Institute |
| FIPS | Federal Information Processing Standards |
| FQDN | Fully Qualified Domain Name |
| HR | Human Resources (system) |
| HSM | Hardware Security Module |
| HTTP / HTTPS | HyperText Transfer Protocol (Secure) |
| IP | Internet Protocol (address) |
| ISO | International Organization for Standardization |
| KU | Key Usage |
| KYC | Know Your Customer |
| LDAP / LDAPS | Lightweight Directory Access Protocol (Secure) |
| MCV | Mailbox Control Validation |
| MFA | Multi-Factor Authentication |
| ML-DSA / SLH-DSA | Post-quantum signature algorithms (FIPS 204 / 205) |
| MPIC | Multi-Perspective Issuance Corroboration |
| mTLS | mutual TLS |
| OID | Object Identifier |
| PEM | Privacy-Enhanced Mail (Base64 certificate encoding) |
| PKCS#10 | Public-Key Cryptography Standards #10 (CSR format) |
| PoP | Proof of Possession |
| PQC | Post-Quantum Cryptography |
| QSCD | Qualified Signature Creation Device |
| RA | Registration Authority |
| RBAC | Role-Based Access Control |
| RFC | Request for Comments (IETF standard) |
| ROCA | Return of Coppersmith's Attack (RSA key vulnerability) |
| RSA | Rivest–Shamir–Adleman (cryptosystem) |
| SAN | Subject Alternative Name |
| SC-081 / SC-067 / SC-063 | CA/Browser Forum ballot numbers |
| SCT | Signed Certificate Timestamp |
| S/MIME | Secure/Multipurpose Internet Mail Extensions |
| SoD | Separation of Duties |
| SPKI | Subject Public Key Info |
| TLS | Transport Layer Security |
| TXT | DNS Text record |
| UPN | User Principal Name (Active Directory) |
| WLCA | Worldline Certificate Authority (this project's CA) |
| zlint / cablint | Certificate linting tools |

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
**Example:** `CN=api.example.com`, SAN `DNS:api.example.com`,
EKU `serverAuth`.

Extra checks:
1. **Domain in SAN** — the domain name must be in the SAN field
   (CN alone is not enough).
2. **Domain name is valid** — proper FQDN; no bare `*.com`; wildcard only
   as `*.something.com`.
3. **Domain Control Validation (DCV)** — the requester must place an
   RA-given random token in the domain's DNS or web server; the RA checks
   it. *This proves they control the domain: only someone who controls the
   domain can place the token where the RA looks for it.*

   **How the RA proves control — DNS TXT method (example):**
   ```
   Domain requested:  api.example.com

   Step 1  RA generates a random, unguessable token:
           8f3a1c9e5b7d2049a6c1e0f4

   Step 2  RA tells the requester to publish it as a DNS TXT record:
           _wlca-challenge.api.example.com.  TXT  "8f3a1c9e5b7d2049a6c1e0f4"

   Step 3  The requester (who controls the DNS zone) adds that record.

   Step 4  The RA looks it up from its OWN resolvers and compares:
           dig TXT _wlca-challenge.api.example.com
             → "8f3a1c9e5b7d2049a6c1e0f4"   ✓ matches  → control proven
             → no record / wrong value       ✗ mismatch → DCV fails, reject
   ```

   **Alternative — HTTP file method (example):**
   ```
   Step 1  RA generates a token:  8f3a1c9e5b7d2049a6c1e0f4
   Step 2  Requester places a file on that exact host:
           http://api.example.com/.well-known/pki-validation/wlca.txt
           (file content = 8f3a1c9e5b7d2049a6c1e0f4)
   Step 3  RA fetches that URL and compares the content.   ✓ / ✗
   ```

   **Why it proves control:** the token is random and unguessable, so a
   stranger cannot produce it; and only the party who controls the domain's
   DNS zone (or its web root) can place the token where the RA checks.
   Match = the requester really controls the domain.

   **WLCA scope note:** for internal enterprise domains, WLCA may instead
   confirm the domain is on the organization's **approved domain list**
   (held in AD / RA configuration) and that the requesting AD account's
   organization owns it — used in place of, or in addition to, the public
   DNS/HTTP challenge above.

   **Implementation flow (how the RA runs DCV, end to end):**
   ```
   Client                                RA
     │ ① POST /requests (CSR) ─────────▶ │ validate CSR (common checks)
     │                                   │ generate SecureRandom token (128-bit)
     │                                   │ save challenge (request, token,
     │                                   │   expiry e.g. 7 days), state=PENDING_DCV
     │ ◀── 202 { recordName, token } ─── │
     │                                   │
     │ ② adds TXT record at registrar    │        (outside the RA)
     │                                   │
     │ ③ POST /requests/{id}/dcv/verify ▶│ RA does its OWN DNS lookup
     │      (empty body — no "proof")    │   (own resolver, no cache)
     │                                   │ compare found value == stored token
     │ ◀── VERIFIED / FAILED ─────────── │ match → state=VALIDATED,
     │                                   │   evidence saved, token single-use
     │                                   │ no match → retry allowed until expiry
   ```

   Key rules: the client only *triggers* verification and never supplies
   proof; the RA always looks up DNS itself; the token is single-use and
   expires; every lookup result is stored as audit evidence.

   State flow: `SUBMITTED → PENDING_DCV → VALIDATED → APPROVED → SENT_TO_CA
   → ISSUED` (DCV failure keeps the request in PENDING_DCV until it expires).
4. **Wildcard certificates must use the DNS method.**
   A wildcard such as `*.pluto.com` is valid for *every* subdomain at that
   level (`www.`, `api.`, `mail.`, `shop.` … unlimited). It is a claim over
   the **whole namespace**, so the proof of control must cover the whole
   namespace too:
   - **HTTP file method proves only ONE host.** Placing a file at
     `http://api.pluto.com/.well-known/pki-validation/wlca.txt` proves the
     requester controls the `api.pluto.com` server — but different
     subdomains can live on different servers run by different teams
     (`blog.` on WordPress, `shop.` on Shopify). Controlling one host does
     not prove control of the whole domain, so it MUST NOT authorize a
     wildcard.
   - **DNS TXT method proves the WHOLE domain.** The record
     `_wlca-challenge.pluto.com` is added in the domain's **DNS zone**,
     which is managed from a single place (the authoritative nameservers /
     registrar account) — the same place where *all* subdomains are
     defined. Whoever can write to the DNS zone controls the entire
     namespace, which matches exactly what a wildcard grants.

   Rule (CA/Browser Forum BR 3.2.2.4): the scope of the proof must equal
   the scope of the certificate — so a wildcard is validated by DNS only,
   never by the HTTP file method.
5. **DCV is fresh (validation reuse window)** — a passed DCV can be reused
   for a while so renewals don't re-validate every time, but the proof
   expires and must then be re-run. Public-trust window: ≤ 200 days (2026),
   shrinking to 100 (2027) and 10 (2029).

   Important points:
   - **Two different clocks** — the *reuse window* (how old the DCV proof
     may be) is not the same as the *certificate validity* (how long the
     issued cert lasts). One valid DCV can back several certificates.
   - **Why it expires** — domains change owners; reusing a stale proof
     could issue a certificate to a previous owner after the domain was
     sold or transferred.
   - **Checked at issuance time, not submission time** — if a request sits
     in the approval queue, re-check freshness just before sending to the
     CA; an expired proof means re-run DCV.
   - **It is one case of a bigger rule** — every piece of vetting evidence
     has a shelf life (e.g. organization identity, mailbox control); store
     each with a timestamp and re-evaluate validity at issuance.
   - **Shrinking window → automation** — as the window shrinks, one-time
     manual validation stops scaling; DCV must be automated and repeatable.

   *WLCA scope note:* the exact public-trust numbers (200/100/10) are not in
   scope. The principle still applies for internal AD-based validation:
   store the AD domain-ownership evidence with a timestamp and periodically
   re-confirm the requesting org still owns the domain — do not trust a
   one-time check forever.
6. **CAA record allows our CA** — before issuing, read the domain's CAA
   DNS record and confirm our CA is permitted; do this ≤ 8 hours before
   issuance.

   Important points:
   - **CAA is the inverse of DCV** — DCV asks "does the *requester* control
     the domain?"; CAA asks "has the domain *owner* authorized *our CA* to
     issue?" The owner declares this in advance in DNS.
   - **Record format** — each CAA record has a flag, a tag, and a value.
     Tags: `issue` (who may issue normal certs), `issuewild` (wildcards),
     `iodef` (where to report an unauthorized attempt). Example for
     `pluto.com`:

     | Domain | Flag | Tag | Value | Meaning |
     |--------|:----:|-----|-------|---------|
     | pluto.com | 0 | issue | `"digicert.com"` | DigiCert may issue normal certs |
     | pluto.com | 0 | issue | `"ourca.example.com"` | Our CA may also issue |
     | pluto.com | 0 | iodef | `"mailto:security@pluto.com"` | Send unauthorized-attempt reports here |

     Here two CAs are authorized (DigiCert and our CA); any other CA must
     refuse. The `iodef` line tells a refusing CA where to report the
     attempt.
   - **Decision logic:**
     - no CAA record → any CA may issue (allowed);
     - CAA present and our CA listed → allowed;
     - CAA present and our CA **not** listed → **reject**, even if DCV
       passed.
   - **It is a defense-in-depth kill switch owned by the domain owner** —
     it can stop mis-issuance the RA would otherwise allow (e.g. if DCV
     were somehow fooled). The owner, not the RA, controls it.
   - **Why ≤ 8 hours** — owners can change CAA at any time; a fresh check
     honors a recently-published "block this CA" decision instead of acting
     on a stale record.

   *WLCA / AD scope note:* CAA is a **public-trust, public-DNS mechanism**
   (it exists because any public CA could otherwise issue for any domain).
   In WLCA's **internal, single-CA, AD-based scope it is N/A** — there is
   one internal CA, the answer to "which CA may issue" is always WLCA, and
   validation is via AD, not public DNS. The internal equivalent, only if
   multiple internal CAs ever exist, is a **policy/config mapping** of which
   CA may serve which domain namespace — not a public CAA DNS record.
7. **No private/internal addresses** — no 10.x, localhost, or `.local`.
8. **EKU = serverAuth.** The Extended Key Usage says what the certificate
   may be used for; a TLS server certificate must be usable as a server.
   The RA reads the EKU from the CSR's requested extensions and checks:
   - **serverAuth present?** — `serverAuth` (OID 1.3.6.1.5.5.7.3.1) must be
     there; the browser checks this at the TLS handshake and rejects a
     server cert that lacks it.
   - **No cross-type EKU?** — `codeSigning`, `emailProtection`, etc. must
     NOT be present; a server certificate must not also be able to sign
     code or email (scope separation limits the blast radius if it leaks).
   - **No anyExtendedKeyUsage?** — the "do-everything" EKU is forbidden; it
     would dissolve all scope boundaries.

   (`clientAuth` may be allowed alongside serverAuth only if the profile
   explicitly permits the same cert for mutual TLS both ways.)
9. **Validity ≤ 200 days.**
10. **Look-alike domain screen** — DCV proves *control*, not *honesty*. A
    phisher who registers `exarnple.com` (rn ≈ m) genuinely owns it and
    passes DCV honestly, then uses the valid padlock to make phishing look
    real. The RA fuzzy-matches the requested domain against a protected
    brand / high-value list (typo distance + homoglyph normalisation) and
    flags close matches.

    **How a flagged request is resolved (maker-checker):**
    - A flag **suppresses auto-approval** — the request cannot be issued
      automatically; it is routed to the human review queue.
    - The **Maker** (an RA officer) investigates *intent*: is this a
      legitimate business/partner/second domain, or an impersonation
      attempt? Findings and evidence are recorded on the request.
    - A separate **Checker** independently reviews the Maker's findings and
      makes the final decision (approve or reject) — the submitter/Maker
      cannot approve their own review (separation of duties).
    - It is **not auto-rejected**, because legitimate similar names exist;
      a human decides intent, and every decision is audit-logged.

    *WLCA / AD scope note:* mostly N/A for internal issuance — the approved
    domain list already prevents requesting an external look-alike domain,
    so a phisher's domain never reaches this stage. Relevant only if WLCA
    issues for public/external domains.
11. **Approval:** may be automatic (DV) once DCV passes.

    Important points:
    - **What "DV" means** — Domain Validated: the only thing verified is
      domain control (DCV); the organization's legal identity is NOT
      vetted.
    - **Why it can be automatic** — DCV is a deterministic, machine-checkable
      proof (the token either matches or it does not); there is no judgment
      call for a human to add, so the RA can issue without an officer.
      (This is how ACME / Let's Encrypt issues certificates in seconds with
      no human involved.)
    - **"May be" — not always** — auto-approval is suppressed and the request
      goes to human review when any judgment is needed: an OV/EV profile
      (organization identity must be vetted), a look-alike/high-value flag
      (see point 10), or any other flag raised in Stages A–H.
    - **Auto-approve still logs and verifies** — even without a human, the RA
      still runs pre-issuance lint, verifies the CA's returned certificate
      matches the request, and audit-logs the decision.

    *WLCA / AD scope note:* internal issuance can be automatic when
    validation is fully deterministic (AD group + approved-domain-list, both
    machine-checkable). Many enterprises still require a single approver on
    internal certs for control/audit — this is a WLCA policy choice.

## 2. TLS CLIENT certificate

**Use:** a user, service, or device proves its identity (mutual TLS).
**Example:** `CN=service-a`, `O=Example Corp`,
EKU `clientAuth`, no server hostname.

**Most important validations (the ones that matter most):**
1. **Proof of Possession** — the CSR signature is valid (the requester holds
   the private key).
2. **Identity exists in AD** — the CN/UPN maps to a real AD object
   (anchored on `objectGUID`), and the account is enabled.
3. **Requester owns the identity** — self, or a service/device owned via AD
   `managedBy` / owning group (the impersonation guard — #1 in practice).
4. **EKU = clientAuth only** — `serverAuth` and `anyEKU` absent.
5. **CN is an identity name, not a hostname** — no FQDN in the CN.
6. **Key strength** — RSA ≥ 2048 or EC P-256/P-384.

Together these answer the two questions a client cert must satisfy: *is
this a real, owned identity?* (2–3) and *is the cert scoped so it can only
be a client?* (4–5).

Extra checks:
1. **Identity exists in our records** — the certificate subject must
   correspond to a *real object* in an authoritative registry, not just a
   name typed into the CSR. In WLCA the **single authoritative registry is Active
   Directory** — it holds all three identity kinds (users, devices,
   services). **All identity validation is done against AD; WLCA does not
   use a CMDB.**

   **AD architecture — one registry, three object types:**

   | Subject kind | AD object type | Matched on | Example |
   |--------------|---------------|-----------|---------|
   | Human user | User object | `userPrincipalName` / `sAMAccountName` | `CN=jdoe` → AD user `jdoe@example.com` |
   | Device / machine | **Computer object** (domain-joined machines auto-register) | `dNSHostName` / `sAMAccountName` | `CN=laptop-4021` → AD computer `laptop-4021` |
   | Service / app | **Service account** or **gMSA** (group Managed Service Account) | `sAMAccountName` | `CN=service-a` → AD service account `svc-service-a` |

   Important details:
   - **Anchor on `objectGUID`, not the name.** Store and bind the AD
     object's immutable `objectGUID` (or `objectSid`), because display
     names and `sAMAccountName` can change or be reused — a new employee
     called "John Doe" must not inherit the old one's certificates.
   - **Check the object is enabled/active** — verify `userAccountControl`
     (not ACCOUNTDISABLE / LOCKOUT) and `accountExpires`, so a disabled AD
     object cannot get a certificate.
   - **Define one mapping rule** — decide exactly which AD attribute the
     cert CN/SAN maps to, so the "exists" check is unambiguous.
   - **Ownership via AD** — model "who owns this service/device" with the
     `managedBy` attribute or an AD group (WLCA uses no CMDB owner field).
   - **Non-AD entities are a known gap** — containers, Kubernetes/cloud
     workloads, and non-domain-joined devices have no AD object; either
     reject them or handle them with a separate, explicitly-scoped
     mechanism (not part of the current AD-only scope).
2. **Requester owns the identity** — you can request a certificate for
   yourself, or for a service your team owns; NOT for someone else. *This
   is the #1 check — the impersonation guard.*

   Point 1 asks "does this identity exist?"; this point asks "does the
   requester have the right to it?" Both are needed. Ownership comes from
   AD: for a person, the requester's AD identity must equal the subject;
   for a service/device, the requester must be its owner (AD `managedBy`
   or the owning AD group).

   Examples:
   - `jdoe` requests `CN=jdoe` (self) → allowed.
   - `jdoe` requests `CN=service-a`, and jdoe's team owns service-a → allowed.
   - `jdoe` requests `CN=payment-gateway` (another team's service) → reject.
   - `jdoe` requests `CN=asmith` (another person) → reject.

   Why it matters: a certificate *is* the identity — every mTLS service
   trusts the subject name. Without this check, any logged-in user could
   get a cert for `payment-gateway` and impersonate it, with no exploit
   needed.
3. **EKU = clientAuth only** — serverAuth must be ABSENT.
   *Why: a client cert that can also be a server turns one hacked laptop
   into a man-in-the-middle tool.*

   - **clientAuth** = the cert may act as a *client* (call a server);
     **serverAuth** = it may act as a *server* (accept connections). A TLS
     client cert should only be a client.
   - **The risk:** if a client cert also has serverAuth and is stolen, the
     attacker can stand up a fake *trusted server* with it and intercept
     other users' traffic (MITM). With serverAuth absent, a stolen cert can
     only impersonate that one client — the damage is contained.
   - **When to use clientAuth-only:** any cert that authenticates a user,
     device, or service *to* a server — mTLS, VPN, smartcard login,
     service-to-service calls.
   - **Rule of thumb:** one cert, one role. Only allow both EKUs if a
     service genuinely acts as client and server, and the profile says so.
4. **CN is not a hostname** — the CN should be an identity name (`jdoe`,
   `service-a`), not an FQDN like `api.example.com`. A hostname here is
   suspicious: the client-cert path needs no DCV, so an attacker could
   sneak a server-style name through it and later misuse the cert where the
   CN is read as a hostname (type confusion). Reject or flag. This pairs
   with the serverAuth rule (point 3) — one closes the EKU door, the other
   the naming door, so a client cert can never act as a server.
5. **UPN / email in SAN matches the directory** exactly (for smartcard
   login).
6. **Device/service is active** — the AD computer object / service account
   is enabled, not retired or disabled. Existing in AD is not enough; a
   retired object may still exist but be disabled. Check these AD
   attributes:

   | AD attribute / bit | Value | Meaning |
   |--------------------|-------|---------|
   | `userAccountControl` → ACCOUNTDISABLE | `0x0002` | Set = account disabled → reject |
   | `userAccountControl` → LOCKOUT | `0x0010` | Account locked out |
   | `accountExpires` | past timestamp | Expired → reject (`0` or max value = never expires) |
   | `lastLogonTimestamp` | very old (e.g. > 90 days) | Dormant/stale → flag for review |

   The primary check is the ACCOUNTDISABLE bit: `userAccountControl & 0x2
   == 0` means enabled. The cleanest query is an LDAP filter that returns
   only enabled objects:
   `(!(userAccountControl:1.2.840.113556.1.4.803:=2))`.
   The same attributes apply to both computer objects (devices) and service
   accounts.
7. **Leaver hook** — if the user/service is disabled in AD, revoke the
   certificate.
8. **Approval:** single officer, or automatic once identity binding passes.

*(Validity is not validated here — it is set and enforced by the CA
through the mapped certificate profile; the RA only selects the correct
profile.)*

**Attributes to check (TLS client) — quick reference:**

From the CSR:

| Attribute | Check |
|-----------|-------|
| Subject → CN | Identity name (`jdoe`, `service-a`); not an FQDN |
| SAN → rfc822Name / otherName UPN | Valid email / UPN format (if present) |
| Public key | RSA ≥ 2048 or EC P-256/P-384 |
| Signature (PoP) | CSR self-signature valid (key ownership) |
| KeyUsage | `digitalSignature` present; CA bits absent |
| ExtendedKeyUsage | `clientAuth` present; `serverAuth` / `anyEKU` absent |
| basicConstraints | `cA=TRUE` absent |

From Active Directory:

| AD attribute | Check |
|--------------|-------|
| userPrincipalName / sAMAccountName / dNSHostName | Matches the CN/UPN — the identity exists |
| objectGUID | Store as the immutable identity anchor (not the name) |
| userAccountControl / accountExpires | Account is enabled, not locked or expired |
| managedBy / owning AD group | Requester owns the identity (impersonation guard) |

**SAN entry types (what each may hold and how the RA validates it):**

| SAN type | ASN.1 tag | Example value | RA validation |
|----------|:---------:|---------------|---------------|
| otherName (UPN) | `[0]` | `jdoe@example.com` | UPN format valid; **exact match to AD `userPrincipalName`** |
| rfc822Name (email) | `[1]` | `jdoe@example.com` | Valid email format; domain org-owned; matches AD `mail` |
| dNSName (device) | `[2]` | `laptop-4021.corp.example.com` | Matches AD computer object `dNSHostName`; not a public/server name |
| URI (service) | `[6]` | `spiffe://example.com/service-a` | Valid SPIFFE URI; trust domain is ours; maps to a registered service |

The ASN.1 tag `[n]` identifies the entry type. `otherName` (UPN) is the most
nested — an OID (`1.3.6.1.4.1.311.20.2.3`) plus a UTF8String — while email,
DNS, and URI are plain strings. UPN and email can look identical but are
different entries; the UPN is what smartcard/AD logon maps on, so its exact
match to the directory is the critical check.

## 3. S/MIME (email) certificate

**Use:** a person signs and encrypts email.
**Example:** `CN=John Doe`, SAN `email:jdoe@example.com`,
EKU `emailProtection`.

Extra checks:
1. **Email in SAN** — a valid `rfc822Name` must be present (mail clients
   match on the SAN, not the CN). Validate the email against Active
   Directory: it must equal the user's **`mail`** attribute (primary
   email), or — if aliases are allowed — be one of the `smtp:` entries in
   **`proxyAddresses`**. Do NOT use `userPrincipalName` as the email; that
   is the AD login identity, which only looks like an email.
2. **Mailbox Control Validation (MCV) — optional in WLCA.**
   *What MCV does:* it proves the requester can actually read the mailbox.
   The RA emails a random code to that exact address; the requester reads
   their inbox and enters the code back. If it matches, they control the
   mailbox.

   *In WLCA this is achieved by AD instead:* the AD `mail` attribute
   authoritatively says the address belongs to the user (point 1), so
   matching AD already proves the mailbox is theirs. **MCV is therefore
   optional** and only needed for a mailbox that is not backed by AD.
3. **Company owns the mail domain** — the domain after `@` (e.g.
   `example.com`) must be one the organization owns.
   *Reason:* the company can only vouch for its own domains; otherwise
   someone could obtain a company-trusted cert for `jdoe@gmail.com` or
   `sales@competitor.com` and impersonate under the company's CA.
   *Validation (WLCA):* extract the domain from the SAN email and match it
   (case-insensitive) against the organization's **verified domain list**
   (AD accepted domains / UPN suffixes / RA config); not on the list →
   reject. This is a fast guardrail alongside point 1 (the full email must
   also match the AD `mail` attribute).
4. **CAA `issuemail` allows our CA — public-trust only; N/A in WLCA.**
   *Purpose:* the email version of CAA (RFC 9495). Before issuing an S/MIME
   cert for `jdoe@example.com`, the CA reads the mail domain's CAA record
   and checks the `issuemail` tag lists our CA; if a record exists and our
   CA is not listed, refuse.
   *Reason:* it lets the domain **owner** declare which CAs may issue S/MIME
   certs for their domain — the inverse of MCV (MCV proves the requester
   controls the mailbox; `issuemail` is the owner authorizing the CA).
   *Scope:* this is a public-trust, public-DNS mechanism. In WLCA's
   internal single-CA, AD-based scope it is **N/A** — there is one CA
   (always WLCA) and validation is via AD, not public DNS. Only relevant if
   WLCA ever issues publicly-trusted S/MIME certs.
5. **Person is verified (against AD)** — for sponsored certificates the CN
   carries a person's name, so it must match that person's Active Directory
   record: the cert CN must equal the AD **`displayName`** (anchored on
   `objectGUID`). In WLCA, "HR records" means Active Directory — no separate
   HR system is used.
6. **Evidence is fresh — public-trust windows; N/A in WLCA.**
   *Purpose:* verification proof does not stay valid forever and may be
   reused only within a window — mailbox-control proof ≤ 398 days, identity
   proof ≤ 825 days. The windows differ because a mailbox changes faster
   (an employee leaves and the address is reassigned) than a person's
   identity.
   *Scope:* these are public-trust (CABF SMBR) reuse windows for cached
   evidence. In WLCA they are **N/A** — the RA reads Active Directory
   **live at each issuance** (current `mail`, `displayName`, account
   state), so there is no cached proof to expire; freshness is inherent.
7. **EKU = emailProtection; KeyUsage for sign + encrypt.** An S/MIME cert
   does two jobs — signs outgoing mail and lets others send encrypted mail
   to the owner — so the RA checks both the role (EKU) and the two key-usage
   bits. The RA reads these from the CSR's requested extensions:

   | What the RA checks | How |
   |--------------------|-----|
   | EKU `emailProtection` present | Parse EKU; must contain `id-kp-emailProtection` (the email role) |
   | `serverAuth` / `clientAuth` absent | Reject if present (scope separation — not a TLS cert) |
   | KeyUsage `digitalSignature` | Present — used to **sign** the owner's outgoing mail |
   | KeyUsage `keyEncipherment` (RSA) or `keyAgreement` (EC) | Present per key type — lets senders **encrypt** mail to the owner (RSA wraps the AES key; EC derives it) |
   | KU matches key algorithm | An EC key must not carry `keyEncipherment` (it uses `keyAgreement`) |

   *(This is a CSR-content check — it applies in WLCA scope too, not just
   public trust.)*
8. **Profile and validity — enforced by the CA.** The certificate profile
   (e.g. Strict / Multipurpose) and the validity period are validated at
   the CA end through the mapped profile ID; the RA only selects the
   correct profile.
9. **Approval:** maker-checker required. A Maker (RA officer) reviews and
   submits the request, and a separate Checker approves it; the
   submitter/Maker cannot approve their own request (separation of duties).
   Even when the email matches AD, issuance is not automatic.

**Attributes to check (S/MIME) — quick reference:**

From the CSR:

| Attribute | Check |
|-----------|-------|
| SAN → rfc822Name | Valid email; present (mandatory for S/MIME) |
| Subject → CN | Person name (sponsored) or organization name |
| KeyUsage | `digitalSignature` + `keyEncipherment` (RSA, for encryption) |
| ExtendedKeyUsage | `emailProtection` present; `serverAuth`/`clientAuth` absent |

From Active Directory:

| AD attribute | Check |
|--------------|-------|
| `mail` / `proxyAddresses` | SAN email matches (primary email, or `smtp:` alias) |
| `displayName` | Matches the cert CN (person name, for sponsored certs) |
| accepted domains / UPN suffixes | Email domain is organization-owned |
| `userAccountControl` / `accountExpires` | Account is enabled, not locked or expired |
| `objectGUID` | Store as the immutable identity anchor |
| self / `managedBy` | Requester owns the mailbox (impersonation guard) |

## 4. CODE SIGNING certificate

**Use:** a company signs software (.exe/.jar) so the OS trusts it.
This is the most dangerous type — a mistake becomes signed malware.
**Example:** `CN=Example Corp`, `O=...`, `C=IN`,
EKU `codeSigning`, no SAN.

Extra checks:
1. **Bigger key — RSA ≥ 3072** (higher than the 2048 used by other types;
   ECDSA P-256/P-384 also accepted).
   *Why bigger:* a code signature must stay trustworthy for many years —
   software signed today is verified for 5–15+ years — so the key must be
   strong enough that it cannot be broken over that lifetime (≈128-bit
   security). A weak key broken later would let an attacker forge trusted
   signatures on malware.
   *How the RA validates:* read the public key from the CSR and check its
   size — for RSA the modulus must be ≥ 3072 bits; for EC the curve must be
   P-256/P-384; otherwise reject. (A CSR-content check — applies in every
   scope.)
2. **CN = the verified legal company name; O and C are mandatory.**
   *This name is what users see as "Publisher"* (e.g. `Publisher: Siemens
   AG`) when they install the software, so it must be a real, verified name
   — otherwise an attacker could put "Microsoft Corporation" and sign
   malware under it.
   *How the RA validates:* the legal name is verified once at customer
   onboarding (e.g. via the Handelsregister) and stored in AD when the
   customer is imported. At request time the RA matches the CSR's CN and O
   against that imported AD organization name, checks C is a valid ISO
   3166-1 country code, and rejects metadata-only values (`.`, `-`, ` `).
   No fresh registry lookup is done per request.
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
**Example:** `CN=John Doe`, `O=...`, `C=IN`,
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
| Identity depth | DV / OV | AD record | mailbox + AD | full legal + callback | AD identity |
| Auto-approve? | Yes (DV) | Yes (after binding) | No — maker-checker | **No — 2 officers** | **No — manual** |
| Validity cap (2026) | 200 days | 1 year | 824 days | 1 year | 3 years |

*End of RA-SPEC-003*
