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

**Examples (Java / Spring Boot):**

```yaml
# R-41-01: application.yml — TLS 1.2+ only
server:
  ssl:
    enabled-protocols: TLSv1.2,TLSv1.3
```

```java
// R-41-02 + R-41-03: authentication + RBAC per certificate type
@PostMapping("/api/v1/csr")
@PreAuthorize("hasRole('CERT_REQUESTER') and @certPolicy.canRequest(principal, #req.certificateType)")
public ResponseEntity<CsrResponse> submit(@Valid @RequestBody CsrRequest req) { ... }
```

```java
// R-41-04: size cap BEFORE parsing
if (req.getCsrPem().length() > maxCsrBytes)           // e.g. 65_536
    throw new RaValidationException("PKI_REQ_002", "CSR exceeds size limit");

// R-41-05: rate limit (Bucket4j)
if (!buckets.resolve(clientId).tryConsume(1))
    throw new RateLimitException("PKI_REQ_005");      // → HTTP 429

// R-41-06: idempotency — DB unique constraint is the real guard
try { requestRepo.save(entity); }                     // clientTxnId UNIQUE
catch (DataIntegrityViolationException e) {
    throw new RaValidationException("PKI_REQ_006", "Duplicate clientTxnId");
}
```

```java
// R-41-07: strict schema — unknown JSON fields rejected
@Bean Jackson2ObjectMapperBuilderCustomizer strict() {
    return b -> b.featuresToEnable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
}

// R-41-08: injection screen on free-text fields
private static final Pattern SAFE = Pattern.compile("^[\\p{L}\\p{N} .,'()\\-]{1,128}$");
if (!SAFE.matcher(req.getRequesterNote()).matches())
    throw new RaValidationException("PKI_REQ_008", "Illegal characters");

// R-41-09: per-tenant IP allowlist (OncePerRequestFilter)
if (!tenant.allowedCidrs().stream().anyMatch(c -> c.contains(remoteIp)))
    throw new AccessDeniedException("Source IP not allowlisted");
```

#### 4.1.1 Active Directory Authentication Profile

When R-41-02/R-41-03 are implemented against Active Directory, AD becomes
three things at once: the authenticator, the RBAC source, and the identity
registry. Each role carries its own validations. The core risk is the
**time gap**: AD can change between token issuance, group resolution, and
approval — so token claims are acceptable for cheap actions (submit), but
sensitive actions (approve, CA-send) MUST use a live AD lookup.

**(a) At authentication time (every request)**

| ID | Lvl | Validation | Why |
|----|-----|-----------|-----|
| R-AD-01 | MUST | Kerberos ticket / OIDC token (ADFS/Entra) cryptographically valid: signature, `iss`, `aud`, `exp`, nonce | A token borrowed from another app must not replay here |
| R-AD-02 | MUST | LDAPS (636) or StartTLS with DC certificate verification; never simple bind in plaintext | Bind credentials and queries must not be sniffable |
| R-AD-03 | MUST | RA clock NTP-synced with DCs (Kerberos 5-min skew) | Auth failures and replay-window drift |
| R-AD-04 | MUST | LIVE account-state check at sensitive actions: `userAccountControl` ACCOUNTDISABLE (0x2), LOCKOUT (0x10), PASSWORD_EXPIRED; `accountExpires` | The user may have been disabled AFTER the token was issued — token valid, user invalid |
| R-AD-05 | MUST | Distinguish human vs service accounts (gMSA/OU/naming); service accounts may submit, MUST NOT approve | Automation accounts must not bypass maker-checker |

**(b) Authorization — AD groups → RA roles**

| ID | Lvl | Validation | Why |
|----|-----|-----------|-----|
| R-AD-06 | MUST | Fresh group resolution (LDAP) for approvals — do not trust token group claims | A user removed from a group must not approve with an old token |
| R-AD-07 | MUST | Resolve nested groups: matching rule `1.2.840.113556.1.4.1941` or `tokenGroups` — `memberOf` is direct-only | Missed nested membership = wrong deny, or worse, wrong allow |
| R-AD-08 | MUST | Dedicated, change-controlled groups (`RA_CERT_REQUESTERS`, `RA_OFFICERS`, `RA_CODESIGN_APPROVERS`); audit who can edit them | Whoever edits the AD group effectively grants RA roles — that group IS the trust boundary |
| R-AD-09 | MUST | SoD enforced per-request at action time (this submitter ≠ this approver), not merely per-group | One user may legitimately hold both roles for different requests |
| R-AD-10 | MUST | Verify MFA claim (`amr` / authentication method) for officer sessions | Password-only sessions must not approve |

**(c) AD as identity registry (TLS client / S/MIME subjects)**

