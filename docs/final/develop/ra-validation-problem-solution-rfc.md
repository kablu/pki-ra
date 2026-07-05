```text
Internal Specification                              Salman Technologies
Category: Informational (Internal)                          PKI-RA Team
                                                            RA-SPEC-002
Companion to: RA-SPEC-001                                   5 July 2026


          Why Each RA Validation Exists: A Problem/Solution
              Analysis of CSR Processing Threats
```

## Abstract

RA-SPEC-001 specifies *what* the Registration Authority validates.
This companion document explains *why*, in problem/solution form:
for each validation area it describes the concrete failure or attack
that occurs when the check is absent — including real-world PKI
incidents where that failure actually happened — and then the
validation that closes it. It is written to be read aloud in an
architecture review: each entry stands alone, states its threat in
plain language, and maps to the normative requirements in
RA-SPEC-001.

## How to Read This Document

Each entry has the form:

```
P-nn  <short problem name>
  THE PROBLEM   what goes wrong, how the attack works, who exploits it
  REAL WORLD    an incident where this failure class actually occurred
  THE SOLUTION  the validation(s) that close it, with RA-SPEC-001 refs
```

Problems are ordered like the pipeline: cheap/structural problems
first, human/organizational problems last. Skipping ahead is safe —
entries do not depend on each other.

---

## Part I — Problems at the Front Door

### P-01: The RA as a free computation service

**THE PROBLEM.** Every validation the RA performs costs something:
CPU for signature verification, an LDAP round-trip for group checks,
a DNS query for DCV, a registry API call that may be metered. If an
unauthenticated caller can submit a request that triggers even one
of these, the RA has handed attackers an amplification primitive:
they spend one cheap HTTP POST, the RA spends an expensive lookup.
At volume this becomes denial of service; at low volume it becomes
reconnaissance — timing differences reveal which usernames exist,
which domains are registered for validation, which transaction IDs
are taken.

**REAL WORLD.** Every public ACME endpoint operates under constant
automated abuse; Let's Encrypt publishes rate-limit tiers precisely
because unthrottled validation endpoints were being used to exhaust
DNS resolution capacity and to probe account existence.

**THE SOLUTION.** Order the pipeline by cost and put the free checks
first (RA-SPEC-001 Sec 3). Authenticate before validating anything
(R-41-02): an anonymous request must never reach a parser, a
directory, or a resolver. Cap payload size before reading the body
(R-41-04), rate-limit per principal (R-41-05), and keep error
responses uniform in shape and timing so that failure codes reveal
the defect to the legitimate user without revealing system state to
a prober (R-14 hygiene, T14-06 in the master catalog).

### P-02: Authenticated is not authorized

**THE PROBLEM.** Enterprises correctly wire authentication first —
and then stop. The result: any employee with AD credentials can
request any certificate type, including code signing certificates in
the company's legal name. The intern's compromised laptop and the
release engineer's workstation have identical certificate-ordering
power. Attackers know this asymmetry well: phishing one low-value
account is cheap, and if authorization is flat, one account is all
they need.

**REAL WORLD.** The 2011 Comodo RA breach worked exactly this way:
an attacker compromised a single affiliate RA account and used its
flat authority to issue certificates for google.com, yahoo.com and
live.com. Nothing was wrong with the CA's cryptography — the
compromised account was simply *allowed to ask* for anything.

**THE SOLUTION.** Role-based authorization per certificate type
(R-41-03, R-AD-06..08): requesting CODE_SIGNING requires membership
in a dedicated, change-controlled group, distinct from TLS
requesters. Add per-org domain and identity scoping (T2-03..05) so
that even within a type, a requester can only name things their own
organization owns, and quotas (T2-06) so that one compromised
account has a bounded blast radius.

### P-03: The replayed and the duplicated request

**THE PROBLEM.** Networks time out; clients retry. Without an
idempotency contract, every retry is a brand-new certificate request
— three timeouts on one submission become four pending requests in
the officer queue. Officers approve the first, ignore the rest — or
worse, different officers approve *different* copies, and now
multiple live certificates exist for the same key and subject, only
one of which the subscriber knows about. An attacker who can capture
one legitimate submission can also deliberately replay it later to
resurrect a request the subscriber believes is complete.

