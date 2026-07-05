# RA Validation Guide — Simple English

> What the RA must check when a CSR arrives, written in plain language.
> Every check has a reason. Covers all five certificate types.
> Detailed versions: [RA-SPEC-001](ra-csr-validation-rfc-spec.md) (formal spec with code),
> [RA-SPEC-002](ra-validation-problem-solution-rfc.md) (attack stories),
> [master catalog](ra-validation-master-catalog.md) (full 201-check list).

---

## The Big Picture

When a client sends a CSR, the RA checks it in a fixed order — cheap and
fast checks first, slow and human checks last:

```
CSR arrives
   │
   1. Is the request itself okay?        (who sent it, is it too big, is it a repeat)
   2. Is the CSR file readable?          (format, structure)
   3. Is the cryptography okay?          (does the sender really own the key, is the key strong)
   4. Are the names okay?                (no fake or misleading names)
   5. Are the requested powers okay?     (no dangerous extension requests)
   6. Type-specific checks               (different for TLS, email, code signing...)
   7. Is the person/company real?        (identity checks — depth depends on type)
   8. Approval workflow                  (right people say yes, everything is recorded)
   │
   Only if ALL pass → send to CA
```

**One rule above all: if any check fails or cannot run, STOP.**
Never skip a check because a system was down.

---

## Part 1 — Checks for EVERY CSR (all certificate types)

### 1.1 Request checks (before even reading the CSR)

| Check | What we do | Why |
|-------|-----------|-----|
| Who is calling? | User must log in (for us: username/password checked against Active Directory) | If anyone can send requests without a name, we cannot trust or trace anything |
| Are they allowed to ask for this? | Check the user's AD groups. Example: only the release team can ask for code signing certificates | Being logged in is not the same as being allowed. An intern should not be able to order the company's signing certificate |
| Is the request too big? | Reject anything over our size limit (for example 64 KB) before parsing it | A huge fake "CSR" can eat all server memory. Cheap check first, protect the expensive ones |
| Too many requests? | Rate limit per user | A stuck script or an attacker should not flood the queue |
| Did we see this exact request before? | Every request carries a unique transaction ID; duplicates are rejected by the database | Networks fail and clients retry. Without this, one submission becomes four pending requests |
| Is the input clean? | No strange characters, no script/SQL injection in text fields | These values get shown on officer screens and stored in the database — dirty input can attack our own tools |

### 1.2 Format checks (can we read the CSR?)

| Check | What we do | Why |
|-------|-----------|-----|
| Is it a proper PEM file? | Must have `-----BEGIN CERTIFICATE REQUEST-----` and matching END, exactly one block | Garbage in, garbage out. We must know exactly what we are looking at |
| Does it decode and parse? | Base64 must decode cleanly; the bytes must be a valid PKCS#10 structure in strict DER encoding | If our parser is relaxed and the CA's parser is strict (or the reverse), an attacker can make us approve one thing and the CA sign another. Strict parsing means there is only one way to read the bytes |
| Version must be 0 | PKCS#10 has only one version | Anything else means broken or hostile tooling |
| **Did they paste a private key by mistake?** | If the payload contains a PRIVATE KEY block: reject AND permanently blocklist that key | The moment a private key travels over the network, it is compromised forever. If we only reject, the user retries with the same (now leaked) key and we would certify it |

### 1.3 Cryptography checks (is the key honest and strong?)

| Check | What we do | Why |
|-------|-----------|-----|
| **Proof of Possession** | Verify the CSR's own signature using the public key inside it | This proves the sender actually holds the private key. Without this, anyone could copy a public key from someone else's certificate and get it issued under their own name. This is the most important single check in the RA |
| Signature algorithm | Only SHA-256 or better. Reject MD5 and SHA-1 | Old algorithms are broken — signatures can be forged |
| Key size | RSA at least 2048 bits (3072 for code signing). EC only P-256 or P-384 | Small keys can be cracked. Code signing needs bigger keys because those signatures must stay safe for years |
| Weak key screening | Check the key against known-bad lists: Debian bug keys, ROCA chips, already-leaked keys | Some keys are broken before they arrive. The owner usually doesn't even know. Certifying one means issuing a credential whose secret half is public |
| Key seen before? | Same key under a *different name* → reject and raise an alert | There is no honest reason for two people to have the same key. It means theft or dangerous copy-paste |
| Key used for another certificate type? | Reject (a TLS key must not become a signing key) | One key for everything means one theft breaks everything, and legal signatures lose their meaning |