| ID | Lvl | Validation | Why |
|----|-----|-----------|-----|
| R-AD-11 | MUST | Cert subject ↔ AD attributes exact match: SAN UPN = `userPrincipalName`, SAN email = `mail`, CN = `displayName`/`cn` | Smartcard logon maps via UPN; mismatch = broken or spoofed logon |
| R-AD-12 | MUST | Authenticated identity == certificate subject identity (or an explicit delegation record) | Salman must not obtain a cert for a colleague |
| R-AD-13 | MUST | Persist `objectGUID`/`objectSid`, not just username | Usernames are reused; a new "salman" must not inherit the old salman's certs |
| R-AD-14 | MUST | Escape all user input in LDAP filters (Spring `LdapQueryBuilder`/`LdapEncoder`) | `cn=*)(uac=*` style injection can dump the directory |
| R-AD-15 | MUST | RA's bind account is read-only, least-privilege, scoped to needed OUs/attributes | An RA compromise must not become an AD compromise |

**(d) Lifecycle integration (most commonly forgotten)**

| ID | Lvl | Validation | Why |
|----|-----|-----------|-----|
| R-AD-16 | MUST | Leaver hook: AD disable/delete → auto-revoke (or review-queue) the user's active certs | An ex-employee's valid client cert is ghost access |
| R-AD-17 | MUST | Mover hook: group/OU change re-evaluates entitlements; voids pending requests that lost their basis | Old entitlements must not survive role changes |
| R-AD-18 | SHOULD | Stale-account guard: `lastLogonTimestamp` older than policy (e.g. 90 days) → manual review | Dormant-account takeover is a classic entry point |
| R-AD-19 | MUST | Audit evidence includes the group snapshot, `objectGUID`, and DC response at verdict time | The auditor's question is "was the officer in the group AT THAT MOMENT?" — needs proof, not memory |

**Examples (Java / Spring LDAP / Spring Security):**

```java
// R-AD-04: live account-state check at approval time (not from the token)
DirContextOperations ctx = ldapTemplate.searchForContext(
    query().base("OU=Users,DC=corp,DC=salmantech,DC=in")
           .where("objectGUID").is(officer.objectGuid()));          // R-AD-13: GUID, not name
int uac = Integer.parseInt(ctx.getStringAttribute("userAccountControl"));
if ((uac & 0x2) != 0 || (uac & 0x10) != 0)                          // disabled / locked
    throw new AccessDeniedException("Account disabled/locked in AD");

// R-AD-06 + R-AD-07: fresh, transitive group check (LDAP_MATCHING_RULE_IN_CHAIN)
boolean isOfficer = !ldapTemplate.search(
    query().where("memberOf:1.2.840.113556.1.4.1941:")
           .is("CN=RA_OFFICERS,OU=Groups,DC=corp,DC=salmantech,DC=in")
           .and("objectGUID").is(officer.objectGuid()),
    (AttributesMapper<String>) a -> "x").isEmpty();
if (!isOfficer) throw new AccessDeniedException("Not in RA_OFFICERS (live check)");
```

```java
// R-AD-10: MFA claim on the officer's OIDC session
List<String> amr = principal.getClaimAsStringList("amr");
if (amr == null || amr.stream().noneMatch(Set.of("mfa", "otp", "hwk")::contains))
    throw new AccessDeniedException("MFA required for approval actions");

// R-AD-11 + R-AD-12: subject binding for a TLS client / S/MIME request
if (!upnFromCsr.equalsIgnoreCase(ctx.getStringAttribute("userPrincipalName"))
        || !principal.objectGuid().equals(requestedForGuid))
    throw new RaValidationException("PKI_CLI_004", "CSR subject not bound to requester");

// R-AD-14: never concatenate user input into filters
LdapQuery q = query().where("cn").is(userSuppliedCn);   // Spring escapes internally

// R-AD-16: leaver hook — scheduled reconciliation
@Scheduled(cron = "0 */15 * * * *")
void revokeLeavers() {
    for (ActiveCert cert : certRepo.findActiveClientCerts())
        if (adClient.isDisabledOrGone(cert.subjectObjectGuid()))
            revocationService.requestRevocation(cert, Reason.AFFILIATION_CHANGED);
}
```

### 4.2 Syntactic Validation (PKCS#10)

| ID | Lvl | Validation | Why | Ref |
|----|-----|-----------|-----|-----|
| R-42-01 | MUST | CSR payload present and non-empty | Fail fast, precise error | — |
| R-42-02 | MUST | Valid PEM armor `CERTIFICATE REQUEST`; exactly one block | Multi-block/garbage input hides content from review | RFC 7468 |
| R-42-03 | MUST | Base64 decodes cleanly, no trailing data | Trailing bytes = smuggling/parser confusion | RFC 7468 |
| R-42-04 | MUST | DER parses as `CertificationRequest`; reject BER/indefinite length | Parser-differential attacks: RA and CA seeing different content | RFC 2986 |
| R-42-05 | MUST | PKCS#10 `version` == 0 | Only defined version; anything else is malformed | RFC 2986 §4 |
| R-42-06 | MUST | Reject if a PRIVATE KEY block accompanies the CSR; permanently blocklist that key | The key is now compromised by definition | — |