**THE SOLUTION.** A client-supplied transaction ID with a database
uniqueness constraint (R-41-06) — enforced in the store, not in
application logic, because two concurrent retries will race any
in-memory check. Replays return the *original* request's state
rather than an error, which makes retry safe for well-behaved
clients and useless for attackers. Signed-request profiles add a
timestamp-skew window (T0-09) so captured requests expire.

---

## Part II — Problems Inside the CSR Bytes

### P-04: Two parsers, two truths

**THE PROBLEM.** A CSR is parsed at least twice: by the RA when
validating and by the CA when signing. ASN.1 offers many encodings
of "the same" structure — BER indefinite lengths, trailing bytes,
duplicate SET members, oversized integers. If the RA's parser
normalizes these one way and the CA's parser another way, an
attacker can construct a byte string with two truths: the RA reads
and approves `CN=harmless.salmantech.com`, the CA signs
`CN=login.bank.com`. Nothing in either system malfunctions — they
simply disagree about what the bytes mean, and the attacker engineers
the disagreement.

**REAL WORLD.** Parser differentials are an established
vulnerability class across PKI implementations: the 2008 MD5
rogue-CA work exploited CA-side normalization behavior, and multiple
CVEs in ASN.1 libraries (OpenSSL, NSS, GnuTLS) stem from lenient
re-encoding. Certificate linting tools (zlint, certlint) exist in
large part because CAs kept signing things their own tooling read
differently than browsers did.

**THE SOLUTION.** The RA enforces strict DER, not BER (R-42-04):
exactly one valid encoding exists for any structure, so there is
nothing to disagree about. One PEM block only (R-42-02), clean
base64 with no trailing data (R-42-03), version pinned to 0
(R-42-05). Then — critically — the RA submits to the CA the *exact
bytes it validated*, never a re-encoded copy, so the artifact that
was checked is the artifact that is signed.

### P-05: The pasted private key

**THE PROBLEM.** Subscribers produce CSRs by copy-pasting from
terminals, and the private key file sits in the same directory with
a nearly identical name. Sooner or later — in practice, weekly at
enterprise scale — someone pastes `server.key` instead of, or along
with, `server.csr`. The naive RA rejects with "invalid CSR format"
and the user tries again correctly. But the damage is already done
and is easy to miss: that private key has now crossed a network, sat
in an HTTP request body, and probably landed in an access log, a
WAF capture, and a crash dump. It is compromised in every meaningful
sense, yet the "correct" retry will certify it minutes later.

**THE SOLUTION.** Detect private-key material in the payload as a
distinct case (R-42-06) with three consequences, not one: reject the
request; compute the key's SPKI fingerprint and add it to the
permanent compromise blocklist; and alert the subscriber explicitly
that this key must never be used — generate a new one. The blocklist
entry is what turns a near-miss into a non-event: when the same key
arrives again next week inside a syntactically perfect CSR (R-43-07
lookup), the RA refuses it with a message that explains why.

### P-06: A certificate for someone else's key

**THE PROBLEM.** Public keys are, by definition, public — every TLS
handshake, every signed email, every published certificate hands
them out. Suppose the RA does not verify the CSR's self-signature.
An attacker copies the public key out of `bank.com`'s certificate,
wraps it in a fresh CSR that names the attacker's own domain, and
submits. The resulting certificate binds the *bank's key* to the
*attacker's name*. The attacker cannot decrypt the bank's traffic —
they lack the private key — but they gain a toolkit of confusion
attacks: signatures made by the bank can be attributed to the
attacker's identity ("that invoice was signed by MY certificate"),
encrypted mail can be misdirected, and cross-protocol tricks become
available. These attacks are obscure precisely because PoP checking
is nearly universal — which is what makes *skipping* it so
attractive a gap.

**THE SOLUTION.** Verify the CSR's self-signature against the public
key inside it (R-43-01), before any other cryptographic processing.
The signature can only have been produced by the holder of the
matching private key — this is the Proof of Possession. It is one
line of BouncyCastle (`csr.isSignatureValid(...)`) and it is the
single most important line in the RA. It must be a hard reject with
no override path: there is no legitimate scenario in which a CSR's
signature fails.

### P-07: Keys that were broken before they arrived

