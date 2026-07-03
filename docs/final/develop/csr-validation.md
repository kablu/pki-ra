# CSR Validation Guide — Enterprise RA

**Audience:** End Users (Certificate Requestors)  
**Context:** Jab tu CSR banata hai aur `POST /api/ra/requests` karta hai,
RA ye sab checks karta hai — ek ek karke.  
**Branch:** csr-approval-dev

---

## Certificate Types

| # | Type | Use Case |
|---|------|----------|
| 1 | **TLS_SERVER** | Website/API HTTPS — `api.acme.com` |
| 2 | **TLS_CLIENT** | User/device authentication — mTLS, VPN |
| 3 | **SMIME** | Email sign + encrypt — Outlook |
| 4 | **CODE_SIGNING** | Software/script sign karna |
| 5 | **DOCUMENT_SIGNING** | PDF sign karna |

---

## Part A — Common Validations (Sab Types Pe Hote Hain)

Ye validations **har certificate type** ke liye same hain.

---

### V-01 — CSR Payload Present

| Field | Detail |
|-------|--------|
| **Check** | `pkcs10` field blank nahi hona chahiye |
| **Reason** | Bina CSR ke kuch validate hi nahi kar sakte |
| **Error** | PKI_VAL_001 — HTTP 400 |
| **Applies To** | ALL types |

---

### V-02 — CSR Size Limit

| Field | Detail |
|-------|--------|
| **Check** | PEM string 8 KB se bada nahi hona chahiye |
| **Reason** | Bada payload parse karna CPU-heavy hai — DoS se bachao |
| **Error** | PKI_VAL_003 — HTTP 400 |
| **Applies To** | ALL types |

---

### V-03 — PEM Format

| Field | Detail |
|-------|--------|
| **Check** | `-----BEGIN CERTIFICATE REQUEST-----` se shuru ho, `-----END CERTIFICATE REQUEST-----` pe khatam ho |
| **Reason** | Galat header = parse fail = cryptic error. Seedha batao user ko. |
| **Error** | PKI_VAL_002 — HTTP 400 |
| **Applies To** | ALL types |

---

### V-04 — ASN.1 / DER Parse

| Field | Detail |
|-------|--------|
| **Check** | PEM ke andar ka Base64 → DER bytes → valid PKCS#10 structure banta hai |
| **Reason** | Agar structure hi invalid hai to aage sab bekar hai |
| **Error** | PKI_VAL_002 — HTTP 400 |
| **Applies To** | ALL types |

---

### V-05 — Proof of Possession (Self-Signature Verify)

| Field | Detail |
|-------|--------|
| **Check** | CSR ke andar jo public key hai, usi se CSR ka signature verify hota hai |
| **Reason** | Yahi sabse important check hai. Agar signature valid nahi → ya to kisi ne CSR tamper kiya, ya tune kisi aur ki public key daal di. Matlab tu private key hold nahi karta — certificate ban bhi jaye to use nahi kar sakta. |
| **Error** | PKI_CRYPTO_001 — HTTP 422 |
| **Applies To** | ALL types |

```
CSR ke andar: SubjectPublicKeyInfo (public key)
CSR ke andar: signature (private key se bana)

RA check karta hai:
  RS256_Verify(CertificationRequestInfo, signature, publicKey)
  → VALID   = tune apni private key se sign kiya ✓
  → INVALID = key mismatch ya tampered CSR ✗
```

---

### V-06 — Signature Algorithm (SHA1 / MD5 Band)

| Field | Detail |
|-------|--------|
| **Check** | SHA1withRSA, MD5withRSA, MD2withRSA — ye sab reject |
| **Reason** | SHA1 2017 se officially broken hai. Aise cert banate hai to browsers/OS reject kar deta hai. |
| **Error** | PKI_CRYPTO_002 — HTTP 422 |
| **Applies To** | ALL types |

| Algorithm | Status |
|-----------|--------|
| MD2withRSA | REJECTED |
| MD5withRSA | REJECTED |
| SHA1withRSA | REJECTED |
| SHA1withECDSA | REJECTED |
| SHA256withRSA | ALLOWED |
| SHA384withRSA | ALLOWED |
| SHA512withRSA | ALLOWED |
| SHA256withECDSA | ALLOWED |
| Ed25519 | ALLOWED |