**Examples (Java / BouncyCastle):**

```java
// R-42-01 + R-42-02 + R-42-03: PEM armor, single block, clean decode
String pem = req.getCsrPem().trim();
if (pem.isEmpty()) throw new RaValidationException("PKI_CSR_001", "Empty CSR");

// R-42-06 FIRST — private key pasted by accident?
if (pem.contains("PRIVATE KEY")) {
    keyBlocklistService.blockForever(extractSpkiHash(pem));   // key is burned
    throw new RaValidationException("PKI_CSR_009", "Private key received — key blocklisted");
}
if (countOccurrences(pem, "-----BEGIN") != 1)
    throw new RaValidationException("PKI_CSR_003", "Exactly one PEM block required");

// R-42-04: strict DER parse via BouncyCastle
try (PEMParser p = new PEMParser(new StringReader(pem))) {
    Object obj = p.readObject();
    if (!(obj instanceof PKCS10CertificationRequest csr))
        throw new RaValidationException("PKI_CSR_004", "Not a PKCS#10 CertificationRequest");

    // R-42-05: version MUST be 0
    if (!BigInteger.ZERO.equals(
            csr.toASN1Structure().getCertificationRequestInfo().getVersion().getValue()))
        throw new RaValidationException("PKI_CSR_005", "PKCS#10 version must be 0");
} catch (IOException e) {
    throw new RaValidationException("PKI_CSR_004", "ASN.1/DER parse failed");
}
```

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

**Examples (Java / BouncyCastle):**

```java
// R-43-01: Proof of Possession — the most important line in the whole RA
boolean popOk = csr.isSignatureValid(
        new JcaContentVerifierProviderBuilder().setProvider("BC")
            .build(csr.getSubjectPublicKeyInfo()));
if (!popOk) throw new RaValidationException("PKI_CRY_001", "PoP signature invalid");

// R-43-02: signature algorithm allowlist (OIDs, not names — names can be spoofed)
private static final Set<ASN1ObjectIdentifier> ALLOWED_SIG = Set.of(
    PKCSObjectIdentifiers.sha256WithRSAEncryption,
    PKCSObjectIdentifiers.sha384WithRSAEncryption,
    X9ObjectIdentifiers.ecdsa_with_SHA256,
    X9ObjectIdentifiers.ecdsa_with_SHA384);
if (!ALLOWED_SIG.contains(csr.getSignatureAlgorithm().getAlgorithm()))
    throw new RaValidationException("PKI_CRY_002", "Weak/unknown signature algorithm");
```

```java
// R-43-03..05: key extraction + RSA rules
PublicKey pub = new JcaPKCS10CertificationRequest(csr).getPublicKey();
if (pub instanceof RSAPublicKey rsa) {
    int bits = rsa.getModulus().bitLength();
    int min  = certType == CertType.CODE_SIGNING ? 3072 : 2048;      // R-43-04
    if (bits < min || bits > 8192)
        throw new RaValidationException("PKI_KEY_001", "RSA size " + bits);
    BigInteger e = rsa.getPublicExponent();                          // R-43-05
    if (e.compareTo(BigInteger.valueOf(65537)) < 0 || !e.testBit(0))
        throw new RaValidationException("PKI_KEY_002", "Weak RSA exponent");
}

// R-43-06: EC curve allowlist — compare parameters, not the curve name string
if (pub instanceof ECPublicKey ec && !isAllowedCurve(ec.getParams()))   // P-256/P-384
    throw new RaValidationException("PKI_KEY_003", "Curve not allowed");
```

```java
// R-43-07 + R-43-08 + R-43-09: SPKI fingerprint drives every key-reputation check
byte[] spkiHash = MessageDigest.getInstance("SHA-256")
        .digest(csr.getSubjectPublicKeyInfo().getEncoded());
if (keyBlocklistService.isBlocked(spkiHash))          // Debian/ROCA/pwnedkeys/revoked
    throw new RaValidationException("PKI_KEY_004", "Compromised/weak key");
certRepo.findBySpkiHash(spkiHash).stream()            // R-43-09: cross-subject reuse
    .filter(c -> !c.subjectDn().equals(requestedDn))
    .findAny().ifPresent(c -> auditAlert("SPKI reuse across subjects", c));

// R-43-10: key reuse across cert types
if (certRepo.existsBySpkiHashAndTypeNot(spkiHash, certType))
    throw new RaValidationException("PKI_KEY_005", "Key already used for another cert type");

// R-43-11: PQC pilot (BC 1.78+ supports ML-DSA)
if ("ML-DSA-65".equals(pub.getAlgorithm()) && !profile.pqcPilotEnabled())
    throw new RaValidationException("PKI_KEY_006", "PQC only under pilot profile");
```

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