**THE PROBLEM.** A certificate asserts "this key is trustworthy for
the validity period." Some keys are disqualified before the request
is even made: generated by the 2008 Debian OpenSSL bug (only 32,767
possible keys per architecture — all enumerated and published),
generated by ROCA-vulnerable Infineon chips (factorable from the
public modulus alone), built from primes too close together
(Fermat-factorable in milliseconds), or simply already published in
a compromise corpus like pwnedkeys.com because they leaked from some
other system. Certifying such a key issues a credential whose
private half is effectively public. The subscriber will pass every
other check honestly — they usually don't know their key is broken.

**REAL WORLD.** ROCA (CVE-2017-15361) invalidated millions of
smartcard and TPM keys, including Estonia's national ID cards —
which had passed every procedural validation and had to be mass-
revoked. Debian weak keys kept appearing in CSRs for *years* after
the 2008 fix, because embedded systems shipped with pre-generated
keys.

**THE SOLUTION.** A key-screening battery (R-43-04..07) that runs on
every CSR: minimum sizes and sane exponents (cheap arithmetic), then
fingerprint lookups against the Debian corpus, the ROCA fingerprint
test, a Fermat closeness check, and the compromised-key blocklist —
all keyed by the SHA-256 hash of the SubjectPublicKeyInfo, computed
once. Revoked-for-compromise keys from the RA's own history feed the
same blocklist (R-43-08), so a key never comes back from the dead.

### P-08: One key, many masters

**THE PROBLEM.** Two distinct requests arrive over a month bearing
the *same public key* under *different subjects* — say,
`CN=service-a` and later `CN=payments-gateway`. There is no
legitimate workflow that produces this. Either a private key has
been stolen and the thief is now certifying it under their own
chosen name, or an administrator is cloning one key pair across
unrelated systems, collapsing the compromise boundary between them.
A quieter variant: the same key appears across certificate *types* —
the key in a TLS cert shows up in a document-signing request. That
one destroys non-repudiation: a "signature" can now be explained
away as a TLS artifact, and a court-facing certificate that can be
explained away is worthless.

**THE SOLUTION.** Index every certificate and request by SPKI
fingerprint. Same key + different subject → reject and *alert*, not
just reject — this is an incident signal, not a user error
(R-43-09). Same key + different certificate type → reject with
guidance to generate a dedicated key (R-43-10). Both checks are one
indexed database lookup on the fingerprint already computed for
P-07's screening.

---

## Part III — Problems in the Names and the Asks

### P-09: The name that lies to humans

**THE PROBLEM.** Certificate names are rendered to people — in
browser padlocks, OS publisher prompts, email clients, and the RA's
own officer console. Unicode gives an attacker everything needed to
make a false name render as a true one: Cyrillic "А" is pixel-
identical to Latin "A", a right-to-left override (U+202E) reverses
displayed text, zero-width characters make two different strings
look identical and *compare* different. The victim is not only the
end user; it is also the RA officer who approves "Аcme Corp" at 4:55
PM because it looks exactly like the Acme Corp they approve every
week. The machine-side twin of this problem is normalization drift:
"Acme Corp " (trailing space) and a TeletexString-encoded "Acme
Corp" bypass duplicate detection and vetted-org matching while
looking identical in every UI.

**REAL WORLD.** IDN homograph attacks are old enough to have
conference talks in 2005 (`pаypal.com` with Cyrillic а) and were
re-demonstrated against Chrome and Firefox in 2017 with a perfect
`аpple.com` proof of concept, punycode-registered and DV-certified —
every automated check passed, because the domain really was
registered and really was controlled by the researcher.

**THE SOLUTION.** Treat the DN as hostile input in two passes.
*Mechanical pass* (hard rejects): string-type allowlist (R-44-03),
control/invisible/bidi character ban (R-44-04), whitespace
normalization (R-44-06), length bounds (R-44-01), placeholder ban
(R-44-05). *Semantic pass* (route to humans): mixed-script and
confusable detection via ICU SpoofChecker (R-44-08) flags the name
for manual review rather than rejecting — mixed scripts are
legitimate in many locales, and the goal is an *informed* officer,
not a false-positive wall. Underneath both passes, R-44-07 pins O=
to the byte-exact vetted organization name, so a lying name would
also have to defeat vetting.