---

### V-07 — Key Size Minimum

| Field | Detail |
|-------|--------|
| **Check** | RSA minimum 2048 bits, EC minimum P-256 |
| **Reason** | RSA-1024 factored ja sakti hai commodity hardware se. NIST ne 2010 mein deprecate kiya. |
| **Error** | PKI_KEY_001 — HTTP 422 |
| **Applies To** | ALL types |

| Key Type | Minimum | Recommended |
|----------|---------|-------------|
| RSA | 2048 bits | 4096 bits |
| EC | P-256 (256-bit) | P-384 |
| Ed25519 | Always OK | — |
| Ed448 | Always OK | — |
| RSA-512 | REJECTED | — |
| RSA-1024 | REJECTED | — |

---

### V-08 — CN Must Be Present

| Field | Detail |
|-------|--------|
| **Check** | Subject DN mein `CN=` field hona mandatory hai |
| **Reason** | CN = certificate holder ka naam/identity. Bina CN ke cert meaningless hoti hai. |
| **Error** | PKI_POL_001 — HTTP 422 |
| **Applies To** | ALL types |

---

### V-09 — BasicConstraints cA=TRUE Forbidden

| Field | Detail |
|-------|--------|
| **Check** | Agar CSR mein `BasicConstraints cA=TRUE` hai → reject |
| **Reason** | Iska matlab tu ek CA certificate maang raha hai jisse tu khud doosron ke certificates sign kar sake. End-user ko ye kabhi nahi milta. |
| **Error** | PKI_POL_008 — HTTP 422 |
| **Applies To** | ALL types |

---

### V-10 — KeyUsage CA-Only Bits Forbidden

| Field | Detail |
|-------|--------|
| **Check** | `keyCertSign` aur `cRLSign` bits CSR mein nahi hone chahiye |
| **Reason** | Ye bits sirf CA certificates mein hote hain. End-entity ke CSR mein ye = suspicious / attack attempt. |
| **Error** | PKI_POL_009, PKI_POL_010 — HTTP 422 |
| **Applies To** | ALL types |

---

### V-11 — anyExtendedKeyUsage Forbidden

| Field | Detail |
|-------|--------|
| **Check** | EKU mein `anyExtendedKeyUsage` (OID 2.5.29.37.0) nahi hona chahiye |
| **Reason** | Ye effectively "koi bhi purpose allowed hai" bolta hai — har security control bypass ho jaata hai |
| **Error** | PKI_POL_012 — HTTP 422 |
| **Applies To** | ALL types |

---

### V-12 — Duplicate CSR Detection

| Field | Detail |
|-------|--------|
| **Check** | Same CSR bytes (SHA-256 hash of DER) pichle 24 ghante mein submit hua tha? |
| **Reason** | Network retry ya double-click se same request baar baar na aaye. Replay attack se bachao. |
| **Error** | PKI_APR_009 — HTTP 409 Conflict |
| **Applies To** | ALL types |

---

### V-13 — clientTxnId Unique

| Field | Detail |
|-------|--------|
| **Check** | Tune jo transaction ID bheja wo pehle kabhi use nahi hua hona chahiye |
| **Reason** | Idempotency — same transaction dobara process na ho |
| **Error** | PKI_VAL_002 — HTTP 422 |
| **Applies To** | ALL types |

---

## Part B — Certificate Type Specific Validations

---

## 1. TLS_SERVER Certificate

**Use:** `api.acme.com`, `payments.acme.com` — server pe HTTPS ke liye  
**Generates with:**
```bash
openssl req -new -key server.key \
  -subj "/CN=api.acme.com/O=Acme Corp/C=IN" \
  -addext "subjectAltName=DNS:api.acme.com" \
  -addext "keyUsage=critical,digitalSignature,keyEncipherment" \
  -addext "extendedKeyUsage=serverAuth" \
  -out server.csr
```