**Examples (Java / BouncyCastle):**

```java
X500Name subject = csr.getSubject();

// R-44-01: RFC 5280 upper bounds
String cn = getFirst(subject, BCStyle.CN);            // IETFUtils.valueToString(...)
if (cn != null && cn.length() > 64)
    throw new RaValidationException("PKI_DN_001", "CN exceeds 64 chars");

// R-44-02: ISO 3166-1 country
String c = getFirst(subject, BCStyle.C);
if (c != null && !Set.of(Locale.getISOCountries()).contains(c))
    throw new RaValidationException("PKI_DN_002", "Invalid country: " + c);

// R-44-03: only UTF8String / PrintableString encodings
for (RDN rdn : subject.getRDNs())
    for (AttributeTypeAndValue atv : rdn.getTypesAndValues()) {
        ASN1Encodable v = atv.getValue();
        if (!(v instanceof DERUTF8String || v instanceof DERPrintableString))
            throw new RaValidationException("PKI_DN_003", "Illegal DN string type");
    }
```

```java
// R-44-04: control / invisible / bidi-override characters
private static final Pattern FORBIDDEN =
    Pattern.compile("[\\p{Cc}\\p{Cf}\\u202A-\\u202E\\u200B-\\u200F]");
if (FORBIDDEN.matcher(cn).find())
    throw new RaValidationException("PKI_DN_004", "Invisible/control character in CN");

// R-44-05 + R-44-06: placeholders and whitespace hygiene
if (Set.of("-", ".", "N/A", "NA", "NULL").contains(cn.trim().toUpperCase())
        || !cn.equals(cn.trim()) || cn.contains("  "))
    throw new RaValidationException("PKI_DN_005", "Placeholder/whitespace defect");

// R-44-07: O must equal the requester's vetted org (DB, not user input)
String o = getFirst(subject, BCStyle.O);
if (o != null && !vettingRepo.activeOrgNames(principal.orgId()).contains(o))
    throw new RaValidationException("PKI_DN_007", "O does not match vetted organization");

// R-44-08: mixed-script homoglyph screen (ICU4J)
if (new SpoofChecker.Builder().setChecks(SpoofChecker.MIXED_SCRIPT_CONFUSABLE)
        .build().failsChecks(cn))
    flagForManualReview("PKI_DN_008", "Mixed-script CN: " + cn);

// R-44-09 + R-44-10: deprecated attrs, reserved names
if (certType.isTls() && getFirst(subject, BCStyle.OU) != null)
    throw new RaValidationException("PKI_DN_009", "OU deprecated for TLS (CABF 2022)");
if (RESERVED.matcher(cn).matches())                    // localhost|*.local|10\..* ...
    throw new RaValidationException("PKI_DN_010", "Reserved/internal name");
```

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

**Examples (Java / BouncyCastle):**

```java
// Extract requested extensions from the PKCS#10 attribute
Extensions ext = null;
for (Attribute a : csr.getAttributes(PKCSObjectIdentifiers.pkcs_9_at_extensionRequest))
    ext = Extensions.getInstance(a.getAttributeValues()[0]);
if (ext == null) return;                               // nothing requested — profile fills defaults

// R-45-01: cA=TRUE forbidden
BasicConstraints bc = BasicConstraints.fromExtensions(ext);
if (bc != null && bc.isCA())
    throw new RaValidationException("PKI_EXT_001", "basicConstraints cA=TRUE forbidden");

// R-45-02: CA-only KeyUsage bits
KeyUsage ku = KeyUsage.fromExtensions(ext);
if (ku != null && (ku.hasUsages(KeyUsage.keyCertSign) || ku.hasUsages(KeyUsage.cRLSign)))
    throw new RaValidationException("PKI_EXT_002", "keyCertSign/cRLSign forbidden");

// R-45-03 + R-45-04: EKU rules per profile
ExtendedKeyUsage eku = ExtendedKeyUsage.fromExtensions(ext);
if (eku != null) {
    if (eku.hasKeyPurposeId(KeyPurposeId.anyExtendedKeyUsage))
        throw new RaValidationException("PKI_EXT_003", "anyExtendedKeyUsage forbidden");
    for (KeyPurposeId kp : eku.getUsages())
        if (!profile.allowedEkus(certType).contains(kp))          // cross-type combos die here
            throw new RaValidationException("PKI_EXT_004", "EKU not allowed: " + kp);
}
```