### P-10: The CSR that asks to become a CA

**THE PROBLEM.** Extensions are the certificate's powers, and the
CSR's extensionRequest attribute lets the requester *ask* for
powers. Three asks are catastrophic if honored. `basicConstraints
cA=TRUE` requests a subordinate CA: signed, it lets the holder mint
unlimited certificates trusted by the entire enterprise.
`keyUsage keyCertSign` is the same power as a bit flag.
`anyExtendedKeyUsage` requests an everything-certificate that
authenticates servers, signs code, and encrypts mail at once. These
arrive in real traffic constantly — mostly from developers who
copied an openssl CA tutorial, occasionally from tooling probing for
a misconfigured profile. The RA cannot tell the difference and must
not try.

**REAL WORLD.** In 2001 VeriSign issued two code-signing
certificates in Microsoft's name to an impersonator (a vetting
failure with extension-scale consequences); and the entire TLS BR
rule forbidding CA-capable end-entity certificates exists because
CAs *did* sign them — the 2012 Trustwave sub-CA and the 2013 ANSSI
incident both put CA power in end-entity hands, and both triggered
root-program crises.

**THE SOLUTION.** Unconditional rejects for the three catastrophic
asks (R-45-01..03) — no profile can enable them, no officer can
override. Everything else is allowlist-based per certificate type
(R-45-04, R-45-06): the profile enumerates the extensions and EKU
combinations it grants; anything not enumerated is rejected, because
"unknown" cannot be risk-rated after it is inside a signed artifact.
Fields the PKI itself computes — SKID, policies, SCTs — are ignored
if the client supplies them (R-45-07): they are outputs, never inputs.

---

## Part IV — Problems of Ownership and Control

### P-11: A certificate for a domain you don't own

**THE PROBLEM.** The entire value of a TLS server certificate is the
browser's inference: "the party presenting this controls the name I
typed." If the RA issues on request-say-so, that inference is false
and every HTTPS guarantee collapses — an attacker with a cert for
`mail.salmantech.com` plus any traffic-interception position (rogue
Wi-Fi, BGP hijack, DNS poisoning) is invisible to users. This is
not a theoretical adversary; it is the single most-attempted PKI
fraud, because the payoff is silent interception at scale.

**REAL WORLD.** DigiNotar (2011): attackers penetrated the CA/RA and
issued 500+ certificates including `*.google.com`, which were then
used to intercept Gmail traffic for ~300,000 users in Iran. The
company was distrusted by every browser within weeks and was
bankrupt within a month. It remains the canonical demonstration that
issuance controls, not cryptography, are what a PKI actually sells.

**THE SOLUTION.** Domain Control Validation for every dNSName, no
exceptions (R-51-06): the RA generates a high-entropy token, the
requester places it where only the domain's controller can — DNS TXT
record, HTTP well-known path, or ACME challenge — and the RA
verifies from *its own* resolvers. Wildcards require the DNS method
(R-51-07), because an HTTP file proves one host, not a namespace.
CAA checking (R-51-09) adds the inverse control — the domain owner's
published statement of which CAs may issue — and the shrinking
reuse windows (R-51-08: 200 days now, 10 by 2029) bound how long a
domain transfer can lag behind the evidence. Look-alike screening
(R-51-13) covers the residual gap DCV cannot: the phisher who
*genuinely controls* `salrnantech.com`.

### P-12: A certificate for a mailbox you can't read

**THE PROBLEM.** An S/MIME certificate for `salman@salmantech.com`
lets its holder sign mail *as Salman* and lets others encrypt mail
*to Salman* under that key. Issued to the wrong party, it doesn't
just enable impersonation — it redirects confidentiality: colleagues
dutifully encrypting to the certificate are encrypting to the
attacker. And unlike a spoofed From: header, the signature *proves*
authenticity to every client that checks it, converting the PKI from
a defense into the attack's credibility layer.

**THE SOLUTION.** Mailbox Control Validation (R-53-02): a random
value delivered to the exact address, echoed back through the RA
portal — readable only by someone with inbox access. At enterprise
scale, the SMBR's alternative applies: validate the *domain* once
(TLS-style DCV) and let the vetted organization attest mailbox
assignments — but then the organization's attestation is itself
evidence with a freshness window (R-53-04: 398 days), because the
fastest-changing fact in this triple is mailbox ownership: employees
leave and addresses are reassigned. Sponsor-validated profiles add
the HR-verified human identity (R-53-06), binding person, org, and
mailbox in one artifact.