### 1.4 Name checks (is the subject honest?)

| Check | What we do | Why |
|-------|-----------|-----|
| Field lengths and country code | CN max 64 chars, country must be a real ISO code like `IN` | Broken fields crash other software; fake countries break identity checks |
| No invisible tricks | Reject hidden characters (zero-width spaces, right-to-left overrides, control characters) | These make a fake name *look* exactly like a real one on screen — even to our own approving officer |
| Look-alike detection | Flag names mixing alphabets (Cyrillic "А" vs Latin "A") for human review | "Аcme Corp" with one Russian letter looks identical to the real one. A machine catches this; eyes cannot |
| No junk values | Reject "-", "N/A", "." as field values | Placeholder junk in a public certificate is forbidden and embarrassing |
| Clean spacing | No leading/trailing/double spaces | "Acme Corp " with a trailing space slips past duplicate checks and matching |
| **Company name must match our records** | The O= field must exactly equal a company name we have already verified for this user | Otherwise any logged-in user could put any company in the world into their certificate |

### 1.5 Extension checks (what powers are they asking for?)

Think of requested extensions as a **permission request**. Some requests
are never okay:

| Check | What we do | Why |
|-------|-----------|-----|
| Asking to be a CA? | `basicConstraints cA=TRUE` → always reject | If signed, the holder could create unlimited certificates trusted by the whole company. This is the biggest possible escalation |
| Asking for CA powers via KeyUsage? | `keyCertSign`, `cRLSign` → always reject | Same CA power, written as bit flags |
| Asking for "everything" usage? | `anyExtendedKeyUsage` → always reject | A do-everything certificate destroys all the boundaries between certificate types |
| Mixed purposes? | Example: serverAuth + codeSigning together → reject | One stolen certificate should never unlock two different worlds |
| Unknown extensions? | Anything not on the allowed list for this certificate type → reject | If we don't understand it, we cannot risk-rate it, and it would end up inside a signed artifact we cannot un-sign |
| Client sending SKID/policies/SCT values? | Ignore them — we compute these ourselves | These fields belong to the PKI, not to the requester |

Most of these arrive from developers copying CA tutorials, not from
attackers — but the RA cannot tell the difference and must reject both
the same way.

---

## Part 2 — Checks for EACH Certificate Type

Different certificate types answer different questions, so each type has
its own extra checks:

| Type | The question the certificate answers |
|------|--------------------------------------|
| TLS Server | "Does this server really control this domain name?" |
| TLS Client | "Is this really the employee/service it claims to be?" |
| S/MIME | "Does this person really own this mailbox?" |
| Code Signing | "Is this software publisher a real, trustworthy company?" |
| Document Signing | "Is this really the legal person whose name is on the document?" |

### 2.1 TLS Server (example: `api.salmantech.com`)

| Check | What we do | Why |
|-------|-----------|-----|
| Domain name in SAN | The domain must be in the SAN field (CN alone is not enough) | Browsers stopped reading CN in 2017. SAN is the real identity |
| Domain name is valid | Proper format, real registrable domain, no bare `*.com`, wildcard only as `*.something.com` | A certificate for `*.com` would be a master key for the internet |
| **Domain Control Validation (DCV)** | We give the requester a random code. They must place it in the domain's DNS (or on its web server). We check it ourselves | This proves they actually control the domain. It is THE check that stops someone getting a certificate for a bank's website |
| Wildcards need the DNS method | An uploaded file proves one server; only DNS proves the whole domain | A wildcard covers everything under the domain, so the proof must too |
| DCV must be fresh | Proof older than 200 days (2026 rule) cannot be reused; this window keeps shrinking (10 days by 2029) | Domains get sold. Old proof may belong to the previous owner |
| CAA record check | Read the domain's CAA DNS record; our CA must be allowed there | The domain owner can publish "only these CAs may issue for me" — we must obey it |
| No private addresses | No `10.x.x.x`, no `localhost`, no `.local` in public certificates | Nobody can "own" these names, so no one may be certified for them |
| Look-alike screening | Domains resembling banks/brands → human review | A phisher who registers `salrnantech.com` (rn instead of m) *really controls it* and passes DCV honestly. Only a reputation check catches this |
| Validity limit | Maximum 200 days (from March 2026; 47 days by 2029) | Industry rule. Also means: our issuance must be automated, or it will not scale |