```java
// R-45-05: KU consistent with key algorithm
if (pub instanceof ECPublicKey && ku != null && ku.hasUsages(KeyUsage.keyEncipherment))
    throw new RaValidationException("PKI_EXT_005", "keyEncipherment meaningless for EC");

// R-45-06 + R-45-07: allowlist of extension OIDs; authoritative fields ignored
for (ASN1ObjectIdentifier oid : ext.getExtensionOIDs()) {
    if (AUTHORITATIVE.contains(oid)) continue;         // SKID/AKID/policies/SCT → RA overwrites
    if (!profile.allowedExtensionOids(certType).contains(oid))
        throw new RaValidationException("PKI_EXT_006", "Unprofiled extension: " + oid);
}

// R-45-08: SAN count + duplicates
GeneralNames san = GeneralNames.fromExtensions(ext, Extension.subjectAlternativeName);
if (san != null) {
    List<GeneralName> names = List.of(san.getNames());
    if (names.size() > 100 || names.size() != Set.copyOf(names).size())
        throw new RaValidationException("PKI_EXT_008", "SAN count/duplicate violation");
}
```

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

**Examples (Java / BC / Spring Boot — dnsjava + Guava):**

```java
// R-51-01: SAN mandatory; CN (if present) must be repeated in SAN
List<String> dnsNames = extractDnsNames(san);          // GeneralName.dNSName entries
if (dnsNames.isEmpty())
    throw new RaValidationException("PKI_TLS_001", "TLS server cert requires SAN dNSName");
if (cn != null && !dnsNames.contains(cn.toLowerCase()))
    throw new RaValidationException("PKI_TLS_001", "CN not present in SAN");

// R-51-02..05: FQDN syntax, registrability, wildcard, IDN — Guava InternetDomainName
for (String name : dnsNames) {
    String host = name.startsWith("*.") ? name.substring(2) : name;
    InternetDomainName idn = InternetDomainName.from(host);        // syntax (throws)
    if (idn.isPublicSuffix())                                       // R-51-03
        throw new RaValidationException("PKI_TLS_003", "Bare public suffix: " + name);
    if (name.contains("*") && !name.startsWith("*."))               // R-51-04
        throw new RaValidationException("PKI_TLS_004", "Illegal wildcard: " + name);
    if (host.contains("xn--")) homographScreen(IDN.toUnicode(host)); // R-51-05
}
```

```java
// R-51-06: DCV — DNS TXT method (dnsjava); one token per authorization domain
String expected = dcvTokenRepo.tokenFor(requestId, domain);
Lookup lookup = new Lookup("_pki-validation." + domain, Type.TXT);
boolean dcvOk = Arrays.stream(Optional.ofNullable(lookup.run()).orElse(new Record[0]))
    .flatMap(r -> ((TXTRecord) r).getStrings().stream())
    .anyMatch(expected::equals);
if (!dcvOk) throw new RaValidationException("PKI_DCV_001", "DNS TXT token not found");

// R-51-07 + R-51-08: wildcard needs DNS method; evidence freshness
if (name.startsWith("*.") && evidence.method() != DcvMethod.DNS_TXT)
    throw new RaValidationException("PKI_DCV_002", "Wildcard requires DNS-based DCV");
if (evidence.ageDays() > policy.dcvReuseDays())        // 200 (2026) → 100 → 10
    throw new RaValidationException("PKI_DCV_003", "DCV evidence stale — revalidate");
```

```java
// R-51-09: CAA (RFC 8659) — walk up the tree, check ≤ 8h before issuance
for (Record r : Optional.ofNullable(new Lookup(domain, Type.CAA).run()).orElse(new Record[0])) {
    CAARecord caa = (CAARecord) r;
    if ("issue".equals(caa.getTag()) && !caa.getValue().startsWith("ourca.example"))
        throw new RaValidationException("PKI_CAA_001", "CAA does not authorize our CA");
}

// R-51-10 + R-51-11: IP SAN policy, validity clamp (SC-081)
if (generalName.getTagNo() == GeneralName.iPAddress && isRfc1918(ipBytes))
    throw new RaValidationException("PKI_TLS_010", "Private IP in public cert");
int granted = Math.min(req.getRequestedValidityDays(), policy.tlsMaxDays()); // 200 in 2026

// R-51-13: risk screen hook (Safe Browsing / internal list) → manual queue, not auto-reject
if (riskService.isHighValueOrLookalike(domain)) workflow.routeToManualReview(requestId);
```

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

**Examples (Java / Spring Boot — LDAP + CMDB):**