### P-13: The colleague's client certificate

**THE PROBLEM.** Client certificates have no domain and no mailbox —
the name only means something inside the enterprise's own registries.
The attack is correspondingly internal: an authenticated developer
requests a certificate for `CN=payment-gateway` (another team's
service) or `CN=priya.sharma` (another person). Every downstream
mTLS service grants access by subject name, so the certificate *is*
the impersonation — no further exploit needed. This is the easiest
mis-issuance to commit accidentally (a typo in an automated pipeline
requesting certs for the wrong service) and the most useful one to
commit deliberately, because internal services rarely double-check
an identity the PKI has already vouched for.

**THE SOLUTION.** Two bindings, both mandatory. The subject must
exist in the authoritative registry — AD/HR for humans, CMDB/MDM for
services and devices (R-52-01) — and the authenticated requester
must *own* that identity: self for humans, registered owner/deployer
team for services (R-52-02). Ownership is a registry attribute, not
a form field. The EKU wall (R-52-03: clientAuth only, serverAuth
forbidden) contains the blast radius of anything that slips through:
a rogue client certificate must at least never be able to
impersonate a server.

---

## Part V — Problems of Identity and Intent

### P-14: The company that exists only on paper

**THE PROBLEM.** For code signing and document signing there is no
domain to challenge — the certificate's subject is a *legal
identity*, and the RA must establish it. The adversary here is
professional: malware distribution groups register real companies
(cost: a few hundred dollars), obtain real registration numbers,
build plausible websites, and submit technically flawless CSRs. If
the RA verifies identity by reading the documents the applicant
uploads, it verifies only that the applicant agrees with themselves.
The subtler variant is the *almost-right* name: "Microsofft
Solutions Ltd" is a genuinely registered company whose registry
lookup succeeds — the fraud is in what users will *think* the name
says on the UAC prompt.

**REAL WORLD.** Signed malware is an industry. Stuxnet (2010) used
driver-signing certificates stolen from Realtek and JMicron; the
2013 Bit9 breach existed solely to steal a code-signing capability;
and CA industry reports consistently show shell and look-alike
companies among code-signing applicants. Every one of those
artifacts passed cryptographic validation everywhere it went — the
signature was real; the *identity behind it* was the lie.

**THE SOLUTION.** Independence of evidence, as a rule with no
exceptions (R-60-01/02, R-54-05/06): legal existence from the
government registry directly, not from uploaded PDFs; the callback
phone number from the registry/QIIS, never from the application
form; requester authority confirmed by an organization contact
*other than* the requester. Layer reputation on top of identity
(R-54-07/08): malware-database and prior-revocation screening,
Levenshtein-distance typosquat detection against brand lists, and
sanctions lists — with hits routed to trained humans. And because
the stakes are executable trust, code signing is never auto-approved
(R-70-03): two officers, always.

### P-15: The stolen signing key

**THE PROBLEM.** Even a perfectly vetted publisher can lose their
private key — and a stolen code-signing key is the single most
valuable credential in the malware economy, because it converts any
payload into "trusted software" retroactively. Software keys are
stolen by anything that can read the developer's disk: infostealer
malware, CI-server compromise, a leaked laptop backup. The RA that
accepts a CSR from a software key has no idea whether one copy of
that key exists or ten thousand.

**REAL WORLD.** The 2022–2023 wave made this concrete: Lapsus$
leaked NVIDIA's code-signing certificates, which were signing
malware within *days* of the leak, and leaked driver-signing keys
from multiple vendors kept appearing in ransomware toolchains. This
is the incident class that pushed the CA/Browser Forum to mandate
hardware keys for all code signing from June 2023.

**THE SOLUTION.** Custody verification via key attestation
(R-54-04): the CSR must be accompanied by a statement, signed by the
hardware token itself and chained to the HSM vendor's root, that the
key was generated inside FIPS 140-2 L2 / CC EAL4+ hardware and is
non-exportable. The RA verifies the attestation chain against pinned
vendor roots and — the step naive implementations skip — verifies the
attested public key is byte-identical to the CSR's key. A software
key is now a hard reject with no officer override, and the shortened
one-year validity (R-54-09) bounds the damage window of anything
that leaks anyway.

