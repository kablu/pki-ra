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

**Use:** Salman ka laptop / service-a → server ko prove karna "main authorized hoon" — mTLS, VPN, smartcard login  
**Generates with:**
```bash
openssl req -new -key client.key \
  -subj "/CN=service-a/O=Salman Technologies/C=IN" \
  -addext "subjectAltName=email:salman@salman.com" \
  -addext "keyUsage=critical,digitalSignature" \
  -addext "extendedKeyUsage=clientAuth" \
  -out client.csr
```

> **SAN mandatory nahi hai** client cert mein — server hostname verify nahi karta.  
> Server sirf yeh dekhta hai: CA ne sign kiya? EKU clientAuth hai? Cert expired nahi?

### RA Verification Layers — TLS_CLIENT

#### Layer 1: Identity Verification (Kaun hai yeh?)

| Type | RA Kya Verify Karega |
|------|---------------------|
| **Human User** | Employee ID HR database se match, email ownership proven (challenge), manager approval |
| **Service/App** | Service name system inventory mein registered, ITSM ticket authorized, requesting team verified |
| **IoT Device** | Device serial number registered, hardware ID / MAC address match, manufacturing record exist |

#### Layer 2: CSR Technical Validation

| # | Validation | Check | Reason | Error Code |
|---|-----------|-------|--------|------------|
| C-01 | **CN person/service name OK** | `Salman Khan`, `service-a` — identity clearly readable | Client cert mein requester ka naam hota hai — server authorization ke liye use hota hai | — (OK) |
| C-02 | **CN max 128 chars** | 128 se bada → warning | Kuch CA systems truncate kar dete hain | PKI_POL_015 (warn) |
| C-03 | **SAN rfc822Name optional** | `email:salman@salman.com` — present ho to format valid hona chahiye | Email SAN optional hai — server hostname verify nahi karta client cert mein | PKI_POL_020 (warn) |
| C-04 | **SAN otherName UPN optional** | `salman@salman.com` UPN format | AD mein smartcard login ke liye UPN SAN required hoti hai | — (optional) |
| C-05 | **KeyUsage: digitalSignature MANDATORY** | Authentication ke liye mandatory — `keyEncipherment` nahi chahiye | Client apna identity signature se prove karta hai TLS handshake mein | PKI_POL_011 |
| C-06 | **EKU: clientAuth MANDATORY** | `id-kp-clientAuth` (OID 1.3.6.1.5.5.7.3.2) | Server yahi EKU check karta hai mTLS mein — absent ho toh handshake fail | PKI_POL_014 |
| C-07 | **EKU: serverAuth absent hona chahiye** | `serverAuth` client cert mein → reject | Client cert ko server identity prove karne ke liye use nahi hona chahiye — scope violation | PKI_POL_014 |
| C-08 | **BasicConstraints: isCA=false MANDATORY** | Client cert CA nahi ban sakta | Certificate hierarchy protect karna — client cert se sub-CA nahi banna chahiye | PKI_POL_001 |
| C-09 | **CN hostname jaisa nahi hona chahiye** | `api.salman.com` as CN in client cert → warning | Server-style hostname client cert mein suspicious — cert type confusion attack | PKI_POL_015 (warn) |
| C-10 | **Key size adequate** | RSA ≥ 2048, EC ≥ 256 | Weak key = security failure | PKI_POL_010 |
| C-11 | **CSR self-signature valid (PoP)** | Proof of Possession verify karo | Requester ke paas private key hai — otherwise stolen public key se cert ban sakta hai | PKI_POL_002 |

#### Layer 3: Policy & Authorization Checks