```java
// R-52-01: identity must exist in the authoritative registry
switch (subjectKind) {
    case HUMAN -> {
        if (ldapTemplate.search(query().where("cn").is(cn), attrMapper).isEmpty())
            throw new RaValidationException("PKI_CLI_001", "No AD record for: " + cn);
    }
    case SERVICE -> {
        CmdbEntry svc = cmdbClient.findService(cn)
            .orElseThrow(() -> new RaValidationException("PKI_CLI_001", "Not in CMDB"));
        // R-52-06: asset must be ACTIVE
        if (svc.status() != Status.ACTIVE)
            throw new RaValidationException("PKI_CLI_006", "Service retired/inactive");
        // R-52-02: requester must own the identity — the impersonation guard
        if (!svc.ownerTeams().contains(principal.teamId()))
            throw new RaValidationException("PKI_AUTHZ_003", "Requester does not own " + cn);
    }
}
```

```java
// R-52-03: clientAuth only, serverAuth forbidden
if (eku == null || !eku.hasKeyPurposeId(KeyPurposeId.id_kp_clientAuth))
    throw new RaValidationException("PKI_CLI_003", "EKU clientAuth required");
if (eku.hasKeyPurposeId(KeyPurposeId.id_kp_serverAuth))
    throw new RaValidationException("PKI_CLI_003", "serverAuth forbidden in client cert");

// R-52-04: UPN otherName (1.3.6.1.4.1.311.20.2.3) must equal AD userPrincipalName
String upn = extractUpnOtherName(san);                 // parse OtherName → UTF8String
if (upn != null && !upn.equalsIgnoreCase(adUser.getUserPrincipalName()))
    throw new RaValidationException("PKI_CLI_004", "UPN mismatch with directory");

// R-52-05: hostname-shaped CN in a client cert = type confusion
if (cn.matches("([a-z0-9-]+\\.)+[a-z]{2,}"))
    flagForManualReview("PKI_CLI_005", "FQDN-style CN in client cert: " + cn);

// R-52-07: policy validity
int granted = Math.min(req.getRequestedValidityDays(), 365);
```

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

**Examples (Java / BC / Spring Boot — JavaMail):**

```java
// R-53-01: rfc822Name SAN mandatory + syntax
String email = extractRfc822Name(san)                  // GeneralName.rfc822Name
    .orElseThrow(() -> new RaValidationException("PKI_SMM_001", "rfc822Name SAN required"));
try { new InternetAddress(email, /*strict*/ true).validate(); }   // RFC 5321-ish
catch (AddressException e) {
    throw new RaValidationException("PKI_SMM_001", "Invalid mailbox: " + email);
}

// R-53-03: mail domain must be org-verified
String mailDomain = email.substring(email.indexOf('@') + 1).toLowerCase();
if (!vettingRepo.verifiedMailDomains(principal.orgId()).contains(mailDomain))
    throw new RaValidationException("PKI_SMM_003", "Domain not sponsored by org");
```

```java
// R-53-02: Mailbox Control Validation — random-value challenge (SMBR 3.2.2)
String code = HexFormat.of().formatHex(SecureRandom.getInstanceStrong().generateSeed(16));
mcvRepo.save(new McvChallenge(requestId, email, sha256(code), Instant.now()));
mailSender.send(mime -> {
    mime.setRecipients(TO, email);                     // the EXACT mailbox, nothing else
    mime.setSubject("Certificate request verification");
    mime.setText("Enter this code in the RA portal: " + code);
});
// later, on portal submit:
if (!mcvRepo.matches(requestId, sha256(submittedCode)))
    throw new RaValidationException("PKI_SMM_002", "Mailbox challenge failed");

// R-53-04 + R-53-06: evidence freshness windows
if (mcvEvidence.ageDays() > 398)
    throw new RaValidationException("PKI_SMM_004", "MCV stale — rechallenge");
if (profile == SmimeProfile.SPONSOR_VALIDATED && hrEvidence.ageDays() > 825)
    throw new RaValidationException("PKI_SMM_006", "Identity evidence expired");

// R-53-05 + R-53-07 + R-53-08: profile, EKU/KU, validity
if (req.getProfile() == SmimeProfile.LEGACY)           // retired July 2025
    throw new RaValidationException("PKI_SMM_005", "Legacy profile retired");
if (!eku.hasKeyPurposeId(KeyPurposeId.id_kp_emailProtection))
    throw new RaValidationException("PKI_SMM_007", "EKU emailProtection required");
int granted = Math.min(req.getRequestedValidityDays(), 824);
```

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

**Examples (Java / BC / Spring Boot):**