| # | Validation | Check | Reason | Error Code |
|---|-----------|-------|--------|------------|
| S-01 | **CN must be FQDN** | `api.acme.com` ✓ — `John Doe` ✗ — `acme` ✗ | Browsers CN ko hostname match karte hain — space wala naam browser reject karega | PKI_POL_013 |
| S-02 | **CN mein spaces nahi** | `John Doe` → reject | Server ka naam kabhi space wala nahi hota | PKI_POL_013 |
| S-03 | **CN IP address nahi** | `192.168.1.1` → reject | IP ke liye SAN iPAddress use karo, CN nahi | PKI_POL_014 |
| S-04 | **CN single label nahi** | `acme` → reject, `acme.com` chahiye | Single label valid FQDN nahi hoti | PKI_POL_014 |
| S-05 | **SAN dNSName MANDATORY** | `subjectAltName=DNS:api.acme.com` hona hi chahiye | RFC 2818 — 2017 se sab browsers CN ignore karte hain, sirf SAN dekhte hain | PKI_POL_004 |
| S-06 | **SAN dNSName valid FQDN** | `DNS:api..acme.com` ✗ — `DNS:-api.acme.com` ✗ | Galat FQDN = TLS handshake fail | PKI_POL_005 |
| S-07 | **Wildcard sirf ek level** | `*.acme.com` ✓ — `*.*.acme.com` ✗ | RFC 5280 — multi-level wildcard allowed nahi | PKI_POL_005 |
| S-08 | **KeyUsage: digitalSignature** | TLS handshake ke liye required | Bina is bit ke TLS authentication kaam nahi karta | PKI_POL_011 (warn) |
| S-09 | **EKU: serverAuth** | `id-kp-serverAuth` hona chahiye | Browsers serverAuth EKU validate karte hain | PKI_POL_011 (warn) |

### FQDN Rules (TLS_SERVER CN aur SAN dNSName dono ke liye)

```
MUST:
  ✓ Total length: 1–253 characters
  ✓ Each label (part between dots): 1–63 characters
  ✓ Label characters: only [a-zA-Z0-9-]
  ✓ Label: must NOT start or end with hyphen
  ✓ Minimum 2 labels (hostname.tld)
  ✓ TLD (last part): letters only, 2–63 chars
  ✓ Wildcard: only *.hostname.tld format

MUST NOT:
  ✗ Spaces anywhere
  ✗ Consecutive dots (..)
  ✗ IP address format (192.168.x.x)
  ✗ Reserved names (localhost)
  ✗ Numeric-only TLD (api.acme.123)
  ✗ Multi-level wildcard (*.*.acme.com)
```

### TLS_SERVER Common Mistakes

| Mistake | CN Value | What Happens |
|---------|----------|--------------|
| Person name as CN | `John Doe` | PKI_POL_013: CN contains spaces |
| No domain in CN | `acme` | PKI_POL_014: not a valid FQDN |
| IP in CN | `192.168.1.1` | PKI_POL_014: IP not allowed as CN |
| No SAN at all | absent | PKI_POL_004: SAN missing |
| Bad FQDN in SAN | `DNS:api..acme.com` | PKI_POL_005: invalid FQDN in SAN |

---

## 2. TLS_CLIENT Certificate

**Use:** Ravi ka laptop → server ko prove karna "main Ravi hun" — mTLS, VPN, smartcard login  
**Generates with:**
```bash
openssl req -new -key client.key \
  -subj "/CN=John Doe/O=Acme Corp/C=IN" \
  -addext "subjectAltName=email:john@acme.com" \
  -addext "keyUsage=critical,digitalSignature" \
  -addext "extendedKeyUsage=clientAuth" \
  -out client.csr
```

| # | Validation | Check | Reason | Error Code |
|---|-----------|-------|--------|------------|
| C-01 | **CN person/service name OK** | `John Doe`, `svc-payments` — spaces allowed | Client cert mein person naam hota hai | — (OK) |
| C-02 | **CN max 128 chars** | 128 se bada → warning | Kuch CA systems truncate kar dete hain | PKI_POL_015 (warn) |
| C-03 | **SAN rfc822Name optional** | `email:ravi@acme.com` — present ho to format valid hona chahiye | Email SAN se mail clients bhi cert use kar sakte hain | PKI_POL_020 (warn) |
| C-04 | **SAN otherName UPN optional** | `ravi@acme.com` UPN format | AD mein smartcard login ke liye UPN SAN required hoti hai | — (optional) |
| C-05 | **KeyUsage: digitalSignature** | Authentication ke liye mandatory | Client apna identity signature se prove karta hai | PKI_POL_011 (warn) |
| C-06 | **EKU: clientAuth recommended** | `id-kp-clientAuth` | Server yahi EKU check karta hai mTLS mein | PKI_POL_014 (warn) |
| C-07 | **CN hostname jaisa nahi hona chahiye** | `api.acme.com` as CN in client cert → warning | Client cert mein server-style hostname suspicious hai | PKI_POL_015 (warn) |