| # | Check | Reason |
|---|-------|--------|
| C-12 | **Validity period ≤ 1 year** (policy) | Client certs short-lived rakhni chahiye — long-lived = revocation risk |
| C-13 | **Duplicate CN check** | Same CN ke liye already active cert hai? → warn/block |
| C-14 | **Blacklist/revocation check** | Yeh identity revoke list mein toh nahi? |
| C-15 | **Requester authorization** | Kya requester ko IS identity ke liye cert maangne ka haq hai? (e.g. DevOps can't request payment-gateway cert) |
| C-16 | **Rate limit** | Ek hi requester bahut zyada certs toh nahi maang raha? |

#### Layer 4: Server Cert vs Client Cert — RA Verification Difference

| Verification | Server Cert | Client Cert (Human) | Client Cert (Service) |
|-------------|-------------|--------------------|-----------------------|
| PoP (CSR Signature) | ✅ | ✅ | ✅ |
| Key Size | ✅ | ✅ | ✅ |
| EKU = serverAuth | ✅ Mandatory | ❌ Must be absent | ❌ Must be absent |
| EKU = clientAuth | ❌ | ✅ Mandatory | ✅ Mandatory |
| isCA = false | ✅ | ✅ | ✅ |
| SAN hostname | ✅ Mandatory | ❌ Optional | ❌ Optional |
| SAN email | ❌ | Optional | ❌ |
| Domain ownership | ✅ | ❌ | ❌ |
| Employee HR check | ❌ | ✅ | ❌ |
| Service inventory | ❌ | ❌ | ✅ |
| Manager/ITSM approval | ❌ | Depends on policy | ✅ |

---

## 3. S/MIME Certificate

**Use:** Salman Outlook se email sign + encrypt karta hai  
**Generates with:**
```bash
openssl req -new -key smime.key \
  -subj "/CN=Salman Khan/O=Acme Corp/C=IN" \
  -addext "subjectAltName=email:salman@salman.com" \
  -addext "keyUsage=critical,digitalSignature,nonRepudiation,keyEncipherment" \
  -addext "extendedKeyUsage=emailProtection" \
  -out smime.csr
```

| # | Validation | Check | Reason | Error Code |
|---|-----------|-------|--------|------------|
| M-01 | **CN person naam hona chahiye** | `Salman Khan` ✓ | Email cert mein person identify hota hai | PKI_POL_001 |
| M-02 | **SAN rfc822Name MANDATORY** | `email:salman@salman.com` hona hi chahiye | RFC 5322 — modern mail clients CN nahi, SAN email dekhte hain | PKI_POL_006 |
| M-03 | **Email format valid** | `salman@salman.com` ✓ — `salman@` ✗ — `salman` ✗ | Galat email = mail delivery fail | PKI_POL_020 |
| M-04 | **KeyUsage: digitalSignature** | Email signing ke liye | Signature se recipient verify karta hai email actually tune bheji | PKI_POL_011 (warn) |
| M-05 | **KeyUsage: keyEncipherment (RSA key)** | Email encryption ke liye RSA | Sender tere public key se email encrypt karta hai | PKI_POL_011 (warn) |
| M-06 | **KeyUsage: keyAgreement (EC key)** | Email encryption ke liye EC | EC ka ECDH-based encryption mechanism | PKI_POL_011 (warn) |
| M-07 | **EKU: emailProtection MANDATORY** | `id-kp-emailProtection` | Mail clients yahi check karte hain | PKI_POL_015 (warn) |
| M-08 | **Signing + Encryption alag keys recommended** | Ek hi cert dono ke liye → warning | Encryption key backup honi chahiye (key escrow). Signing key never backed up. | PKI_POL_011 (warn) |

---

## 4. CODE_SIGNING Certificate

**Use:** Salman ek `.exe` ya `.jar` sign karta hai taaki Windows/OS trust kare  
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
| CS-01 | **CN organization ya developer naam** | `Acme Corp`, `Salman Khan` ✓ — IP ✗ | Software ke saath naam dikhta hai user ko — "Publisher: Acme Corp" | PKI_POL_016 |
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
  -subj "/CN=Salman Khan/O=Acme Corp/C=IN" \
  -addext "keyUsage=critical,digitalSignature,nonRepudiation" \
  -out docsign.csr
```

| # | Validation | Check | Reason | Error Code |
|---|-----------|-------|--------|------------|
| D-01 | **CN person ya organization naam** | `Salman Khan`, `Acme Corp Legal` | Document par naam dikhega — legal identity | PKI_POL_001 |
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

---

## Part E — Enterprise RA Validations — Complete List with Purpose

### Category 1 — HTTP / Request Layer

| # | Validation | Purpose |
|---|-----------|---------|
| 1 | **Required fields check** (`pkcs10`, `clientTxnId`, `profile`) | Reject incomplete requests before any crypto work starts |
| 2 | **CSR payload size limit** (max 8 KB) | Prevent DoS — parse + signature verify is CPU-heavy |
| 3 | **PEM header/footer format** | Give user a clear error instead of cryptic ASN.1 exception |
| 4 | **Content-Type: application/json** | Ensure RA receives parseable input |
| 5 | **JWT token present** | No anonymous submissions — every request tied to an identity |

---

### Category 2 — Identity & Authentication

| # | Validation | Purpose |
|---|-----------|---------|
| 6 | **JWT signature verify** (RS256 via AD public key / JWKS) | Prove token came from AD — not forged |
| 7 | **JWT expiry check** (`exp` claim) | Reject stale tokens — session timeout enforcement |
| 8 | **JWT issuer + audience** (`iss`, `aud` claims) | Ensure token was meant for this RA — not reused from another system |
| 9 | **AD group → Role mapping** | Only authorized users can submit/approve/admin |
| 10 | **User exists in RA database** | Link request to local user record for workflow tracking |

---

### Category 3 — Separation of Duties (Workflow Identity)

| # | Validation | Purpose |
|---|-----------|---------|
| 11 | **Requester ≠ Maker** | Person who submitted CSR cannot review their own request |
| 12 | **Requester ≠ Checker** | Person who submitted cannot be final approver either |
| 13 | **Maker ≠ Checker** (DUAL mode) | Operator who reviewed cannot also accept — 4-eyes principle |
| 14 | **Only assigned operator can act** | Prevent unauthorized operators from acting on others' work |

---

### Category 4 — CSR Cryptographic Validation

| # | Validation | Purpose |
|---|-----------|---------|
| 15 | **ASN.1 / DER parse** | Confirm CSR is structurally valid PKCS#10 before any field inspection |
| 16 | **Proof of Possession** (self-signature verify) | **Most critical** — proves submitter holds the private key. If invalid → key mismatch or tampered CSR |
| 17 | **Signature algorithm — no SHA1/MD5** | SHA1 broken since 2017; browsers/OS reject certs with weak sig algo |
| 18 | **RSA key ≥ 2048 bits** | RSA-1024 factored by commodity hardware; NIST deprecated 2010 |
| 19 | **EC key ≥ P-256** | Curves below P-256 have known weaknesses |
| 20 | **Ed25519 / Ed448 always accepted** | Modern, safe — no size check needed |

---

### Category 5 — CSR Policy / Content Validation

| # | Validation | Purpose |
|---|-----------|---------|
| 21 | **CN present in Subject DN** | Certificate holder identity mandatory in every cert type |
| 22 | **CN is valid FQDN** (TLS_SERVER only) | Browsers match CN/SAN to hostname — person name causes TLS failure |
| 23 | **CN has no spaces** (TLS_SERVER only) | Space = person name used instead of server hostname |
| 24 | **CN is not an IP address** (TLS_SERVER, CODE_SIGNING) | IPs go in SAN iPAddress field, not CN |
| 25 | **SAN dNSName present** (TLS_SERVER only) | RFC 2818 + CA/Browser Forum — all browsers ignore CN since 2017, only SAN used |
| 26 | **SAN dNSName is valid FQDN** | Invalid hostname in SAN = TLS handshake fail at runtime |
| 27 | **No multi-level wildcard** (`*.*.acme.com` rejected) | RFC 5280 — only single-level wildcard allowed |
| 28 | **SAN rfc822Name present** (SMIME only) | Modern mail clients use SAN email, not CN |
| 29 | **Organization (O=) present** (CODE_SIGNING) | CA/Browser Forum requirement — legal entity name mandatory |
| 30 | **Country (C=) present** (CODE_SIGNING) | CA/Browser Forum requirement |
| 31 | **BasicConstraints cA=TRUE forbidden** | End-user cannot obtain a CA certificate — would let them sign certs themselves |
| 32 | **keyCertSign bit forbidden** | CA-only KeyUsage bit — end-entity must never have this |
| 33 | **cRLSign bit forbidden** | CA-only KeyUsage bit — end-entity must never have this |
| 34 | **anyExtendedKeyUsage forbidden** | Bypasses all EKU restrictions — effectively unlimited cert scope |
| 35 | **Profile ↔ EKU cross-check** | TLS_SERVER must have serverAuth, SMIME must have emailProtection, etc. |
| 36 | **Validity days within limits** (DOCUMENT_SIGNING max 3 years) | DSC regulations — India max 3 years |

---

### Category 6 — Duplicate & Idempotency

| # | Validation | Purpose |
|---|-----------|---------|
| 37 | **Duplicate CSR hash** (SHA-256 of DER, 24hr window) | Prevent double-submission from retry/double-click; block replay attacks |
| 38 | **clientTxnId globally unique** | Idempotency — same client transaction never processed twice |

---

### Category 7 — Workflow State Machine

| # | Validation | Purpose |
|---|-----------|---------|
| 39 | **Status transition valid** | e.g. cannot approve a CLOSED request — strict state machine enforced |
| 40 | **Approval mode check** (SINGLE vs DUAL) | SINGLE mode → direct approve/reject. DUAL mode → review first, then checker accepts |
| 41 | **approvalModeAtPickup locked** | Config changes mid-flight don't affect in-progress requests |
| 42 | **Remarks required + minimum length** | Audit trail — operator must justify every decision |
| 43 | **Return reason required** | When returning request, reason must be given to requestor |
| 44 | **Request not already picked up** | Prevent two operators grabbing same request simultaneously |

---

### Category 8 — CA Submission

| # | Validation | Purpose |
|---|-----------|---------|
| 45 | **CSR PEM still present on approved request** | Sanity check before sending to CA |
| 46 | **Status is APPROVED before CA send** | Never send unapproved requests to CA |
| 47 | **Not already sent to CA** | Idempotency — prevent duplicate CA submissions |
| 48 | **Validity days within CA-allowed range** | Cap at CA maximum if user requested too many days |
| 49 | **CA callback: requestId exists** | Reject callbacks for unknown requests |
| 50 | **CA callback: caTransactionId matches** | Prevent spoofed callbacks from triggering certificate issuance |
| 51 | **Issued cert subject matches CSR subject** | CA issued cert for the right person/server |
| 52 | **Issued cert not already expired** | CA returned a bad certificate |

---

### Summary by Category

```
Category 1 — HTTP (5)          Garbage in, garbage out se bachao
Category 2 — Identity (5)      Har request ek real AD user se aani chahiye
Category 3 — Duties (4)        Koi ek insaan poora process akele nahi kar sakta
Category 4 — Crypto (6)        CSR mathematically sound hai — tampered nahi
Category 5 — Policy (16)       CSR content certificate ke purpose se match karta hai
Category 6 — Duplicate (2)     Same request baar baar process nahi honi chahiye
Category 7 — Workflow (6)      Approval process ke rules strictly follow hon
Category 8 — CA Submit (8)     CA ko sirf valid, approved, correct payload jaaye
─────────────────────────────────────────────────────────────
Total: 52 validations across 8 categories
```

---

## Part F — Certificate Type Matrix (RFC Style)

```text
                 Certificate Types: Purpose, Verification & Validation

   +------------------+-------------------------+----------------------------------+--------------------------------+
   | Certificate Type | Purpose                 | Verification & Validation (RA)   | Example                        |
   +------------------+-------------------------+----------------------------------+--------------------------------+
   | TLS_SERVER       | Server identity for     | o  PKCS#10 parse + Proof of      | CN=api.acme.com                |
   |                  | HTTPS/TLS; browser to   |    Possession (self-sig verify)  | SAN: dNSName=api.acme.com,     |
   |                  | server encryption       | o  Domain Control Validation     |      dNSName=www.acme.com      |
   |                  |                         |    (DNS TXT / HTTP token /       | EKU: serverAuth                |
   |                  |                         |    email to admin@domain)        | KU: digitalSignature,          |
   |                  |                         | o  CN/SAN FQDN syntax; wildcard  |     keyEncipherment            |
   |                  |                         |    policy; no bare IP (policy)   |                                |
   |                  |                         | o  EKU=serverAuth; RSA >= 2048   |                                |
   |                  |                         | o  CAA record check              |                                |
   +------------------+-------------------------+----------------------------------+--------------------------------+
   | TLS_CLIENT       | Client/device identity  | o  PoP + key/algo checks         | CN=salman.device-042           |
   |                  | for mutual TLS (mTLS);  | o  Identity binding: CN maps to  | O=Acme Corp                    |
   |                  | API and service auth    |    a real user/device in IAM/    | EKU: clientAuth                |
   |                  |                         |    HR/CMDB registry              | KU: digitalSignature           |
   |                  |                         | o  Requester owns that identity  |                                |
   |                  |                         |    (no impersonation)            |                                |
   |                  |                         | o  EKU=clientAuth only;          |                                |
   |                  |                         |    serverAuth forbidden          |                                |
   +------------------+-------------------------+----------------------------------+--------------------------------+
   | S/MIME           | Email signing and       | o  PoP + key/algo checks         | CN=Salman Khan                 |
   |                  | encryption; sender      | o  Mailbox Control Validation:   | SAN: rfc822Name=               |
   |                  | authenticity            |    challenge mail to the exact   |      salman@acme.com           |
   |                  |                         |    address in SAN rfc822Name     | EKU: emailProtection           |
   |                  |                         | o  SAN email syntax valid;       | KU: digitalSignature,          |
   |                  |                         |    domain belongs to org         |     keyEncipherment            |
   |                  |                         | o  EKU=emailProtection           |                                |
   +------------------+-------------------------+----------------------------------+--------------------------------+
   | CODE_SIGNING     | Sign .exe/.jar/.msi so  | o  PoP + RSA >= 3072 (CSBR)      | CN=Acme Corp                   |
   |                  | OS trusts publisher;    | o  Legal identity vetting: org   | O=Acme Corp, C=IN              |
   |                  | shows "Publisher:       |    in govt registry (MCA/ROC/    | serialNumber=U72900MH2015      |
   |                  | Acme Corp"              |    DUNS); verified callback      |   (EV only)                    |
   |                  |                         | o  Requester authorized by org   | EKU: codeSigning               |
   |                  |                         | o  Malware/abuse + sanctions     | KU: digitalSignature           |
   |                  |                         |    screening                     | No SAN                         |
   |                  |                         | o  Key in FIPS 140-2 L2 HW       |                                |
   |                  |                         |    (attestation, CSBR 2023)      |                                |
   |                  |                         | o  O and C mandatory; no SAN;    |                                |
   |                  |                         |    manual RA approval always     |                                |
   +------------------+-------------------------+----------------------------------+--------------------------------+
   | DOCUMENT_SIGNING | Sign PDFs/contracts;    | o  PoP + key/algo checks         | CN=Salman Khan                 |
   |                  | legal non-repudiation   | o  Personal identity proofing:   | O=Acme Corp, C=IN              |
   |                  | (DSC in India)          |    govt photo ID, video/         | KU: digitalSignature,          |
   |                  |                         |    in-person KYC (India DSC)     |     nonRepudiation             |
   |                  |                         | o  KU must include               | Validity: <= 3 years           |
   |                  |                         |    nonRepudiation                |                                |
   |                  |                         | o  No serverAuth/clientAuth EKU  |                                |
   |                  |                         | o  Validity <= 3 years (India    |                                |
   |                  |                         |    DSC regulation)               |                                |
   +------------------+-------------------------+----------------------------------+--------------------------------+

                      Table 1: RA Verification Matrix by Certificate Type
```

**Pattern:** Upar se neeche jaate hue verification **automated technical checks** (TLS server — domain control fully automated ho sakta hai) se **human identity vetting** (code signing / document signing — legal registries, callbacks, KYC) ki taraf shift hota hai — isliye last two types mein manual RA officer approval hamesha mandatory hai.

---

## Part G — Code Signing CSR: Complete RA Verification List

Real enterprise RA + CA/Browser Forum Code Signing Baseline Requirements (CSBR) ke hisaab se, phase-wise complete list. Phase 1–3 is doc mein pehle se covered hain (V-01..V-13, CS-01..CS-09); Phase 4–5 code signing ke special requirements hain.

### Phase 1 — Request/Transport Layer

1. **Authentication** — request bhejne wala client authenticated hai (mTLS / API key / session)
2. **Authorization** — is user ko CODE_SIGNING type request karne ka right hai ya nahi (RBAC)
3. **Payload present + size limit** — CSR empty nahi, aur size cap (e.g. 64KB) ke andar
4. **clientTxnId unique** — idempotency/replay protection

### Phase 2 — CSR Technical (Cryptographic) Validation

5. **PEM format valid** — `-----BEGIN CERTIFICATE REQUEST-----` structure sahi
6. **ASN.1/DER parse** — PKCS#10 structure valid
7. **Proof of Possession** — CSR ki self-signature verify karo; prove karta hai ki client ke paas private key hai
8. **Signature algorithm** — SHA-256+ only; MD5/SHA-1 reject
9. **Key size** — RSA minimum **3072-bit** (CSBR mandate for code signing — TLS ke 2048 se strict), ya ECDSA P-256/P-384
10. **Weak/compromised key check** — Debian weak keys, ROCA-vulnerable keys, known-compromised key blocklist ke against public key match
11. **Duplicate CSR / duplicate public key** — same key pe pehle koi cert issued/revoked to nahi

### Phase 3 — Subject DN & Extension Policy

12. **CN = organization ya developer naam** — yahi naam user ko "Publisher: Acme Corp" ke roop mein dikhega; IP/FQDN reject
13. **O (Organization) mandatory** — CABF requirement
14. **C (Country) mandatory** — aur O ke registered country se match kare
15. **SAN absent** — code signing mein SAN suspicious hai (warn/reject)
16. **KeyUsage = digitalSignature only** — keyEncipherment nahi
17. **EKU = codeSigning (`1.3.6.1.5.5.7.3.3`)** — serverAuth/clientAuth forbidden
18. **BasicConstraints cA=TRUE forbidden** + `anyExtendedKeyUsage` forbidden
19. **EV code signing**: `serialNumber` field mein company registration number mandatory

### Phase 4 — Identity Vetting (RA ka core kaam — yahi TLS se sabse bada difference hai)

20. **Legal existence verification** — organization government registry (MCA/ROC in India, QIIS, DUNS) mein registered hai
21. **Physical address verification** — registered office address confirm
22. **Requester authority verification** — jo bhej raha hai (e.g. Salman), woh organization ki taraf se authorized hai — authorization letter ya verified contact se confirmation
23. **Verified callback** — organization ke *independently verified* phone number pe call karke request confirm (applicant ke diye number pe nahi!)
24. **Individual developer case** — government photo ID + face-to-face ya remote video verification
25. **EV extra**: operational existence (company 3+ saal, ya bank account proof), signed subscriber agreement

### Phase 5 — Risk & Reputation Screening (code signing specific)

26. **Malware/abuse history check** — applicant ka naam CA ke internal denied list + industry malware databases mein to nahi (kyunki code signing cert ka misuse = signed malware)
27. **Sanctions/denied party screening** — OFAC/embargo lists
28. **High-risk applicant flag** — pehle revoked-for-abuse cert, typosquatting company names (e.g. "Microsofft Corp") → manual review
29. **Private key protection attestation** — **June 2023 se CSBR mandatory**: private key FIPS 140-2 Level 2 / Common Criteria EAL4+ hardware (HSM/USB token) mein generate hui hai — key attestation verify karo ya subscriber attestation lo. Software-only key ab allowed nahi.

### Phase 6 — Workflow & Approval

30. **Separation of duties** — jo request laya wahi approve nahi kar sakta (maker-checker)
31. **RA officer manual review** — code signing kabhi bhi fully auto-approve nahi hota (TLS DV jaisa nahi)
32. **Validity period check** — max 39 months (CSBR)
33. **Audit logging** — har verification step ka evidence record (WebTrust audit ke liye)
34. **CA submission** — sab pass hone ke baad hi CSR CA ko forward

**Ek line mein farq:** TLS cert mein RA *domain control* verify karta hai, lekin code signing mein RA *legal identity + reputation + key hardware protection* verify karta hai — kyunki galat haath mein gaya code signing cert directly signed malware banata hai.