### P-16: Yesterday's identity

**THE PROBLEM.** Every piece of vetting evidence describes the world
at the moment it was gathered — and the world moves. The employee in
the HR extract resigned last month; the domain in the DCV record was
sold; the company in the registry lookup filed for dissolution; the
video-KYC session shows a person who has since handed their token to
someone else. An RA that treats vetting as a one-time gate
accumulates a growing gap between what its certificates assert and
what is true. Attackers exploit the gap deliberately: buy an
expiring domain *because* its DCV evidence is still warm at some CA,
or activate a dormant vetted account after its owner departs.

**THE SOLUTION.** Evidence carries a timestamp and a shelf life, and
validity is evaluated at *decision time*, not collection time
(R-60-04). The windows are set per evidence type by how fast the
underlying fact changes — 825 days for organizational identity, 398
for mailbox control, 200-and-shrinking for domain control (SC-081),
two days for India video-KYC (R-55-04). Architecturally this means
the RA needs an evidence store with expiry semantics and a
revalidation scheduler — freshness is a *system*, not a checkbox at
onboarding. The joiner-mover-leaver hooks (R-AD-16/17) are the same
principle pushed to certificates already issued: when AD disables a
user, their live certificates go to revocation review automatically.

---

## Part VI — Problems Inside the RA Itself

### P-17: The officer who does everything

**THE PROBLEM.** The most dangerous account in the PKI is not the
subscriber's — it is the RA officer's. One officer who can submit,
vet, and approve a request is a complete issuance pipeline in a
single credential: one phished password, one bribed employee, one
disgruntled leaver, and certificates flow. The UI-only variant of
this failure is common and subtle: the web console hides the
"approve" button from the submitter, but the API endpoint behind it
checks nothing — and curl does not read UIs.

**REAL WORLD.** Comodo 2011 (again): the breached affiliate RA
account could perform validation and issuance alone — single-actor
authority is precisely what converted one compromised password into
rogue google.com certificates. Every post-incident root-program
reform since has pushed the same direction: no single human between
request and certificate.

**THE SOLUTION.** Separation of duties enforced where it cannot be
bypassed — in the data layer (R-70-02): the approval write fails if
`approver == submitter`, regardless of which client performed it.
High-stakes types require two independent officers (R-70-03).
Officers cannot vet their own organization's requests (R-60-08).
Approval-capable sessions require MFA and live AD group checks at
action time (R-AD-04/06/10), because the group membership that
mattered is the one at the *moment of approval*, not at login. And
every one of these decisions lands in the audit chain with the
officer's immutable identifier (P-20).

### P-18: Approve the innocent, issue the guilty

**THE PROBLEM.** A request is validated, then approved, then — in
the gap before CA submission — modified. Perhaps by its submitter
("just fixing a typo in the SAN"), perhaps by a compromised account
exploiting an edit endpoint that seemed harmless. The officers
approved one artifact; the CA signs another. Every validation that
ran is now attached to bytes that no longer exist, and the approval
records will happily testify to the wrong certificate. A cousin
failure needs no attacker at all: rules change between approval and
issuance — a validity cap shrinks (SC-081 does this on a schedule) —
and a stale approval sails through under yesterday's policy.

**THE SOLUTION.** Approvals bind to content, not to request IDs: any
modification voids the approval and resets the state machine
(R-70-04), enforced at the persistence layer so no endpoint can
forget it. The state machine itself permits only defined transitions
(R-70-01), pending requests expire (R-70-05), and policy limits are
re-evaluated at CA-send time, not only at submission (R-70-06). The
net effect: what the CA receives is provably the bytes the officers
saw, under the rules in force at issuance.

### P-19: Trusting the CA's answer