---

## 3. S/MIME Certificate

**Use:** Ravi Outlook se email sign + encrypt karta hai  
**Generates with:**
```bash
openssl req -new -key smime.key \
  -subj "/CN=Ravi Sharma/O=Acme Corp/C=IN" \
  -addext "subjectAltName=email:ravi@acme.com" \
  -addext "keyUsage=critical,digitalSignature,nonRepudiation,keyEncipherment" \
  -addext "extendedKeyUsage=emailProtection" \
  -out smime.csr
```

| # | Validation | Check | Reason | Error Code |
|---|-----------|-------|--------|------------|
| M-01 | **CN person naam hona chahiye** | `Ravi Sharma` ✓ | Email cert mein person identify hota hai | PKI_POL_001 |
| M-02 | **SAN rfc822Name MANDATORY** | `email:ravi@acme.com` hona hi chahiye | RFC 5322 — modern mail clients CN nahi, SAN email dekhte hain | PKI_POL_006 |
| M-03 | **Email format valid** | `ravi@acme.com` ✓ — `ravi@` ✗ — `ravi` ✗ | Galat email = mail delivery fail | PKI_POL_020 |
| M-04 | **KeyUsage: digitalSignature** | Email signing ke liye | Signature se recipient verify karta hai email actually tune bheji | PKI_POL_011 (warn) |
| M-05 | **KeyUsage: keyEncipherment (RSA key)** | Email encryption ke liye RSA | Sender tere public key se email encrypt karta hai | PKI_POL_011 (warn) |
| M-06 | **KeyUsage: keyAgreement (EC key)** | Email encryption ke liye EC | EC ka ECDH-based encryption mechanism | PKI_POL_011 (warn) |
| M-07 | **EKU: emailProtection MANDATORY** | `id-kp-emailProtection` | Mail clients yahi check karte hain | PKI_POL_015 (warn) |
| M-08 | **Signing + Encryption alag keys recommended** | Ek hi cert dono ke liye → warning | Encryption key backup honi chahiye (key escrow). Signing key never backed up. | PKI_POL_011 (warn) |

---

## 4. CODE_SIGNING Certificate

**Use:** Ravi ek `.exe` ya `.jar` sign karta hai taaki Windows/OS trust kare  
**Generates with:**
```bash
openssl req -new -key codesign.key \
  -subj "/CN=Acme Corp/O=Acme Corp/C=IN" \
  -addext "keyUsage=critical,digitalSignature" \
  -addext "extendedKeyUsage=codeSigning" \
  -out codesign.csr
```

| # | Validation | Check | Reason | Error Code |
|---|-----------|-------|--------|------------|
| CS-01 | **CN organization ya developer naam** | `Acme Corp`, `Ravi Sharma` ✓ — IP ✗ | Software ke saath naam dikhta hai user ko — "Publisher: Acme Corp" | PKI_POL_016 |
| CS-02 | **CN IP address nahi** | `192.168.1.1` → reject | Code signing cert mein IP meaningless aur suspicious | PKI_POL_016 |
| CS-03 | **O (Organization) MANDATORY** | `O=Acme Corp` hona chahiye | CA/Browser Forum requirement for code signing | PKI_POL_001 |
| CS-04 | **C (Country) MANDATORY** | `C=IN` hona chahiye | CAB Forum — country mandatory for code signing | PKI_POL_001 |
| CS-05 | **SAN typically absent** | SAN hone pe warning | Code signing certs mein SAN unusual hai — suspicious activity indicator | PKI_POL_011 (warn) |
| CS-06 | **KeyUsage: digitalSignature only** | Sirf signing, no keyEncipherment | Code sign karte hain, key exchange nahi | PKI_POL_011 (warn) |
| CS-07 | **EKU: codeSigning MANDATORY** | `id-kp-codeSigning` | OS loader yahi check karta hai runtime pe | PKI_POL_016 (warn) |
| CS-08 | **Key size RSA 3072+ recommended** | RSA 2048 accepted, 3072 recommended | Code signing certs longer validity hoti hai — stronger key future-proof | PKI_KEY_001 (warn) |
| CS-09 | **EV: serialNumber field required** | Company registration number | EV code signing mein legally registered company number mandatory | PKI_POL_001 |