```java
// R-54-01: CSBR key floor — stricter than TLS
if (pub instanceof RSAPublicKey rsa && rsa.getModulus().bitLength() < 3072)
    throw new RaValidationException("PKI_CSG_001", "Code signing requires RSA >= 3072");

// R-54-02 + R-54-03: CN = vetted legal name; no SAN; single-purpose EKU/KU
if (!cn.equals(vettedOrg.legalName()))
    throw new RaValidationException("PKI_CSG_002", "CN must be exact vetted legal name");
if (san != null)
    throw new RaValidationException("PKI_CSG_003", "SAN not permitted in code signing");
if (!eku.hasKeyPurposeId(KeyPurposeId.id_kp_codeSigning) || eku.getUsages().length != 1)
    throw new RaValidationException("PKI_CSG_003", "EKU must be codeSigning only");
```

```java
// R-54-04: key attestation — verify statement chains to HSM vendor root (e.g. YubiKey)
X509Certificate attest = parseCert(req.getAttestationCertPem());
CertPath path = certFactory.generateCertPath(List.of(attest, req.intermediate()));
PKIXParameters params = new PKIXParameters(yubicoTrustAnchors);   // vendor roots, pinned
params.setRevocationEnabled(false);
CertPathValidator.getInstance("PKIX").validate(path, params);     // throws on failure
// attested key MUST equal the CSR key
if (!Arrays.equals(attest.getPublicKey().getEncoded(),
                   csr.getSubjectPublicKeyInfo().getEncoded()))
    throw new RaValidationException("PKI_CSG_004", "Attestation key != CSR key");
```

```java
// R-54-05: registry lookup (MCA/QIIS connector)
OrgRecord rec = registryClient.lookup(vettedOrg.registrationNumber())
    .orElseThrow(() -> new RaValidationException("PKI_CSG_005", "Org not in registry"));
if (rec.status() != OrgStatus.ACTIVE)
    throw new RaValidationException("PKI_CSG_005", "Org not ACTIVE: " + rec.status());

// R-54-06: verified callback — contact from registry, NEVER from the application
CallbackTask task = workflow.scheduleCallback(requestId, rec.registryPhone());  // not req.phone()!

// R-54-07: malware/abuse + typosquat screening (Levenshtein vs brand list)
for (String brand : brandList)
    if (LevenshteinDistance.getDefaultInstance().apply(normalize(cn), brand) <= 2
            && !normalize(cn).equals(brand))
        workflow.routeToManualReview(requestId, "Possible typosquat of " + brand);
if (malwareIntelClient.isKnownAbuser(vettedOrg, spkiHash))
    throw new RaValidationException("PKI_CSG_007", "Malware association");

// R-54-08 + R-54-09: sanctions; validity cap (2026)
if (sanctionsClient.isListed(vettedOrg, rec.directors()))
    throw new RaValidationException("PKI_CSG_008", "Sanctions hit");
int granted = Math.min(req.getRequestedValidityDays(), 365);      // 1y since 2026-02-15
```

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

**Examples (Java / BC / Spring Boot):**

```java
// R-55-01: CN must equal the KYC-verified name
if (!cn.equals(kycRecord.verifiedFullName()))
    throw new RaValidationException("PKI_DOC_001", "CN != KYC-verified name");

// R-55-02: nonRepudiation (contentCommitment) mandatory
if (ku == null || !ku.hasUsages(KeyUsage.nonRepudiation))
    throw new RaValidationException("PKI_DOC_002", "nonRepudiation KU required");

// R-55-03: TLS/code-signing EKUs forbidden
if (eku != null && (eku.hasKeyPurposeId(KeyPurposeId.id_kp_serverAuth)
        || eku.hasKeyPurposeId(KeyPurposeId.id_kp_clientAuth)
        || eku.hasKeyPurposeId(KeyPurposeId.id_kp_codeSigning)))
    throw new RaValidationException("PKI_DOC_003", "Out-of-scope EKU");
```

```java
// R-55-04: CCA IVG — KYC mode + video freshness (<= 2 days at ISSUANCE time)
if (!EnumSet.of(KycMode.AADHAAR_EKYC, KycMode.PAN_ATTESTED, KycMode.BANK_KYC)
        .contains(kycRecord.mode()))
    throw new RaValidationException("PKI_DOC_004", "Unsupported KYC mode");
if (Duration.between(kycRecord.videoVerifiedAt(), Instant.now()).toDays() > 2)
    throw new RaValidationException("PKI_DOC_004", "Video KYC stale — redo (CCA IVG)");

// R-55-05: token-resident key + validity 1/2/3 years
if (!req.hasTokenAttestation())                        // FIPS 140-2 L2 token proof
    throw new RaValidationException("PKI_DOC_005", "Crypto-token key evidence required");
if (!Set.of(365, 730, 1095).contains(req.getRequestedValidityDays()))
    throw new RaValidationException("PKI_DOC_005", "Validity must be 1/2/3 years");

// R-55-06: org-person cert — both identities vetted
if (o != null && !hrClient.isEmployedBy(kycRecord.personId(), o))
    throw new RaValidationException("PKI_DOC_006", "No employment proof for O=" + o);
```

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