**THE PROBLEM.** The RA sends an approved request to the CA and gets
a certificate back. The comfortable assumption — "it's our internal
CA, it signed what we sent" — fails in two ways. Misconfiguration:
profile mappings drift, and the CA quietly issues with a wrong EKU,
a 5-year validity, or a sequential serial number; every such defect
is now a signed, possibly publicly-logged artifact. Compromise: a
CA (or the channel to it) that can be made to return a *different*
certificate than requested has turned the RA's approval into a
laundering step — the RA's records will show a clean approval for an
artifact it never examined. The callback variant is worse: an
unauthenticated issuance-callback endpoint lets an attacker inject
"your certificate is ready" payloads containing certificates the CA
never made.

**THE SOLUTION.** Verify-back as a mandatory stage (R-70-08): on
receipt, the RA confirms the certificate's subject, SAN set, SPKI,
extensions, validity dates, and serial-number entropy match the
approved request byte-for-byte, and that it chains to the expected
issuer — before the subscriber ever sees it. Callbacks authenticate
(T12-07): signed or mTLS, single-use CA transaction IDs, unknown
request IDs rejected. Pre-issuance linting (R-70-07) catches the
misconfiguration class before signing; CT logging (R-70-09) makes
whatever does get signed publicly discoverable.

### P-20: The validation that cannot be proven

**THE PROBLEM.** A WebTrust or ETSI auditor's method is sampling:
"here are 25 certificates you issued — show me the evidence for each
validation, each approval, each identity check." An RA that
performed every check flawlessly but logged casually — mutable rows,
usernames instead of immutable IDs, evidence overwritten by
revalidation — cannot answer. In an audited PKI the burden of proof
is inverted: *a validation that cannot be demonstrated is treated as
one that did not happen.* The same gap has a security face: an
insider who can edit history can erase the trail of anything they
did (and usernames get reused — "salman" in an old log may be a
different human than "salman" today).

**THE SOLUTION.** Treat evidence as the product (R-70-10, R-AD-19):
every verdict — pass and fail — is appended to a hash-chained,
append-only audit log carrying the requirement ID, the evidence
reference (the actual DNS response, the registry payload, the group
snapshot), the actor's immutable identifier (objectGUID, not name),
and the timestamp. Evidence is versioned, never overwritten;
retention follows the longest applicable regime (2 years post-expiry
under BR, 7 under CCA). The hash chain converts "trust our database"
into "verify our database" — tampering breaks the chain visibly.

---

## Closing Argument (for the architecture review)

Three sentences carry this entire document:

1. **The CA signs whatever the RA approves** — so every problem
   above is the RA's problem; there is no downstream safety net.

2. **Attackers submit perfect requests** — DigiNotar's certificates
   verified flawlessly, Stuxnet's signatures were valid, the
   homograph domain really was controlled by its registrant. The
   checks that stop real attacks are the ones that look past the
   cryptography: control, identity, freshness, custody, reputation,
   and process.

3. **Whatever cannot be proven did not happen** — the audit chain is
   not overhead on the validation pipeline; it is the deliverable
   that makes the pipeline worth running.

## Cross-Reference Index

| Problem | Closed by (RA-SPEC-001) |
|---------|------------------------|
| P-01 free computation | R-41-02/04/05, Sec 3 ordering |
| P-02 authn ≠ authz | R-41-03, R-AD-06..08, T2-03..06 |
| P-03 replay/duplicate | R-41-06, T0-09 |
| P-04 parser differential | R-42-02..05 |
| P-05 pasted private key | R-42-06, R-43-07 |
| P-06 no proof of possession | R-43-01 |
| P-07 broken keys | R-43-04..08 |
| P-08 key reuse/theft | R-43-09/10 |
| P-09 lying names | R-44-01..08 |
| P-10 CA-power asks | R-45-01..07 |
| P-11 domain not owned | R-51-06..09, R-51-13 |
| P-12 mailbox not readable | R-53-02..06 |
| P-13 colleague impersonation | R-52-01..03 |
| P-14 paper company | R-54-05..08, R-60-01..03 |
| P-15 stolen signing key | R-54-04, R-54-09 |
| P-16 stale evidence | R-60-04, R-51-08, R-55-04, R-AD-16/17 |
| P-17 single-actor officer | R-70-02/03, R-60-08, R-AD-04/06/10 |
| P-18 approve-then-modify | R-70-01/04/05/06 |
| P-19 trusting CA blindly | R-70-07/08/09, T12-07 |
| P-20 unprovable validation | R-70-10, R-AD-19 |

*End of RA-SPEC-002*