---

## 5. DOCUMENT_SIGNING Certificate

**Use:** PDF sign karna — legal documents, contracts, Aadhaar-linked signing  
**Generates with:**
```bash
openssl req -new -key docsign.key \
  -subj "/CN=Ravi Sharma/O=Acme Corp/C=IN" \
  -addext "keyUsage=critical,digitalSignature,nonRepudiation" \
  -out docsign.csr
```

| # | Validation | Check | Reason | Error Code |
|---|-----------|-------|--------|------------|
| D-01 | **CN person ya organization naam** | `Ravi Sharma`, `Acme Corp Legal` | Document par naam dikhega — legal identity | PKI_POL_001 |
| D-02 | **O (Organization) recommended** | `O=Acme Corp` | Legal entity ke liye organization naam important | PKI_POL_011 (warn) |
| D-03 | **KeyUsage: digitalSignature MANDATORY** | Mandatory | PDF signature mechanism ke liye | PKI_POL_011 (warn) |
| D-04 | **KeyUsage: nonRepudiation MANDATORY** | `nonRepudiation` bit | Legal documents mein non-repudiation critical — baad mein "maine sign nahi kiya" nahi bol sakte | PKI_POL_011 (warn) |
| D-05 | **EKU: no serverAuth/clientAuth** | Ye bits absent hone chahiye | Document cert ko TLS mein use nahi hona chahiye — scope limited rakhna | PKI_POL_011 (warn) |
| D-06 | **Validity max 3 years** | 3 saal se zyada validity → reject | India DSC (Digital Signature Certificate) regulations — max 3 years | PKI_VAL_001 |
| D-07 | **SAN absent ya optional** | SAN allowed nahi typically | Document signing cert ka scope servers/email nahi hona chahiye | — (warn if present) |

---

## Part C — Quick Reference Matrix

```
Validation                  TLS_SERVER  TLS_CLIENT  SMIME   CODE_SIGN  DOC_SIGN
────────────────────────── ──────────  ──────────  ──────  ─────────  ────────
V-01  Payload present          MUST        MUST      MUST     MUST       MUST
V-02  Size limit (8KB)         MUST        MUST      MUST     MUST       MUST
V-03  PEM format               MUST        MUST      MUST     MUST       MUST
V-04  ASN.1 parse              MUST        MUST      MUST     MUST       MUST
V-05  Self-signature verify    MUST        MUST      MUST     MUST       MUST
V-06  No SHA1/MD5              MUST        MUST      MUST     MUST       MUST
V-07  Key size minimum         MUST        MUST      MUST     MUST       MUST
V-08  CN present               MUST        MUST      MUST     MUST       MUST
V-09  No cA=TRUE               MUST        MUST      MUST     MUST       MUST
V-10  No keyCertSign           MUST        MUST      MUST     MUST       MUST
V-11  No anyEKU                MUST        MUST      MUST     MUST       MUST
V-12  Duplicate hash check     MUST        MUST      MUST     MUST       MUST
V-13  clientTxnId unique       MUST        MUST      MUST     MUST       MUST
────────────────────────── ──────────  ──────────  ──────  ─────────  ────────
S-01  CN is valid FQDN         MUST        SKIP      SKIP     SKIP       SKIP
S-02  CN no spaces             MUST        SKIP      SKIP     SKIP       SKIP
S-03  CN not IP address        MUST        SKIP      SKIP     MUST       SKIP
S-04  CN min 2 labels          MUST        SKIP      SKIP     SKIP       SKIP
S-05  SAN dNSName present      MUST        SKIP      SKIP     SKIP       SKIP
S-06  SAN dNSName valid FQDN   MUST        SKIP      SKIP     SKIP       SKIP
S-07  No multi-level wildcard  MUST        SKIP      SKIP     SKIP       SKIP
────────────────────────── ──────────  ──────────  ──────  ─────────  ────────
M-02  SAN email present        SKIP        SKIP      MUST     SKIP       SKIP
M-03  Email format valid       SKIP        OPT       MUST     SKIP       SKIP
────────────────────────── ──────────  ──────────  ──────  ─────────  ────────
CS-03 O= present               SKIP        SKIP      SKIP     MUST       OPT
CS-04 C= present               SKIP        SKIP      SKIP     MUST       SKIP
CS-05 SAN absent               SKIP        SKIP      SKIP     WARN       WARN
────────────────────────── ──────────  ──────────  ──────  ─────────  ────────
KU    digitalSignature         MUST        MUST      MUST     MUST       MUST
KU    nonRepudiation           SKIP        SKIP      OPT      SKIP       MUST
KU    keyEncipherment          OPT(RSA)    SKIP      OPT      SKIP       SKIP
KU    keyAgreement             OPT(EC)     SKIP      OPT      SKIP       SKIP
────────────────────────── ──────────  ──────────  ──────  ─────────  ────────
EKU   serverAuth               MUST        SKIP      SKIP     SKIP       SKIP
EKU   clientAuth               SKIP        MUST      SKIP     SKIP       SKIP
EKU   emailProtection          SKIP        SKIP      MUST     SKIP       SKIP
EKU   codeSigning              SKIP        SKIP      SKIP     MUST       SKIP
────────────────────────── ──────────  ──────────  ──────  ─────────  ────────
D-06  Validity max 3 years     SKIP        SKIP      SKIP     SKIP       MUST

Legend: MUST=required  OPT=optional  SKIP=not applicable  WARN=warning only
```