### 2.2 TLS Client (example: `service-a`, or employee Salman's laptop)

| Check | What we do | Why |
|-------|-----------|-----|
| Identity exists in our registry | Human name must exist in AD/HR; service/device name must exist in the CMDB inventory | The name in a client certificate only means what our own records say. No record = no certificate |
| **Requester owns the identity** | Salman can request only for himself; a team can request only for services it owns | This blocks the number one internal attack: getting a certificate that says you are the payment gateway, or your colleague |
| clientAuth only | EKU must be clientAuth; serverAuth is forbidden | A client certificate that can also act as a server turns one hacked laptop into a man-in-the-middle tool |
| UPN/email matches directory | The SAN value must exactly match the AD record | Windows smartcard login maps through UPN. A mismatch is broken at best, spoofed at worst |
| Device still active? | Check the device is not retired or reported lost | Lost and retired devices must not receive fresh credentials |
| Short validity | Around 1 year maximum | Employees leave and devices disappear much faster than servers do |

### 2.3 S/MIME (example: `salman@salmantech.com`)

| Check | What we do | Why |
|-------|-----------|-----|
| Email address in SAN, valid format | `rfc822Name` must be present and well-formed | Mail programs match certificates by the SAN email |
| **Mailbox Control Validation** | Send a random code to that exact address; the requester must enter it in our portal | Proves they can actually read that inbox. Otherwise someone else could sign mail as Salman — and colleagues would encrypt secret mail *to the attacker* |
| Company owns the mail domain | The domain after @ must be on the company's verified list | A company may only sponsor addresses in domains it owns |
| Person is real (sponsored certificates) | The human name must match HR records | Enterprise S/MIME binds person + company + mailbox together; each part must be checked |
| Evidence freshness | Mailbox proof max 398 days old; identity proof max 825 days | People leave. Their addresses get given to new people. Old proof lies |
| Correct profile and validity | Strict or Multipurpose profile only (Legacy died July 2025); maximum 824 days | Industry rules — certificates on the dead profile are not trusted publicly |

### 2.4 Code Signing (example: Salman Technologies signs `Setup.exe`)

This is the most dangerous type: a wrongly issued certificate here
becomes **signed malware**. So the checks are the strictest.

| Check | What we do | Why |
|-------|-----------|-----|
| Stronger key | RSA minimum 3072 bits | Software signatures must stay trustworthy for many years after signing |
| Real legal company | We look the company up in the government registry (MCA/ROC in India) ourselves — we do NOT trust uploaded documents | Malware groups register real-looking shell companies. Documents from the applicant only prove the applicant agrees with themselves |
| **Independent callback** | We phone the company on a number taken from the registry — never the number written on the application | A phone number supplied by a fraudster connects to the fraudster |
| Requester is authorized | Someone at the company (not the requester) must confirm this person may order signing certificates | The certificate carries the company's name; the company must actually want it |
| **Key lives in hardware** | The private key must be generated inside a certified hardware token (FIPS 140-2 L2), proven by a "key attestation" signed by the token itself | Software keys get stolen by malware — the NVIDIA leak in 2022 was signing malware within days. Hardware keys cannot be copied out. Industry made this mandatory in 2023 |
| Name-trick screening | "Microsofft", "PayPa1" etc. → human review, likely reject | Users trust the publisher name on the install prompt. An almost-right name IS the attack, even when the company legally exists |
| Malware history | Check the applicant and the key against malware databases and our own past revocations | Repeat abusers come back with fresh, technically perfect requests |
| No SAN, exact name, single purpose | CN must be the exact vetted legal name; EKU codeSigning only | The certificate must do exactly one thing for exactly one verified name |
| **Never auto-approved** | Two different RA officers must both say yes | The cost of a mistake here is executable malware carrying our trust |
| Validity limit | Maximum 1 year (2026 rule) | If a key leaks anyway, the damage window is short |