**Examples (Java / Spring Boot):**

```java
// R-60-01: registry-backed org verification, stored as evidence
OrgRecord rec = registryClient.lookup(org.registrationNumber()).orElseThrow(...);
evidenceRepo.save(Evidence.of(requestId, "ORG_REGISTRY", rec.rawResponse(), Instant.now()));

// R-60-02: contacts from registry only — the field from the application is IGNORED
String verifiedPhone = rec.registryPhone();            // never req.getContactPhone()

// R-60-03: authenticated != authorized
if (!authorityRepo.hasAuthority(principal.userId(), org.id(), certType))
    throw new RaValidationException("PKI_VET_003", "Requester lacks authority for org");

// R-60-04: generic freshness gate — every evidence type carries its own window
for (Evidence e : evidenceRepo.forRequest(requestId))
    if (e.ageDays() > policy.maxAgeDays(e.type()))     // 825 org / 200 DCV / 2 video-KYC
        throw new RaValidationException("PKI_VET_004", e.type() + " evidence stale");

// R-60-05..07: agreement, sanctions, velocity
if (!agreementRepo.hasCurrentSignedAgreement(org.id(), certType))
    throw new RaValidationException("PKI_VET_005", "Subscriber agreement missing/outdated");
if (sanctionsClient.isListed(org)) throw new RaValidationException("PKI_VET_006", "Sanctions");
if (requestRepo.countSince(org.id(), Instant.now().minus(1, HOURS)) > policy.hourlyBurst())
    workflow.routeToManualReview(requestId, "Velocity anomaly");

// R-60-08: conflict-of-interest bar, enforced in the service layer
if (officerRepo.orgOf(officerId).equals(request.orgId()))
    throw new AccessDeniedException("Officer cannot vet own organization");
```

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

**Examples (Java / BC / Spring Boot):**

```java
// R-70-01: state machine — transitions defined once, enforced everywhere
public enum ReqState { SUBMITTED, VALIDATED, APPROVED, SENT_TO_CA, ISSUED, REJECTED;
    private static final Map<ReqState, Set<ReqState>> LEGAL = Map.of(
        SUBMITTED, Set.of(VALIDATED, REJECTED),
        VALIDATED, Set.of(APPROVED, REJECTED),
        APPROVED,  Set.of(SENT_TO_CA, REJECTED),
        SENT_TO_CA, Set.of(ISSUED, REJECTED));
    public void assertCanGoTo(ReqState next) {
        if (!LEGAL.getOrDefault(this, Set.of()).contains(next))
            throw new IllegalStateTransitionException(this + " -> " + next);
    }
}

// R-70-02 + R-70-03: maker-checker in the service layer (never UI-only)
if (request.getSubmittedBy().equals(approverId))
    throw new RaValidationException("PKI_WFL_002", "Submitter cannot approve (SoD)");
if (certType == CertType.CODE_SIGNING && request.approvals().size() < 2)
    return;                                            // stays pending until 2nd officer

// R-70-04 + R-70-05: modification voids approval; stale requests expire
@PreUpdate void onModify() { if (state == APPROVED) state = VALIDATED; approvals.clear(); }
@Scheduled(cron = "0 0 * * * *")
void expireStale() { requestRepo.expireOlderThan(Instant.now().minus(30, DAYS)); }
```

```java
// R-70-06 + R-70-07: re-clamp at CA-send + pre-issuance lint
int days = Math.min(request.getApprovedDays(), policy.currentMaxDays(certType)); // rules moved?
LintResult lint = zlintRunner.lint(tbsCertificateBytes);            // exec zlint, parse JSON
if (lint.hasErrors())
    throw new RaValidationException("PKI_WFL_007", "Lint: " + lint.firstError());

// R-70-08: verify the CA returned EXACTLY what was approved
X509CertificateHolder issued = new X509CertificateHolder(caResponse.certDer());
if (!issued.getSubject().equals(approvedSubject)
        || !Arrays.equals(issued.getSubjectPublicKeyInfo().getEncoded(),
                          csr.getSubjectPublicKeyInfo().getEncoded())
        || issued.getNotAfter().after(Date.from(maxNotAfter))
        || issued.getSerialNumber().bitLength() < 64)
    throw new RaValidationException("PKI_WFL_008", "Issued cert deviates from approval");

// R-70-09 + R-70-10: CT presence; hash-chained audit trail
if (certType == CertType.TLS_SERVER && issued.getExtension(SCT_LIST_OID) == null)
    throw new RaValidationException("PKI_WFL_009", "Missing SCTs");
auditRepo.append(AuditEntry.builder().requestId(requestId).verdict(verdict)
    .evidenceRef(evidenceId)
    .prevHash(auditRepo.lastHash())                    // tamper-evident chain
    .build());
```

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