---

## Part D — Error Code Reference

| Code | Severity | HTTP | Description |
|------|----------|------|-------------|
| PKI_VAL_001 | ERROR | 400 | Required field missing or blank |
| PKI_VAL_002 | ERROR | 400 | CSR parse failure — malformed PEM, wrong header, bad ASN.1 |
| PKI_VAL_003 | ERROR | 400 | CSR payload exceeds 8 KB size limit |
| PKI_CRYPTO_001 | ERROR | 422 | Self-signature invalid — key mismatch or CSR tampered |
| PKI_CRYPTO_002 | ERROR | 422 | Weak/forbidden algorithm (SHA1, MD5) |
| PKI_KEY_001 | ERROR | 422 | Key size below minimum (RSA<2048, EC<P-256) |
| PKI_KEY_002 | WARN | — | Key type unusual — size undetermined |
| PKI_POL_001 | ERROR | 422 | CN missing from Subject DN |
| PKI_POL_004 | ERROR | 422 | TLS_SERVER: no SAN or no dNSName in SAN |
| PKI_POL_005 | ERROR | 422 | dNSName in SAN is not a valid FQDN |
| PKI_POL_006 | ERROR | 422 | S/MIME: no rfc822Name (email) in SAN |
| PKI_POL_007 | WARN | — | No X.509v3 extensions in CSR |
| PKI_POL_008 | ERROR | 422 | BasicConstraints cA=TRUE found |
| PKI_POL_009 | ERROR | 422 | KeyUsage has keyCertSign (CA-only bit) |
| PKI_POL_010 | ERROR | 422 | KeyUsage has cRLSign (CA-only bit) |
| PKI_POL_011 | WARN | — | Recommended KeyUsage or EKU bit missing |
| PKI_POL_012 | ERROR | 422 | EKU contains anyExtendedKeyUsage |
| PKI_POL_013 | ERROR | 422 | TLS_SERVER CN contains spaces |
| PKI_POL_014 | ERROR | 422 | TLS_SERVER CN is not a valid FQDN |
| PKI_POL_015 | WARN | — | CN exceeds 128 characters |
| PKI_POL_016 | ERROR | 422 | CODE_SIGNING CN is an IP address |
| PKI_POL_020 | WARN | — | rfc822Name format invalid |
| PKI_APR_009 | ERROR | 409 | Duplicate CSR detected within 24 hour window |

---

*Related:*
- *Enterprise_RA_Validation_Guide.md* — developer reference with Java implementation
- *CSR_Validation_Scenario_And_Implementation.md* — scenario walkthroughs + source code
- *PKCS10_CSR_Structure_Guide.md* — complete PKCS#10 field reference