### 2.5 Document Signing (example: Salman signs legal PDFs — India DSC)

| Check | What we do | Why |
|-------|-----------|-----|
| Real person, full KYC | Aadhaar eKYC / PAN with attested documents / bank KYC | This signature can end up in court. The name must be a legally proven identity |
| **Fresh video verification** | Video KYC must be done within 2 days before issuance (CCA rule in India) | The regulator demands proof that the real person, right now, is requesting this |
| Non-repudiation bit | KeyUsage must include `nonRepudiation` | This is the flag that lets a court say "you cannot deny you signed this." Without it the certificate is legally decorative |
| No TLS or code-signing usage | Those EKUs must be absent | A signing key that also does TLS gives the signer an excuse to deny signatures |
| Hardware token | Key must live on a certified crypto token (CCA rule) | "Only the signer could have signed" must survive cross-examination |
| Company link (if company is named) | Employment proof plus company authorization | The person signs on behalf of the company — both identities need checking |
| Validity | 1, 2, or 3 years only (CCA rule) | Regulator-defined options |

---

## Part 3 — After Validation: Workflow Checks

Even a perfectly valid CSR must go through a safe process:

| Check | What we do | Why |
|-------|-----------|-----|
| Fixed state flow | SUBMITTED → VALIDATED → APPROVED → SENT_TO_CA → ISSUED, no jumping | A request that could skip a state would skip its checks |
| **Maker-checker** | The person who submits can never be the person who approves — enforced in the database | One person alone must never be able to push a request from start to certificate. The 2011 Comodo breach was exactly one account with full power |
| Two approvers for code signing | Both must independently agree | Highest risk, most eyes |
| Edit cancels approval | Any change to an approved request sends it back for re-approval | Classic trick: get an innocent request approved, then "fix a typo" that changes the domain |
| Old requests expire | Pending requests die after a set time | The evidence behind an old approval has gone stale |
| Re-check limits at send time | Validity caps are re-applied just before sending to the CA | Rules change between request and issuance (the 200-day TLS rule arrived on a fixed date) |
| **Check what the CA returns** | The issued certificate must match the approved request exactly — name, key, extensions, dates | "It's our internal CA" is not a reason to skip this. Misconfiguration produces the same wrong certificate as an attack |
| Authenticated callbacks | The CA's "certificate ready" callback must be authenticated, with a one-time transaction ID | Otherwise anyone could inject fake "issued" certificates into our system |
| **Record everything** | Every check's result (pass AND fail) goes into an append-only, tamper-evident audit log, with the evidence and the acting person's permanent ID | Auditors work by sampling: "prove these 25 certificates were validated." A check we cannot prove is treated as a check we never did |

---

## Part 4 — One-Page Summary

```
EVERY CSR:            login (AD) → allowed? → size/rate/duplicate →
                      PEM/DER parse → pasted-key trap →
                      Proof of Possession → algorithm/key strength →
                      weak-key lists → key reuse →
                      name hygiene → look-alike screen → org matches records →
                      no CA powers → extension allowlist

TLS SERVER adds:      SAN rules → DCV (prove domain control) → CAA →
                      freshness (200d) → look-alike domains → validity <= 200d

TLS CLIENT adds:      exists in AD/CMDB → requester OWNS the identity →
                      clientAuth only → UPN matches → device active → <= 1y

S/MIME adds:          email challenge (prove mailbox) → org owns domain →
                      HR check → freshness 398d/825d → validity <= 824d

CODE SIGNING adds:    RSA 3072 → registry lookup → independent callback →
                      authority proof → hardware key attestation →
                      malware/typosquat/sanctions screen →
                      TWO officers, never auto → validity <= 1y

DOC SIGNING adds:     full KYC → video KYC <= 2 days old → nonRepudiation bit →
                      hardware token → employment proof → validity <= 3y

ALWAYS:               maker-checker → edit voids approval →
                      verify what CA returns → log everything, fail closed
```

**The three sentences to remember:**

1. The CA signs whatever the RA approves — there is no safety net after us.
2. Attackers send *perfect* requests — the checks that stop them are about
   control, identity, freshness, and process, not just cryptography.
3. A check we cannot prove happened is a check that never happened.

