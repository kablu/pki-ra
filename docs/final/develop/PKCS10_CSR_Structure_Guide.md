# PKCS#10 CSR — Complete Structure Guide

**Document ID :** PKI-RA-CSR-001  
**Version      :** 1.0  
**Date         :** 2026-07-03  
**Author       :** Kablu Mandal — Magellan  
**Project      :** pki-ra (Enterprise Registration Authority)  
**Reference    :** RFC 2986 (PKCS#10), RFC 5280 (X.509), RFC 5958, RFC 8017  

---

## Table of Contents

1. [What is a CSR?](#1-what-is-a-csr)
2. [Complete ASN.1 Structure](#2-complete-asn1-structure)
3. [Field: version](#3-field-version)
4. [Field: subject — Subject Distinguished Name](#4-field-subject--subject-distinguished-name)
5. [Field: subjectPublicKeyInfo](#5-field-subjectpublickeyinfo)
6. [Field: attributes — extensionRequest](#6-field-attributes--extensionrequest)
7. [Extension: Subject Alternative Name (SAN)](#7-extension-subject-alternative-name-san)
8. [Extension: Key Usage](#8-extension-key-usage)
9. [Extension: Extended Key Usage (EKU)](#9-extension-extended-key-usage-eku)
10. [Extension: Basic Constraints](#10-extension-basic-constraints)
11. [Extension: Other Extensions](#11-extension-other-extensions)
12. [Attribute: challengePassword](#12-attribute-challengepassword)
13. [Field: signatureAlgorithm](#13-field-signaturealgorithm)
14. [Field: signature](#14-field-signature)
15. [PEM Format and Encoding](#15-pem-format-and-encoding)
16. [Case Guide — Which Fields Are Present](#16-case-guide--which-fields-are-present)
17. [How Client Generates a CSR](#17-how-client-generates-a-csr)
18. [Master Presence/Absence Table](#18-master-presenceabsence-table)

---

## 1. What is a CSR?

A **Certificate Signing Request (CSR)** is a message sent by an entity
(person, server, or device) to a Certificate Authority (CA) or Registration
Authority (RA) asking for a digital certificate.

The CSR contains:
- **Who** wants the certificate (Subject DN)
- **What key** they will use (public key)
- **What the certificate should contain** (requested extensions)
- **Proof** that they own the private key (self-signature)

The CA/RA verifies the CSR, validates the request against policy, and if
approved, issues a signed X.509 certificate.

**PKCS#10** (RFC 2986) is the most widely used CSR format. It is also called
a **Certificate Request** or simply a **CSR**.

```
Client                         RA / CA
  |                               |
  |  1. Generate key pair         |
  |     (private + public key)    |
  |                               |
  |  2. Build CSR                 |
  |     (subject DN + public key  |
  |      + extensions)            |
  |                               |
  |  3. Self-sign CSR             |
  |     (with private key)        |
  |                               |
  |------- POST CSR ------------->|
  |                               |
  |                               |  4. Verify self-signature
  |                               |  5. Validate policy
  |                               |  6. Operator approves
  |                               |  7. CA signs certificate
  |                               |
  |<------ X.509 Certificate -----|
  |                               |
  |  8. Install certificate       |
```

---

## 2. Complete ASN.1 Structure

RFC 2986 defines the exact ASN.1 structure of a PKCS#10 CSR.

```asn1
-- Top-level structure
CertificationRequest ::= SEQUENCE {
    certificationRequestInfo   CertificationRequestInfo,
    signatureAlgorithm         AlgorithmIdentifier { SIGNATURE-ALGORITHM, ... },
    signature                  BIT STRING
}

-- The actual data being signed
CertificationRequestInfo ::= SEQUENCE {
    version       INTEGER { v1(0) },
    subject       Name,
    subjectPKInfo SubjectPublicKeyInfo {{ PKInfoAlgorithms }},
    attributes    [0] Attributes {{ CRIAttributes }} OPTIONAL
}

-- Subject Distinguished Name
Name ::= CHOICE {
    rdnSequence  RDNSequence
}
RDNSequence ::= SEQUENCE OF RelativeDistinguishedName
RelativeDistinguishedName ::= SET SIZE (1..MAX) OF AttributeTypeAndValue
AttributeTypeAndValue ::= SEQUENCE {
    type    AttributeType,   -- OID
    value   AttributeValue   -- string
}

-- Public key
SubjectPublicKeyInfo ::= SEQUENCE {
    algorithm        AlgorithmIdentifier,
    subjectPublicKey BIT STRING
}

-- Attributes container (optional)
Attributes { ATTRIBUTE:IOSet } ::= SET OF Attribute{{ IOSet }}
Attribute { ATTRIBUTE:IOSet } ::= SEQUENCE {
    attrType    ATTRIBUTE.&id({IOSet}),
    attrValues  SET SIZE(1..MAX) OF ATTRIBUTE.&Type({IOSet}{@attrType})
}
```

### Visual Map of the Full CSR

```
CertificationRequest  SEQUENCE
│
├── certificationRequestInfo  SEQUENCE        ← SIGNED PART (bytes used for signature)
│   │
│   ├── version  INTEGER = 0                  ← always 0 (v1)
│   │
│   ├── subject  SEQUENCE                     ← Subject Distinguished Name
│   │   ├── SET → OID(CN)   + UTF8String(api.acme.com)
│   │   ├── SET → OID(O)    + UTF8String(Acme Corp)
│   │   ├── SET → OID(OU)   + UTF8String(IT Security)
│   │   ├── SET → OID(C)    + PrintableString(IN)
│   │   ├── SET → OID(ST)   + UTF8String(Karnataka)
│   │   └── SET → OID(L)    + UTF8String(Bengaluru)
│   │
│   ├── subjectPublicKeyInfo  SEQUENCE         ← Public Key
│   │   ├── algorithm  SEQUENCE
│   │   │   ├── OID = 1.2.840.113549.1.1.1   ← rsaEncryption
│   │   │   └── NULL
│   │   └── subjectPublicKey  BIT STRING       ← actual public key bytes
│   │       └── RSAPublicKey  SEQUENCE
│   │           ├── modulus  INTEGER           ← n (large number, e.g. 4096-bit)
│   │           └── publicExponent  INTEGER    ← e (usually 65537)
│   │
│   └── attributes  [0] IMPLICIT SET           ← OPTIONAL
│       └── Attribute  SEQUENCE
│           ├── attrType  OID = 1.2.840.113549.1.9.14   ← extensionRequest
│           └── attrValues  SET
│               └── Extensions  SEQUENCE
│                   │
│                   ├── Extension (SAN)  SEQUENCE
│                   │   ├── OID = 2.5.29.17
│                   │   └── OCTET STRING (DER encoded)
│                   │       └── GeneralNames  SEQUENCE
│                   │           ├── [2] dNSName = api.acme.com
│                   │           └── [2] dNSName = api2.acme.com
│                   │
│                   ├── Extension (KeyUsage)  SEQUENCE
│                   │   ├── OID = 2.5.29.15
│                   │   ├── BOOLEAN = TRUE  (critical)
│                   │   └── OCTET STRING → BIT STRING
│                   │       └── bits: digitalSignature + keyEncipherment
│                   │
│                   └── Extension (EKU)  SEQUENCE
│                       ├── OID = 2.5.29.37
│                       └── OCTET STRING (DER encoded)
│                           └── ExtKeyUsageSyntax  SEQUENCE
│                               └── OID = 1.3.6.1.5.5.7.3.1  ← serverAuth
│
├── signatureAlgorithm  SEQUENCE               ← Algorithm used to sign
│   ├── OID = 1.2.840.113549.1.1.11           ← sha256WithRSAEncryption
│   └── NULL
│
└── signature  BIT STRING                      ← Signature over certificationRequestInfo
    └── (raw signature bytes)
```

---

## 3. Field: version

```asn1
version  INTEGER { v1(0) }
```

**Value:** Always `0` (integer zero). This is the only defined version in RFC 2986.

**How generated:** Client always sets this to `0`. No configuration needed.

**Why:** RFC 2986 defines only one version of PKCS#10. The field exists for
future extensibility, but no v2 has ever been standardized. If a CSR has
`version != 0`, it MUST be rejected — it indicates a malformed or
non-compliant tool.

**Presence:** Always present. Never absent. Never any other value.

**Hex example:**
```
02 01 00      ← INTEGER, length 1, value 0
```

**RA check:** Reject if `version != 0` with error `PKI_VAL_002`.

---

## 4. Field: subject — Subject Distinguished Name

The Subject DN (Distinguished Name) identifies **who or what** the certificate
is for. It is an X.500 Name — a sequence of RDNs (Relative Distinguished
Names), each being an OID + value pair.

### 4.1 All Possible DN Attributes

| Short Name | OID | Description | Max Length | String Type |
|------------|-----|-------------|------------|-------------|
| `CN` | 2.5.4.3 | Common Name — primary identifier | 64 | UTF8String |
| `O` | 2.5.4.10 | Organization name | 64 | UTF8String |
| `OU` | 2.5.4.11 | Organizational Unit | 64 | UTF8String |
| `C` | 2.5.4.6 | Country (ISO 3166-1 alpha-2) | 2 | PrintableString |
| `ST` | 2.5.4.8 | State or Province | 128 | UTF8String |
| `L` | 2.5.4.7 | Locality / City | 128 | UTF8String |
| `E` / `emailAddress` | 1.2.840.113549.1.9.1 | Email address (legacy, use SAN instead) | 255 | IA5String |
| `SN` (serialNumber) | 2.5.4.5 | Device or person serial number | 64 | PrintableString |
| `GN` (givenName) | 2.5.4.42 | First name | 16 | UTF8String |
| `surname` | 2.5.4.4 | Last name / family name | 40 | UTF8String |
| `title` | 2.5.4.12 | Job title | 64 | UTF8String |
| `DC` | 0.9.2342.19200300.100.1.25 | Domain Component | 63 | IA5String |
| `UID` | 0.9.2342.19200300.100.1.1 | User ID / username | 256 | UTF8String |
| `street` | 2.5.4.9 | Street address | 128 | UTF8String |
| `postalCode` | 2.5.4.17 | Postal / ZIP code | 40 | UTF8String |
| `businessCategory` | 2.5.4.15 | Business category (EV certs) | 128 | UTF8String |
| `jurisdictionC` | 1.3.6.1.4.1.311.60.2.1.3 | Jurisdiction country (EV) | 2 | PrintableString |
| `jurisdictionST` | 1.3.6.1.4.1.311.60.2.1.2 | Jurisdiction state (EV) | 128 | UTF8String |

### 4.2 CN — Common Name (Most Important)

The CN is the **primary human-readable identifier** in the certificate.
What goes in CN depends entirely on the certificate type:

| Certificate Type | CN Value | Example |
|-----------------|----------|---------|
| TLS Server | FQDN of the server | `api.acme.com` |
| TLS Client | Username or person's name | `john.doe` or `John Doe` |
| S/MIME (Email) | Person's full name | `John Doe` |
| Code Signing | Developer or organization name | `Acme Corp` |
| Sub-CA / Root CA | CA's descriptive name | `Acme Corp Intermediate CA 1` |
| Device cert | Device hostname or serial | `printer-floor2.acme.com` |

> **Important:** CN alone is NOT sufficient for TLS Server — modern browsers
> require the hostname in the **SAN dNSName** extension. CN is ignored for
> hostname verification by RFC 2818 (since 2011). However, CN is still
> required in the Subject DN.

**How client generates CN:**

```bash
# OpenSSL: CN entered in -subj parameter
openssl req -new -key server.key -subj "/CN=api.acme.com/O=Acme Corp/C=IN"

# Or in interactive mode, OpenSSL asks:
Common Name (e.g. server FQDN or YOUR name) []: api.acme.com
```

```java
// Java: using Bouncy Castle
X500NameBuilder builder = new X500NameBuilder(BCStyle.INSTANCE);
builder.addRDN(BCStyle.CN, "api.acme.com");
builder.addRDN(BCStyle.O,  "Acme Corp");
builder.addRDN(BCStyle.C,  "IN");
X500Name subject = builder.build();
```

### 4.3 O — Organization

Legal name of the organization that owns the certificate.

**When present:** Almost always present for server and code signing certs.
**When absent:** May be absent for personal client certs if only CN + email.

**Validation by RA:** If policy enforces org name, must match approved list.

```
O=Acme Corp         ← OK
O=ACME CORPORATION  ← Different legal name — may fail if policy strict
O=Test Company      ← May be blocked by policy
```

### 4.4 OU — Organizational Unit

Sub-division within the organization. **Deprecated by many CA/Browser Forum
(CAB Forum) rules for public TLS certificates** — most public CAs no longer
include OU in issued certificates. Still used for internal PKI.

**When present:** Internal certs, S/MIME, legacy systems.  
**When absent:** Public TLS certs (CAB Forum BR deprecates OU).

### 4.5 C — Country

Two-letter ISO 3166-1 alpha-2 country code. PrintableString type (only
uppercase A-Z, digits, space, some punctuation — no UTF-8).

```
C=IN    ← India
C=US    ← United States
C=DE    ← Germany
C=GB    ← United Kingdom
```

**When present:** Almost all organizational certificates.  
**When absent:** May be absent for purely personal client certs.

### 4.6 ST and L — State and Locality

State/Province and City. Optional but common in organizational certs.

```
ST=Karnataka
L=Bengaluru
```

### 4.7 emailAddress in Subject (Legacy)

OID `1.2.840.113549.1.9.1` — email directly in the Subject DN.

**This is a legacy approach.** Modern practice is to put email in the
**SAN rfc822Name extension** instead.

**When present:**
- S/MIME certificates (legacy format)
- Some older enterprise PKI systems

**When absent:**
- TLS Server certificates (never)
- Modern S/MIME (email goes in SAN instead)

```
# Legacy S/MIME subject
CN=John Doe, emailAddress=john@acme.com, O=Acme Corp, C=IN

# Modern approach (email in SAN, not subject)
CN=John Doe, O=Acme Corp, C=IN
+ SAN: rfc822Name=john@acme.com
```

### 4.8 DC — Domain Component

Used in enterprise/LDAP-style PKI where the DN follows the domain hierarchy.

```
# LDAP-style DN for john@acme.com
CN=John Doe, DC=acme, DC=com

# Equivalent traditional DN
CN=John Doe, O=Acme Corp
```

**When present:** Enterprise Microsoft PKI (Active Directory Certificate
Services generates LDAP-style DNs). Rare in non-Microsoft environments.

### 4.9 Complete Subject DN Examples

**TLS Server Certificate:**
```
Subject: CN=api.acme.com, O=Acme Corp, OU=IT Security, C=IN, ST=Karnataka, L=Bengaluru
```

**TLS Client / Person Certificate:**
```
Subject: CN=John Doe, O=Acme Corp, OU=Engineering, C=IN
```

**S/MIME Certificate (modern):**
```
Subject: CN=John Doe, O=Acme Corp, C=IN
SAN: rfc822Name=john@acme.com
```

**S/MIME Certificate (legacy):**
```
Subject: CN=John Doe, emailAddress=john@acme.com, O=Acme Corp, C=IN
```

**Code Signing Certificate:**
```
Subject: CN=Acme Corp, O=Acme Corp, C=IN
```

**EV (Extended Validation) TLS Certificate:**
```
Subject: CN=acme.com,
         O=Acme Corporation Pvt Ltd,
         businessCategory=Private Organization,
         serialNumber=U72200KA2010PTC123456,
         jurisdictionC=IN,
         L=Bengaluru,
         ST=Karnataka,
         C=IN
```

**AD CS (Microsoft Active Directory) generated client cert:**
```
Subject: CN=john.doe@acme.com, DC=acme, DC=com
```

### 4.10 Empty Subject DN

An empty Subject DN (`Subject: `) is allowed **only if** the certificate
will contain a SAN extension. This is common for:
- TLS Server certs where all hostname info is in SAN
- Some modern client auth certs

If Subject is empty AND no SAN → reject (nothing to identify the entity).

### 4.11 ASN.1 / DER encoding of Subject DN

```
SEQUENCE {                           ← Name
  SET {                              ← RDN for CN
    SEQUENCE {
      OID 2.5.4.3                    ← commonName
      UTF8String 'api.acme.com'
    }
  }
  SET {                              ← RDN for O
    SEQUENCE {
      OID 2.5.4.10                   ← organizationName
      UTF8String 'Acme Corp'
    }
  }
  SET {                              ← RDN for C
    SEQUENCE {
      OID 2.5.4.6                    ← countryName
      PrintableString 'IN'
    }
  }
}
```

**Note on string types:** RFC 5280 requires that `C` use `PrintableString`.
All others SHOULD use `UTF8String`. Legacy tools may use `TeletexString` or
`BMPString` — these are acceptable but deprecated.

---

## 5. Field: subjectPublicKeyInfo

This field contains the **public key** that the client wants in their
certificate, along with the algorithm identifier.

```asn1
SubjectPublicKeyInfo ::= SEQUENCE {
    algorithm        AlgorithmIdentifier,
    subjectPublicKey BIT STRING
}

AlgorithmIdentifier ::= SEQUENCE {
    algorithm   OBJECT IDENTIFIER,
    parameters  ANY OPTIONAL
}
```

**Presence:** Always present. The entire purpose of a CSR is to get this
public key signed by the CA.

**How generated:** Client generates a **key pair** (private + public). The
private key is kept secret — never leaves the client. Only the public key
goes into the CSR.

### 5.1 RSA Keys

**Algorithm OID:** `1.2.840.113549.1.1.1` (rsaEncryption)  
**Parameters:** NULL

```asn1
-- RSA public key structure (inside the BIT STRING)
RSAPublicKey ::= SEQUENCE {
    modulus           INTEGER,   -- n: the large number (product of two primes)
    publicExponent    INTEGER    -- e: usually 65537 (0x010001)
}
```

**How client generates RSA key pair:**

```bash
# OpenSSL — generate 4096-bit RSA key
openssl genrsa -out server.key 4096

# What happens internally:
# 1. Generate two large random primes: p, q (each ~2048 bits for RSA-4096)
# 2. Compute n = p × q  (the modulus — 4096-bit number)
# 3. Compute φ(n) = (p-1)(q-1)
# 4. Choose e = 65537 (public exponent — coprime to φ(n))
# 5. Compute d = e⁻¹ mod φ(n)  (private exponent)
# 6. Private key = (n, e, d, p, q, dp, dq, qInv)  ← kept secret
# 7. Public key  = (n, e)  ← goes into CSR
```

```java
// Java: generate RSA-4096 key pair
KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
kpg.initialize(4096, new SecureRandom());
KeyPair keyPair = kpg.generateKeyPair();
// keyPair.getPrivate() → RSAPrivateCrtKey (keep secret)
// keyPair.getPublic()  → RSAPublicKey (goes into CSR)
```

**Key sizes and their security equivalence:**

| RSA Key Size | Security Level | Equivalent EC | Recommended Until |
|---|---|---|---|
| 1024 bit | 80 bit | — | **BROKEN — reject** |
| 2048 bit | 112 bit | P-224 | ~2030 (minimum acceptable) |
| 3072 bit | 128 bit | P-256 | ~2040 |
| 4096 bit | 140 bit | P-384 | Long-term |

**Example RSA public key in PEM (2048-bit, truncated):**
```
-----BEGIN PUBLIC KEY-----
MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA2a2rwplBQLF29amygykE
...
-----END PUBLIC KEY-----
```

**What the modulus looks like (RSA-2048):**
```
n = 26959946667150639794667015087019630673637144422540572481103610249215
    ... (617 digits) ...
e = 65537
```

### 5.2 EC (Elliptic Curve) Keys

**Algorithm OID:** `1.2.840.10045.2.1` (ecPublicKey)  
**Parameters:** Named curve OID

```asn1
-- EC algorithm identifier
SEQUENCE {
    OID 1.2.840.10045.2.1           ← ecPublicKey
    OID 1.2.840.10045.3.1.7         ← prime256v1 (P-256 / secp256r1)
}

-- EC public key (inside BIT STRING)
-- Uncompressed point format:
-- 04 || X-coordinate (32 bytes) || Y-coordinate (32 bytes)
-- Total: 65 bytes for P-256
```

**Named curve OIDs:**

| Curve Name | OID | Key Size | Security Level |
|---|---|---|---|
| P-256 (prime256v1, secp256r1) | 1.2.840.10045.3.1.7 | 256-bit | 128-bit |
| P-384 (secp384r1) | 1.3.132.0.34 | 384-bit | 192-bit |
| P-521 (secp521r1) | 1.3.132.0.35 | 521-bit | 256-bit |
| secp256k1 (Bitcoin curve) | 1.3.132.0.10 | 256-bit | Not for PKI |

**How client generates EC key pair:**

```bash
# OpenSSL — generate EC P-256 key pair
openssl ecparam -name prime256v1 -genkey -noout -out ec-key.pem

# What happens internally:
# 1. Choose named curve: P-256
# 2. Generate random integer d in [1, n-1]   ← private key (scalar)
# 3. Compute Q = d × G   ← public key (point on curve)
#    where G = generator point (fixed for the curve)
# 4. Private key = d  (32 bytes for P-256)
# 5. Public key  = Q = (x, y)  (64 bytes uncompressed, or 33 compressed)
```

```java
// Java: generate EC P-256 key pair
KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
kpg.initialize(new ECGenParameterSpec("prime256v1"), new SecureRandom());
KeyPair keyPair = kpg.generateKeyPair();
```

**EC vs RSA in CSR:**

| Aspect | RSA-2048 | EC P-256 |
|---|---|---|
| Key size in CSR | ~294 bytes | ~91 bytes |
| Signature size | 256 bytes | ~72 bytes (DER) |
| Security | 112-bit | 128-bit |
| Performance | Slower | Faster |
| Compatibility | Universal | Modern (TLS 1.2+) |

### 5.3 Ed25519 Keys

**Algorithm OID:** `1.3.101.112` (id-EdDSA, Ed25519 specifically)  
**Parameters:** None (absent — Ed25519 has no parameters)

```asn1
SEQUENCE {
    OID 1.3.101.112    ← Ed25519
    -- NO parameters field (absent entirely, not NULL)
}
```

**How client generates Ed25519 key pair:**

```bash
# OpenSSL 1.1.1+
openssl genpkey -algorithm ed25519 -out ed25519-key.pem

# Internally:
# 1. Generate 32 random bytes as seed
# 2. SHA-512 hash the seed: h = SHA-512(seed)
# 3. Lower 32 bytes → scalar a (clamped)
# 4. Upper 32 bytes → nonce generation key
# 5. Public key = a × B (Edwards curve point, compressed to 32 bytes)
```

**Key size comparison in CSR:**
- RSA-4096: public key = ~550 bytes
- EC P-256: public key = 65 bytes (uncompressed) or 33 (compressed)
- Ed25519: public key = **32 bytes** (always compressed)

### 5.4 Key Pair Generation — Security Requirements

```
MUST use a cryptographically secure random number generator (CSPRNG):
  Linux/Mac: /dev/urandom or getrandom() syscall
  Windows:   CryptGenRandom() or BCryptGenRandom()
  Java:      java.security.SecureRandom (uses OS CSPRNG)

MUST NOT use:
  java.util.Random (not cryptographic)
  Math.random()
  Predictable seeds (time, PID, etc.)

The private key MUST:
  Never be transmitted to anyone (not even the CA/RA)
  Be stored encrypted (PKCS#8 with AES-256-GCM passphrase, or HSM)
  Have access controls (file permissions 0600 or HSM ACL)
```

### 5.5 HSM (Hardware Security Module) Key Generation

For high-assurance keys (Code Signing, CA keys), the key pair is generated
**inside the HSM** and the private key never exists in software:

```bash
# PKCS#11 via OpenSSL (SoftHSM example, same API for real HSM)
openssl req -new \
    -engine pkcs11 \
    -keyform engine \
    -key "pkcs11:token=MyToken;object=ServerKey;type=private" \
    -subj "/CN=api.acme.com/O=Acme Corp/C=IN" \
    -out server.csr

# The private key never leaves the HSM
# CSR is signed by HSM internally
```

---

## 6. Field: attributes — extensionRequest

The `attributes` field in CertificationRequestInfo is **OPTIONAL** but almost
always present in modern CSRs. It carries the list of X.509v3 extensions the
client **requests** to be included in the issued certificate.

```asn1
-- attributes is tagged [0] IMPLICIT
attributes [0] Attributes {{ CRIAttributes }} OPTIONAL

-- The key attribute for extension requests:
extensionRequest ATTRIBUTE ::= {
    WITH SYNTAX  Extensions
    SINGLE VALUE TRUE
    ID           pkcs-9-at-extensionRequest   -- OID: 1.2.840.113549.1.9.14
}
```

**Structure in DER:**
```
[0] IMPLICIT SET (context tag 0)
  └── SEQUENCE (Attribute)
      ├── OID 1.2.840.113549.1.9.14   ← extensionRequest
      └── SET
          └── SEQUENCE (Extensions)
              ├── Extension 1 (SAN)
              ├── Extension 2 (Key Usage)
              └── Extension 3 (EKU)
```

### 6.1 When attributes is ABSENT

- **Minimal CSR:** Some legacy tools (old devices, embedded systems) generate
  a bare CSR with no attributes at all.
- **Effect:** No SANs, no Key Usage, no EKU in the CSR. The CA/RA applies
  defaults from the certificate template/profile.
- **Modern implication:** A TLS server cert without SAN in the CSR is
  problematic — modern browsers reject TLS certs without SAN. RA must either
  derive SAN from CN or reject the request.

### 6.2 When attributes is PRESENT (normal case)

The `extensionRequest` attribute contains a SEQUENCE of `Extension` objects,
each with:
```asn1
Extension ::= SEQUENCE {
    extnID     OBJECT IDENTIFIER,     ← which extension
    critical   BOOLEAN DEFAULT FALSE, ← must understand this? default false
    extnValue  OCTET STRING           ← DER-encoded extension value
}
```

**The `critical` flag:**
- `critical = TRUE` means: if a system does not understand this extension,
  it MUST reject the certificate.
- `critical = FALSE` (default): if not understood, it may be ignored.
- Client can request an extension as critical — CA may honour or override.

---

## 7. Extension: Subject Alternative Name (SAN)

**OID:** `2.5.29.17`  
**Criticality:** Usually non-critical in CSR (CA decides in final cert)

SAN is the **most important extension** for TLS Server certificates.
It lists all the hostnames, IPs, or emails the certificate is valid for.

```asn1
SubjectAltName ::= GeneralNames

GeneralNames ::= SEQUENCE SIZE (1..MAX) OF GeneralName

GeneralName ::= CHOICE {
    otherName                    [0] OtherName,
    rfc822Name                   [1] IA5String,      ← email
    dNSName                      [2] IA5String,      ← hostname
    x400Address                  [3] ORAddress,
    directoryName                [4] Name,
    ediPartyName                 [5] EDIPartyName,
    uniformResourceIdentifier    [6] IA5String,      ← URI
    iPAddress                    [7] OCTET STRING,   ← IP address
    registeredID                 [8] OBJECT IDENTIFIER
}
```

### 7.1 SAN Types Explained

**dNSName [2] — Hostname**

The most common SAN type for TLS Server certificates.

```
dNSName = api.acme.com          ← exact hostname match
dNSName = *.acme.com            ← wildcard (matches one level only)
dNSName = acme.com              ← apex domain

Rules:
  - Valid FQDN format required
  - Wildcard only allowed as leftmost label (*.acme.com OK, *.*.acme.com NOT OK)
  - No bare TLD (*.com not allowed)
  - No IP addresses (use iPAddress SAN instead)
  - No underscores (RFC violation, some internal systems use them)
  - Max 253 characters
  - Case insensitive
```

**rfc822Name [1] — Email Address**

Used in S/MIME certificates and sometimes client auth certificates.

```
rfc822Name = john@acme.com
rfc822Name = john.doe@acme.com

Rules:
  - Must be RFC 5321 compliant email address
  - local-part@domain format
  - Domain must be valid FQDN
```

**iPAddress [7] — IP Address**

Used when certificate must cover an IP address directly.

```
iPAddress = 192.168.1.100    ← stored as 4 raw bytes (IPv4)
iPAddress = 2001:db8::1      ← stored as 16 raw bytes (IPv6)

Rules:
  - Not a string — stored as raw bytes in OCTET STRING
  - IPv4: 4 bytes
  - IPv6: 16 bytes
  - Loopback (127.0.0.1, ::1) not allowed for public certs
  - Usually only for internal/private network certs
```

**uniformResourceIdentifier [6] — URI**

Used in some special certificates (OCSP Signing, code signing manifest).
Rarely in standard TLS/client certs.

```
URI = https://acme.com
URI = urn:ietf:params:oauth:jwk-thumbprint:sha-256:abc123
```

**otherName [0] — Flexible / Custom**

Used for:
- **UPN (User Principal Name)** in Microsoft/AD environments — used in
  smart card login and client auth certs
- **RFC 822 alternative**
- Custom enterprise identifiers

```asn1
OtherName ::= SEQUENCE {
    type-id    OBJECT IDENTIFIER,
    value      [0] EXPLICIT ANY DEFINED BY type-id
}

-- UPN example (used in AD client certs)
OtherName {
    type-id = 1.3.6.1.4.1.311.20.2.3   ← msUserPrincipalName OID
    value   = UTF8String "john@acme.com"
}
```

### 7.2 How Client Generates SAN

```bash
# OpenSSL: SAN via -addext (OpenSSL 1.1.1+)
openssl req -new -key server.key \
    -subj "/CN=api.acme.com/O=Acme Corp/C=IN" \
    -addext "subjectAltName=DNS:api.acme.com,DNS:api2.acme.com,DNS:*.acme.com" \
    -out server.csr

# OpenSSL: SAN via config file (older method)
# openssl.cnf:
[ req ]
distinguished_name = req_distinguished_name
req_extensions     = v3_req

[ req_distinguished_name ]
CN = api.acme.com
O  = Acme Corp
C  = IN

[ v3_req ]
subjectAltName = @alt_names

[ alt_names ]
DNS.1 = api.acme.com
DNS.2 = api2.acme.com
DNS.3 = *.acme.com
IP.1  = 192.168.1.100
```

```java
// Java (Bouncy Castle): add SAN to CSR
GeneralName[] sanNames = new GeneralName[] {
    new GeneralName(GeneralName.dNSName, "api.acme.com"),
    new GeneralName(GeneralName.dNSName, "api2.acme.com"),
    new GeneralName(GeneralName.iPAddress, "192.168.1.100")
};
GeneralNames subjectAltNames = new GeneralNames(sanNames);

ExtensionsGenerator extGen = new ExtensionsGenerator();
extGen.addExtension(Extension.subjectAlternativeName, false, subjectAltNames);

// Add to CSR attributes
PKCS10CertificationRequestBuilder builder = new PKCS10CertificationRequestBuilder(
    subject, publicKeyInfo);
builder.addAttribute(PKCSObjectIdentifiers.pkcs_9_at_extensionRequest,
    extGen.generate());
```

### 7.3 SAN Presence Rules

| Certificate Type | SAN Required? | SAN Types Used |
|-----------------|---------------|----------------|
| TLS Server | **MANDATORY** (RFC 2818, since 2011) | dNSName, iPAddress |
| TLS Client | Optional | rfc822Name, otherName (UPN) |
| S/MIME | **MANDATORY** (modern) | rfc822Name |
| Code Signing | Not used | — |
| CA Certificate | Not used | — |
| OCSP Signing | Not used | — |

### 7.4 SAN Examples for Each Certificate Type

```
TLS Server (api.acme.com with wildcard backup):
  SAN: dNSName=api.acme.com, dNSName=*.acme.com

TLS Server (multi-domain / SAN cert):
  SAN: dNSName=acme.com, dNSName=www.acme.com,
       dNSName=api.acme.com, dNSName=shop.acme.com

TLS Server with IP:
  SAN: dNSName=internal-server, iPAddress=10.0.1.50

S/MIME modern:
  SAN: rfc822Name=john@acme.com

TLS Client (AD environment with UPN):
  SAN: otherName (UPN OID) = john@acme.com

TLS Client (email-based):
  SAN: rfc822Name=john@acme.com
```

---

## 8. Extension: Key Usage

**OID:** `2.5.29.15`  
**Criticality:** MUST be critical if present (RFC 5280)

Key Usage defines what the **public key is allowed to be used for**. It is a
BIT STRING where each bit position represents one permitted use.

```asn1
KeyUsage ::= BIT STRING {
    digitalSignature   (0),   ← verify digital signatures
    nonRepudiation     (1),   ← non-repudiation / content commitment
    keyEncipherment    (2),   ← encrypt symmetric keys (RSA key exchange)
    dataEncipherment   (3),   ← directly encrypt data
    keyAgreement       (4),   ← ECDH key agreement
    keyCertSign        (5),   ← sign certificates (CA ONLY)
    cRLSign            (6),   ← sign CRLs (CA ONLY)
    encipherOnly       (7),   ← only encrypt in key agreement
    decipherOnly       (8)    ← only decrypt in key agreement
}
```

### 8.1 Each Bit Explained

| Bit | Name | Meaning | When Used |
|-----|------|---------|-----------|
| 0 | `digitalSignature` | Public key verifies a signature made by the private key | TLS Server (TLS handshake), TLS Client auth, Code Signing, S/MIME signing |
| 1 | `nonRepudiation` | Used for legal non-repudiation (signed docs cannot be denied) | S/MIME signing, Document signing |
| 2 | `keyEncipherment` | RSA: recipient's public key encrypts a symmetric key (TLS RSA key exchange) | TLS Server (RSA key exchange), S/MIME encryption |
| 3 | `dataEncipherment` | Directly encrypt data with the public key (rare) | Legacy email encryption |
| 4 | `keyAgreement` | ECDH key exchange — establish shared secret | ECDSA-based TLS Server (replaces keyEncipherment for EC keys) |
| 5 | `keyCertSign` | Sign X.509 certificates | **CA certificates only — reject in end-entity CSR** |
| 6 | `cRLSign` | Sign Certificate Revocation Lists | **CA certificates only — reject in end-entity CSR** |
| 7 | `encipherOnly` | Restrict keyAgreement to encryption only | Rare |
| 8 | `decipherOnly` | Restrict keyAgreement to decryption only | Rare |

### 8.2 Key Usage per Certificate Type

```
TLS Server (RSA key):
  KeyUsage: digitalSignature, keyEncipherment
  Reason: digitalSignature for TLS 1.3 / ECDHE handshake
          keyEncipherment for legacy RSA key exchange (TLS 1.2)

TLS Server (EC key):
  KeyUsage: digitalSignature
  Reason: EC uses keyAgreement for key exchange, not keyEncipherment
          Some tools also add keyAgreement for completeness

TLS Client Authentication:
  KeyUsage: digitalSignature
  Reason: Client proves identity by signing with private key

S/MIME Signing:
  KeyUsage: digitalSignature, nonRepudiation

S/MIME Encryption:
  KeyUsage: keyEncipherment (for RSA key)
  KeyUsage: keyAgreement (for EC key)

S/MIME (combined signing + encryption, separate keys preferred):
  KeyUsage: digitalSignature, nonRepudiation, keyEncipherment

Code Signing:
  KeyUsage: digitalSignature

CA / Sub-CA:
  KeyUsage: keyCertSign, cRLSign
```

### 8.3 How Client Sets Key Usage

```bash
# OpenSSL
openssl req -new -key server.key \
    -subj "/CN=api.acme.com/O=Acme Corp/C=IN" \
    -addext "keyUsage=critical,digitalSignature,keyEncipherment" \
    -addext "subjectAltName=DNS:api.acme.com" \
    -out server.csr
```

```java
// Java (Bouncy Castle)
int keyUsageBits = KeyUsage.digitalSignature | KeyUsage.keyEncipherment;
extGen.addExtension(Extension.keyUsage, true,  // critical=true
    new KeyUsage(keyUsageBits));
```

### 8.4 Key Usage Absent in CSR

Key Usage may be absent in many CSRs — especially those generated by
simple tools. When absent, the CA applies the Key Usage from the
certificate template/profile. This is completely normal and acceptable.

**RA policy:** If Key Usage is present in the CSR:
- `keyCertSign` or `cRLSign` → **REJECT** (end-entity cannot sign certs/CRLs)
- `dataEncipherment` → **WARN** (rarely needed, potential misuse)
- Conflicting combination (e.g., `keyCertSign` + `digitalSignature` on an
  end-entity) → **REJECT**

---

## 9. Extension: Extended Key Usage (EKU)

**OID:** `2.5.29.37`  
**Criticality:** Usually non-critical

EKU further restricts what the certificate can be used for. It complements
Key Usage with application-specific OIDs.

```asn1
ExtKeyUsageSyntax ::= SEQUENCE SIZE (1..MAX) OF KeyPurposeId
KeyPurposeId ::= OBJECT IDENTIFIER
```

### 9.1 Standard EKU OIDs

| EKU | OID | Purpose |
|-----|-----|---------|
| `serverAuth` | 1.3.6.1.5.5.7.3.1 | TLS/SSL Server Authentication |
| `clientAuth` | 1.3.6.1.5.5.7.3.2 | TLS/SSL Client Authentication |
| `codeSigning` | 1.3.6.1.5.5.7.3.3 | Code Signing |
| `emailProtection` | 1.3.6.1.5.5.7.3.4 | S/MIME Email Protection |
| `timeStamping` | 1.3.6.1.5.5.7.3.8 | Trusted Timestamping (RFC 3161) |
| `OCSPSigning` | 1.3.6.1.5.5.7.3.9 | OCSP Response Signing |
| `anyExtendedKeyUsage` | 2.5.29.37.0 | All purposes — **REJECT in RA** |
| `msSmartcardLogin` | 1.3.6.1.4.1.311.20.2.2 | Microsoft Smartcard Login |
| `msKDC` | 1.3.6.1.5.2.3.5 | Microsoft Kerberos KDC |
| `documentSigning` | 1.3.6.1.4.1.311.10.3.12 | Microsoft Document Signing |

### 9.2 EKU per Certificate Type

```
TLS Server:
  EKU: serverAuth (1.3.6.1.5.5.7.3.1)
  Optional: clientAuth (if same cert used for mutual TLS)

TLS Client Auth:
  EKU: clientAuth (1.3.6.1.5.5.7.3.2)

S/MIME:
  EKU: emailProtection (1.3.6.1.5.5.7.3.4)

Code Signing:
  EKU: codeSigning (1.3.6.1.5.5.7.3.3)

OCSP Responder:
  EKU: OCSPSigning (1.3.6.1.5.5.7.3.9)

Timestamp Authority:
  EKU: timeStamping (1.3.6.1.5.5.7.3.8)

Microsoft Smartcard (AD):
  EKU: clientAuth + msSmartcardLogin + msKDC
```

### 9.3 EKU Absent in CSR

Very common. Most clients do not set EKU in the CSR — the CA applies
it from the template. Acceptable.

### 9.4 Conflicting / Dangerous EKU Combinations

```
serverAuth + codeSigning → WARN (a server cert should not sign code)
anyExtendedKeyUsage      → REJECT (too permissive, accepts all uses)
```

---

## 10. Extension: Basic Constraints

**OID:** `2.5.29.19`  
**Criticality:** MUST be critical if `cA=TRUE`

```asn1
BasicConstraints ::= SEQUENCE {
    cA                BOOLEAN DEFAULT FALSE,
    pathLenConstraint INTEGER (0..MAX) OPTIONAL
}
```

| Field | Meaning |
|-------|---------|
| `cA = TRUE` | This is a CA certificate — can sign other certs |
| `cA = FALSE` | End-entity certificate (default if absent) |
| `pathLenConstraint = 0` | CA can only sign end-entity certs, not sub-CAs |
| `pathLenConstraint = 1` | CA can sign sub-CAs that can only sign end-entity |

### 10.1 In CSR — End-Entity vs CA Request

**End-entity CSR (TLS Server, Client, S/MIME):**
- Basic Constraints is usually **absent** in the CSR
- Or present with `cA = FALSE`
- CA always sets this to `cA = FALSE` in issued end-entity certs

**Sub-CA CSR:**
- `cA = TRUE` MUST be present
- `pathLenConstraint` based on PKI hierarchy depth
- **RA must verify the requester is authorized to request CA certs**
  (extremely restricted — usually only PKI admins)

### 10.2 RA Enforcement

```
CSR contains BasicConstraints with cA=TRUE:
  → REJECT unless requester has ROLE_PKI_ADMIN
  → Error: PKI_POL_015 "CA certificate issuance requires PKI Admin authorization"

CSR contains BasicConstraints with cA=FALSE:
  → Accept (matches end-entity intent)

CSR does not contain BasicConstraints:
  → Accept (CA will set cA=FALSE from template)
```

---

## 11. Extension: Other Extensions

### 11.1 Subject Key Identifier (SKI)

**OID:** `2.5.29.14`

A hash of the public key, used to identify the key across certificates.
Usually **not put in CSR** — CA generates it from the public key.

```
SKI = SHA-1(subjectPublicKey bit string)
Example: 14:2E:B3:17:B7:58:56:CB:AE:50:09:40:E6:1F:AF:9D:8B:14:C2:C6
```

### 11.2 Authority Key Identifier (AKI)

**OID:** `2.5.29.35`

Identifies the CA's key that signed this certificate. **Never in CSR** —
CA always sets this from its own key.

### 11.3 Certificate Policies

**OID:** `2.5.29.32`

Links the certificate to a published Certificate Policy (CP) document.
Rarely put in CSR — CA applies from template.

```
policyIdentifier = 2.23.140.1.2.1   ← CAB Forum DV policy OID
policyIdentifier = 2.23.140.1.2.2   ← CAB Forum OV policy OID
policyIdentifier = 2.23.140.1.1     ← CAB Forum EV policy OID
```

### 11.4 CRL Distribution Points (CDP)

**OID:** `2.5.29.31`

URL where the CRL (Certificate Revocation List) is published. **Never in
CSR** — CA sets this from its own infrastructure.

```
CRL Distribution Points:
  URI: http://crl.acme.com/acme-ca.crl
```

### 11.5 Authority Information Access (AIA)

**OID:** `1.3.6.1.5.5.7.1.1`

Contains URLs for:
- OCSP responder (online revocation check)
- CA Issuer cert download

**Never in CSR** — CA sets from its own infrastructure.

```
Authority Information Access:
  OCSP - URI: http://ocsp.acme.com
  CA Issuers - URI: http://certs.acme.com/acme-ca.crt
```

### 11.6 Microsoft-Specific Extensions

For AD CS (Active Directory Certificate Services) environments:

```
Certificate Template Name: OID 1.3.6.1.4.1.311.20.2
  Value: UTF8String "WebServer"
  Purpose: Tells AD CS which template to use

Certificate Template Info: OID 1.3.6.1.4.1.311.21.7
  Contains template OID + major/minor version numbers
```

These are sometimes added to CSRs by Microsoft tools (certreq.exe,
MMC certificate snap-in) to specify which AD CS template to use.

---

## 12. Attribute: challengePassword

**OID:** `1.2.840.113549.1.9.7`

```asn1
challengePassword ATTRIBUTE ::= {
    WITH SYNTAX  DirectoryString { ub-challengePassword }
    SINGLE VALUE TRUE
    ID           pkcs-9-at-challengePassword
}
```

**Purpose:** Used exclusively in **SCEP (Simple Certificate Enrollment
Protocol)** as a pre-shared secret to authenticate the enrollment request.
Not used in REST API or CMP flows.

**How it works in SCEP:**
```
1. Admin pre-registers a challenge password in the SCEP server
   (one-time-use token, like an OTP)

2. Client embeds this password in the CSR:
   challengePassword = "MySecretToken123"

3. SCEP server verifies the challengePassword against its database
   MATCH    → authentication succeeded, proceed with issuance
   NO MATCH → reject enrollment

4. Password is single-use — invalidated after first use
```

**When present:**
- SCEP protocol enrollment (network devices, printers, VPN clients)
- Some CA challenge-response systems

**When absent:**
- REST API enrollment (John's scenario — AD authentication used instead)
- EST protocol (uses HTTP Basic or mTLS)
- CMP protocol (uses signature or PBMAC)

**RA handling:**
```
If present in REST API CSR:
  → Ignore or WARN "challengePassword not applicable for REST enrollment"
  → Do NOT treat it as authentication — REST uses AD/JWT for that

If present in SCEP request:
  → Verify against protocol_clients table
  → MATCH: proceed to Layer 3+
  → NO MATCH: reject 401
```

**Security note:** The challengePassword in PKCS#10 is in **plaintext** inside
the CSR. SCEP wraps the CSR in encrypted PKCS#7 (EnvelopedData) so the
password is not visible on the wire, but it IS visible to the RA after
decryption.

---

## 13. Field: signatureAlgorithm

This field specifies which algorithm was used to **create the signature** in
the `signature` field.

```asn1
AlgorithmIdentifier ::= SEQUENCE {
    algorithm   OBJECT IDENTIFIER,
    parameters  ANY OPTIONAL
}
```

**Important:** This MUST match the algorithm used with the private key.
RSA key + ECDSA signature algorithm = invalid combination.

### 13.1 RSA Signature Algorithms

| OID | Name | Hash | Notes |
|-----|------|------|-------|
| 1.2.840.113549.1.1.5 | sha1WithRSAEncryption | SHA-1 | **REJECT — weak** |
| 1.2.840.113549.1.1.11 | sha256WithRSAEncryption | SHA-256 | Standard ✓ |
| 1.2.840.113549.1.1.12 | sha384WithRSAEncryption | SHA-384 | Strong ✓ |
| 1.2.840.113549.1.1.13 | sha512WithRSAEncryption | SHA-512 | Strongest ✓ |
| 1.2.840.113549.1.1.10 | RSASSA-PSS | configurable | Modern RSA ✓ |
| 1.2.840.113549.1.1.4 | md5WithRSAEncryption | MD5 | **REJECT — broken** |

### 13.2 ECDSA Signature Algorithms

| OID | Name | Curve | Notes |
|-----|------|-------|-------|
| 1.2.840.10045.4.3.1 | ecdsa-with-SHA224 | any EC | Acceptable |
| 1.2.840.10045.4.3.2 | ecdsa-with-SHA256 | P-256 recommended | Standard ✓ |
| 1.2.840.10045.4.3.3 | ecdsa-with-SHA384 | P-384 recommended | Strong ✓ |
| 1.2.840.10045.4.3.4 | ecdsa-with-SHA512 | P-521 recommended | Strongest ✓ |

### 13.3 EdDSA

| OID | Name | Notes |
|-----|------|-------|
| 1.3.101.112 | id-Ed25519 | No hash parameter — hash baked in ✓ |
| 1.3.101.113 | id-Ed448 | Larger, more secure ✓ |

**Note:** For Ed25519, parameters field is **absent entirely** (not NULL).
Ed25519 always uses SHA-512 internally — no choice of hash.

### 13.4 Algorithm Choice Best Practice

```
RSA key  → use sha256WithRSAEncryption or RSASSA-PSS
EC key   → use ecdsa-with-SHA256 (P-256), ecdsa-with-SHA384 (P-384)
Ed25519  → use id-Ed25519

NEVER:
  sha1WithRSAEncryption  → SHA-1 collision attacks since 2005
  md5WithRSAEncryption   → MD5 completely broken since 2004
  RSA key + ECDSA OID    → algorithm/key mismatch → reject
```

---

## 14. Field: signature

```asn1
signature  BIT STRING
```

This is the **digital signature** over the DER-encoded
`certificationRequestInfo`. It is the proof that the entity submitting
the CSR actually possesses the private key corresponding to the public key
in the CSR.

### 14.1 How signature is computed

```
Step 1: DER-encode the certificationRequestInfo SEQUENCE
        (version + subject + subjectPublicKeyInfo + attributes)

Step 2: Apply the hash algorithm from signatureAlgorithm
        hash = SHA-256(DER-encoded certificationRequestInfo)

Step 3: Sign the hash with the private key
        For RSA (PKCS#1 v1.5):
          signature = RSA_Sign(privateKey, hash)
          (pad with PKCS#1 v1.5, then apply RSA private key operation)

        For ECDSA:
          signature = ECDSA_Sign(privateKey, hash)
          Returns (r, s) pair, DER-encoded as SEQUENCE { INTEGER r, INTEGER s }

        For Ed25519:
          signature = Ed25519_Sign(privateKey, message)
          (no separate hash step — Ed25519 hashes internally)
          Returns 64-byte raw signature

Step 4: Encode as BIT STRING (prepend 0x00 unused-bits byte)
```

### 14.2 How RA Verifies signature (Proof of Possession)

```
Step 1: Extract certificationRequestInfo bytes (DER)
Step 2: Extract signatureAlgorithm OID
Step 3: Extract signature BIT STRING
Step 4: Extract subjectPublicKeyInfo (public key)

Step 5: Verify
  RSA: RSA_Verify(publicKey, hash(certificationRequestInfo), signature)
  ECDSA: ECDSA_Verify(publicKey, hash(certificationRequestInfo), (r,s))
  Ed25519: Ed25519_Verify(publicKey, certificationRequestInfo, signature)

VALID   → requester possesses private key (Proof of Possession confirmed)
INVALID → reject PKI_CRYPTO_001
```

```java
// Java: verify CSR self-signature (Bouncy Castle)
ContentVerifierProvider verifierProvider =
    new JcaContentVerifierProviderBuilder()
        .setProvider("BC")
        .build(csrRequest.getSubjectPublicKeyInfo());

boolean signatureValid = csrRequest.isSignatureValid(verifierProvider);
if (!signatureValid) {
    throw new CryptoValidationException("PKI_CRYPTO_001",
        "CSR self-signature verification failed -- " +
        "requester does not possess the private key");
}
```

### 14.3 Signature Size

| Algorithm | Signature Size |
|-----------|---------------|
| RSA-2048 PKCS#1 v1.5 | 256 bytes |
| RSA-4096 PKCS#1 v1.5 | 512 bytes |
| ECDSA P-256 | 70–72 bytes (DER, variable) |
| ECDSA P-384 | 102–104 bytes (DER, variable) |
| Ed25519 | 64 bytes (always fixed) |

---

## 15. PEM Format and Encoding

After creating the CertificationRequest ASN.1 structure, it must be encoded
for transmission.

### 15.1 DER Encoding

DER (Distinguished Encoding Rules) is the binary encoding of the ASN.1
structure. Every byte is deterministic — same input always produces same
DER output.

### 15.2 PEM Encoding

PEM (Privacy Enhanced Mail) is Base64 encoding of DER bytes, wrapped in
header/footer lines.

```
-----BEGIN CERTIFICATE REQUEST-----
MIICvDCCAaQCAQAweTELMAkGA1UEBhMCSU4xEjAQBgNVBAgMCUthcm5hdGFrYTES
MBAGA1UEBwwJQmVuZ2FsdXJ1MRIwEAYDVQQKDAlBY21lIENvcnAxFDASBgNVBAsM
C0lUIFNlY3VyaXR5MRIwEAYDVQQDDAlhcGkuYWNtZTCCASIwDQYJKoZIhvcNAQEB
BQADggEPADCCAQoCggEBALRiMLAHudeSA/xKFBGBMt2/OpBFHPFzIMVAknOcI6xM
-----END CERTIFICATE REQUEST-----
```

**Why PEM?**
- Text-safe (Base64 = only ASCII printable characters)
- Easy to copy-paste
- Clear visual identification (BEGIN/END headers)
- Can be embedded in JSON as a string value

### 15.3 Annotated Hex Dump of a Minimal CSR

```
30 82 01 ...     ← SEQUENCE (CertificationRequest)
│
├── 30 82 00 ...  ← SEQUENCE (certificationRequestInfo)
│   │
│   ├── 02 01 00  ← INTEGER 0 (version = v1)
│   │
│   ├── 30 ...    ← SEQUENCE (subject / Name)
│   │   └── 31 ...  ← SET (RDN)
│   │       └── 30 ...  ← SEQUENCE (AttributeTypeAndValue)
│   │           ├── 06 03 55 04 03  ← OID 2.5.4.3 (commonName)
│   │           └── 0C 0D 61 70 69 2E 61 63 6D 65 2E 63 6F 6D
│   │                               ← UTF8String "api.acme.com"
│   │
│   ├── 30 ...    ← SEQUENCE (subjectPublicKeyInfo)
│   │   ├── 30 ...  ← SEQUENCE (AlgorithmIdentifier)
│   │   │   ├── 06 09 2A 86 48 86 F7 0D 01 01 01  ← OID rsaEncryption
│   │   │   └── 05 00  ← NULL (parameters)
│   │   └── 03 ...  ← BIT STRING (public key)
│   │       └── 00 30 ...  ← RSAPublicKey SEQUENCE
│   │           ├── 02 ...  ← INTEGER (modulus n)
│   │           └── 02 03 01 00 01  ← INTEGER 65537 (exponent e)
│   │
│   └── A0 ...    ← [0] IMPLICIT (attributes)
│       └── 30 ...  ← SEQUENCE (Attribute)
│           ├── 06 09 2A 86 48 86 F7 0D 01 09 0E  ← OID extensionRequest
│           └── 31 ...  ← SET
│               └── 30 ...  ← SEQUENCE (Extensions)
│                   └── 30 ...  ← Extension (SAN)
│                       ├── 06 03 55 1D 11  ← OID 2.5.29.17 (SAN)
│                       └── 04 ...  ← OCTET STRING
│                           └── 30 ...  ← GeneralNames
│                               └── 82 0D 61 70 69 2E 61 63 6D 65 2E 63 6F 6D
│                                       ← [2] dNSName "api.acme.com"
│
├── 30 0D           ← SEQUENCE (signatureAlgorithm)
│   ├── 06 09 2A 86 48 86 F7 0D 01 01 0B  ← OID sha256WithRSAEncryption
│   └── 05 00       ← NULL
│
└── 03 82 01 01 00 ...  ← BIT STRING (signature, 256 bytes for RSA-2048)
```

### 15.4 Inspect a CSR

```bash
# View CSR contents
openssl req -in server.csr -text -noout

# Output:
Certificate Request:
    Data:
        Version: 1 (0x0)
        Subject: CN=api.acme.com, O=Acme Corp, C=IN
        Subject Public Key Info:
            Public Key Algorithm: rsaEncryption
                RSA Public-Key: (4096 bit)
                Modulus: 00:b3:e4:...
                Exponent: 65537 (0x10001)
        Attributes:
            Requested Extensions:
                X509v3 Subject Alternative Name:
                    DNS:api.acme.com
                X509v3 Key Usage: critical
                    Digital Signature, Key Encipherment
                X509v3 Extended Key Usage:
                    TLS Web Server Authentication
    Signature Algorithm: sha256WithRSAEncryption
    Signature Value:
        2a:f3:...
```

---

## 16. Case Guide — Which Fields Are Present

### Case 1: TLS Server Certificate (api.acme.com)

**Who generates:** Server admin (e.g., John with domain_authorizations)  
**Key generated on:** The actual server or HSM  
**Purpose:** HTTPS, securing web/API traffic

```
Subject DN:
  CN = api.acme.com          ← FQDN of server (MANDATORY)
  O  = Acme Corp             ← org name (RECOMMENDED)
  OU = IT Security           ← unit (OPTIONAL, deprecated for public)
  C  = IN                    ← country (RECOMMENDED)
  ST = Karnataka             ← state (OPTIONAL)
  L  = Bengaluru             ← city (OPTIONAL)

Public Key:
  RSA-4096 or EC P-256/P-384 ← MANDATORY, minimum RSA-2048

SAN:
  dNSName = api.acme.com     ← MANDATORY (RFC 2818, browsers require SAN)
  dNSName = api2.acme.com    ← OPTIONAL additional hostnames
                               (absent if single-domain cert)

Key Usage:
  digitalSignature           ← MANDATORY (TLS handshake)
  keyEncipherment            ← if RSA (TLS 1.2 RSA key exchange)
  keyAgreement               ← if EC (ECDH, optional in CSR)

EKU:
  serverAuth                 ← MANDATORY for TLS Server
  clientAuth                 ← OPTIONAL (if server also needs client auth)

Basic Constraints:
  absent OR cA=FALSE         ← never cA=TRUE for end-entity

challengePassword:
  absent                     ← not used in REST enrollment

Signature Algorithm:
  sha256WithRSAEncryption    ← if RSA key
  ecdsa-with-SHA256          ← if EC key
```

**OpenSSL command:**
```bash
# Generate key
openssl genrsa -out server.key 4096

# Generate CSR
openssl req -new -key server.key \
  -subj "/CN=api.acme.com/O=Acme Corp/C=IN" \
  -addext "subjectAltName=DNS:api.acme.com,DNS:api2.acme.com" \
  -addext "keyUsage=critical,digitalSignature,keyEncipherment" \
  -addext "extendedKeyUsage=serverAuth" \
  -out server.csr
```

---

### Case 2: TLS Client Authentication Certificate (John)

**Who generates:** John on his workstation  
**Key generated on:** Laptop, smartcard, or soft token  
**Purpose:** Authenticate John to servers (mTLS, VPN, web portals)

```
Subject DN:
  CN = John Doe              ← person's name (COMMON)
  OR
  CN = john.doe              ← username format (COMMON in enterprise)
  O  = Acme Corp             ← OPTIONAL
  C  = IN                    ← OPTIONAL

Public Key:
  RSA-2048 or EC P-256       ← personal cert, 2048 acceptable

SAN:
  rfc822Name = john@acme.com ← OPTIONAL (email, useful for identity)
  otherName (UPN) = john@acme.com  ← if Microsoft/AD environment

Key Usage:
  digitalSignature           ← MANDATORY (authentication via signing)

EKU:
  clientAuth                 ← MANDATORY
  emailProtection            ← OPTIONAL (if same cert for S/MIME)
  msSmartcardLogin           ← if smartcard login (Microsoft AD)

Basic Constraints:
  absent OR cA=FALSE

challengePassword:
  absent (REST + AD auth)

Signature Algorithm:
  sha256WithRSAEncryption or ecdsa-with-SHA256
```

**OpenSSL command:**
```bash
openssl genrsa -out john.key 2048

openssl req -new -key john.key \
  -subj "/CN=John Doe/O=Acme Corp/C=IN" \
  -addext "subjectAltName=email:john@acme.com" \
  -addext "keyUsage=critical,digitalSignature" \
  -addext "extendedKeyUsage=clientAuth" \
  -out john.csr
```

---

### Case 3: S/MIME Email Protection Certificate

**Who generates:** John (or auto-enrollment system)  
**Key generated on:** Email client (Outlook, Thunderbird), smartcard  
**Purpose:** Sign and/or encrypt emails

```
Subject DN:
  CN = John Doe              ← person's full name (MANDATORY)
  O  = Acme Corp             ← OPTIONAL
  C  = IN                    ← OPTIONAL
  emailAddress = john@acme.com  ← LEGACY only, prefer SAN

Public Key:
  RSA-2048/4096 or EC P-256  ← if signing only: EC fine
                                if encryption: RSA or EC P-256

SAN:
  rfc822Name = john@acme.com ← MANDATORY (modern S/MIME)

Key Usage:
  For signing cert:
    digitalSignature
    nonRepudiation            ← OPTIONAL, legal non-repudiation

  For encryption cert:
    keyEncipherment           ← RSA key
    keyAgreement              ← EC key

  For combined (single cert):
    digitalSignature, nonRepudiation, keyEncipherment

EKU:
  emailProtection             ← MANDATORY

Basic Constraints:
  absent or cA=FALSE

Signature Algorithm:
  sha256WithRSAEncryption or ecdsa-with-SHA256
```

**Best practice:** Use **separate keys** for signing and encryption:
- Signing key: stays on device, never backed up
- Encryption key: backed up by CA (key escrow) so encrypted emails can
  be recovered even if key is lost

**OpenSSL command:**
```bash
openssl genrsa -out john-smime.key 4096

openssl req -new -key john-smime.key \
  -subj "/CN=John Doe/O=Acme Corp/C=IN" \
  -addext "subjectAltName=email:john@acme.com" \
  -addext "keyUsage=critical,digitalSignature,nonRepudiation,keyEncipherment" \
  -addext "extendedKeyUsage=emailProtection" \
  -out john-smime.csr
```

---

### Case 4: Code Signing Certificate

**Who generates:** Developer or CI/CD build system  
**Key generated on:** HSM (mandatory for EV code signing), or secure workstation  
**Purpose:** Sign executables, scripts, packages, drivers

```
Subject DN:
  CN = Acme Corp             ← organization or developer name
  O  = Acme Corp             ← MANDATORY
  C  = IN                    ← MANDATORY

  For EV Code Signing (optional):
    serialNumber = U72200KA2010PTC123456  ← company registration number
    businessCategory = Private Organization
    jurisdictionC = IN

Public Key:
  RSA-4096 or EC P-256       ← minimum RSA-3072 for new certs (CAB Forum)
  Key MUST be on HSM for EV Code Signing

SAN:
  usually absent             ← code signing certs rarely have SAN

Key Usage:
  digitalSignature           ← MANDATORY

EKU:
  codeSigning                ← MANDATORY
  timeStamping               ← OPTIONAL (if also used for timestamps)

Basic Constraints:
  absent or cA=FALSE

challengePassword:
  absent

Signature Algorithm:
  sha256WithRSAEncryption or ecdsa-with-SHA256
```

---

### Case 5: Device / IoT Certificate

**Who generates:** Device itself (on-board crypto), or provisioning system  
**Key generated on:** TPM chip, secure element, or HSM  
**Purpose:** Device identity, device-to-server mTLS

```
Subject DN:
  CN = device-serial-001     ← device unique ID
  OR
  CN = printer-floor2.acme.com  ← device hostname
  O  = Acme Corp
  OU = Manufacturing Floor 2
  serialNumber = SN-2026-DEVICE-001  ← device serial number

Public Key:
  EC P-256 preferred         ← smaller key, faster on constrained devices
  RSA-2048 if TPM 1.2 (older TPMs only support RSA)

SAN:
  dNSName = printer-floor2.acme.com   ← if device has hostname
  OR
  otherName (hwModuleName) = ...       ← hardware module name OID

Key Usage:
  digitalSignature
  keyEncipherment            ← if device also encrypts data

EKU:
  clientAuth                 ← device authenticates to servers
  OR
  serverAuth                 ← if device acts as server too

challengePassword:
  May be present if SCEP enrollment (printer using SCEP)
```

---

### Case 6: SCEP Enrollment (Network Device / Printer)

**Who generates:** Device firmware / OS  
**Protocol:** SCEP (Simple Certificate Enrollment Protocol)  
**Purpose:** Automated certificate enrollment for printers, switches, VPN

```
Subject DN:
  CN = printer-floor2        ← device name
  O  = Acme Corp
  C  = IN

Public Key:
  RSA-2048                   ← SCEP requires RSA, no EC in original SCEP

SAN:
  may be absent              ← legacy SCEP devices often omit SAN

Key Usage:
  digitalSignature
  keyEncipherment

EKU:
  clientAuth

challengePassword:
  PRESENT                    ← MANDATORY for SCEP authentication
  Value = pre-shared OTP from SCEP server admin

Signature Algorithm:
  sha256WithRSAEncryption
```

---

## 17. How Client Generates a CSR — Step by Step

### Step 1: Generate Key Pair

```bash
# RSA-4096
openssl genrsa -out private.key 4096

# EC P-256
openssl ecparam -name prime256v1 -genkey -noout -out private.key

# Ed25519
openssl genpkey -algorithm ed25519 -out private.key

# With password protection on private key
openssl genrsa -aes256 -out private.key 4096
# Prompts for passphrase → encrypts private key with AES-256
```

**What happens:**
- OS entropy pool (`/dev/urandom`) seeded by hardware events
- CSPRNG generates random bytes
- Key generation algorithm runs (prime generation for RSA, scalar
  multiplication for EC)
- Private key saved to file (or HSM if using PKCS#11)

### Step 2: Create CSR

```bash
# Simple CSR (minimal — no extensions in CSR)
openssl req -new -key private.key \
  -subj "/CN=api.acme.com/O=Acme Corp/C=IN" \
  -out server.csr

# Full CSR with extensions
openssl req -new -key private.key \
  -subj "/CN=api.acme.com/O=Acme Corp/OU=IT/C=IN/ST=Karnataka/L=Bengaluru" \
  -addext "subjectAltName=DNS:api.acme.com,DNS:*.acme.com" \
  -addext "keyUsage=critical,digitalSignature,keyEncipherment" \
  -addext "extendedKeyUsage=serverAuth" \
  -out server.csr
```

**What happens internally:**
1. Build `CertificationRequestInfo`:
   - Set version = 0
   - Encode Subject DN (each field as OID + value pair)
   - Encode SubjectPublicKeyInfo (derive from private.key)
   - Encode extensions into `extensionRequest` attribute
2. DER-encode `CertificationRequestInfo`
3. Compute `hash = SHA-256(DER bytes)`
4. Compute `signature = RSA_Sign(privateKey, hash)` or ECDSA/EdDSA
5. Build `CertificationRequest`:
   - `certificationRequestInfo` = the SEQUENCE from step 2
   - `signatureAlgorithm` = sha256WithRSAEncryption OID
   - `signature` = BIT STRING from step 4
6. DER-encode `CertificationRequest`
7. Base64-encode → add PEM headers → write to `server.csr`

### Step 3: Verify CSR Before Submitting

```bash
# View the CSR contents
openssl req -in server.csr -text -noout -verify

# Verify self-signature
openssl req -verify -in server.csr -noout
# Output: verify OK

# Extract public key from CSR
openssl req -in server.csr -pubkey -noout

# Check specific field
openssl req -in server.csr -subject -noout
# Output: subject=CN=api.acme.com, O=Acme Corp, C=IN
```

### Step 4: Submit to RA

```bash
# REST API
curl -X POST https://ra.acme.com/api/ra/requests \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{
    "pkcs10": "'"$(cat server.csr)"'",
    "clientTxnId": "TXN-2026-07-03-001"
  }'
```

### Step 5: Store Private Key Securely

```bash
# After submitting CSR, protect the private key:

# Option A: Encrypted file
openssl pkcs8 -in private.key -topk8 -v2 aes256 -out private.key.enc
chmod 600 private.key.enc
rm private.key   # remove unencrypted version

# Option B: PKCS#12 (combine cert + key after issuance)
openssl pkcs12 -export -in certificate.pem -inkey private.key \
  -out identity.p12 -passout pass:MySecurePassphrase

# Option C: HSM (best for servers)
# Key never leaves HSM, only CSR extracted
```

---

## 18. Master Presence/Absence Table

```
Field / Extension           TLS Server  TLS Client  S/MIME   Code Sign  SCEP Device
--------------------------  ----------  ----------  -------  ---------  -----------
version (always 0)          MUST        MUST        MUST     MUST       MUST
subject CN                  MUST        MUST        MUST     MUST       MUST
subject O                   REC         OPT         OPT      MUST       OPT
subject OU                  OPT         OPT         OPT      OPT        OPT
subject C                   REC         OPT         OPT      MUST       OPT
subject ST                  OPT         OPT         OPT      OPT        OPT
subject L                   OPT         OPT         OPT      OPT        OPT
subject emailAddress        MUST NOT    OPT(legacy) OPT(leg) MUST NOT   MUST NOT
subject serialNumber        OPT(EV)     MUST NOT    NO       OPT(EV)    OPT
subjectPublicKeyInfo        MUST        MUST        MUST     MUST       MUST
RSA key                     OPT         OPT         OPT      OPT        MUST(SCEP)
EC key                      OPT         OPT         OPT      OPT        NO(SCEP)
Ed25519 key                 OPT         OPT         NO       NO         NO
SAN dNSName                 MUST        NO          NO       NO         OPT
SAN rfc822Name (email)      MUST NOT    OPT         MUST     NO         NO
SAN iPAddress               OPT         NO          NO       NO         NO
SAN otherName (UPN)         NO          OPT(AD)     NO       NO         NO
Key Usage (critical)        REC         REC         REC      REC        OPT
  digitalSignature          MUST        MUST        MUST     MUST       OPT
  nonRepudiation            NO          NO          OPT      NO         NO
  keyEncipherment           RSA only    NO          OPT      NO         OPT
  keyAgreement              EC only     NO          OPT      NO         NO
  keyCertSign               NEVER       NEVER       NEVER    NEVER      NEVER
  cRLSign                   NEVER       NEVER       NEVER    NEVER      NEVER
EKU serverAuth              MUST        NO          NO       NO         NO
EKU clientAuth              OPT         MUST        NO       NO         MUST
EKU emailProtection         NO          OPT         MUST     NO         NO
EKU codeSigning             NO          NO          NO       MUST       NO
EKU anyExtendedKeyUsage     NEVER       NEVER       NEVER    NEVER      NEVER
Basic Constraints cA=FALSE  OPT         OPT         OPT      OPT        OPT
Basic Constraints cA=TRUE   NEVER(end)  NEVER       NEVER    NEVER      NEVER
challengePassword           ABSENT      ABSENT      ABSENT   ABSENT     MUST(SCEP)
signatureAlgorithm          MUST        MUST        MUST     MUST       MUST
  sha1With...               NEVER       NEVER       NEVER    NEVER      NEVER
  md5With...                NEVER       NEVER       NEVER    NEVER      NEVER
  sha256With...             REC         REC         REC      REC        REC
signature (self-sign)       MUST        MUST        MUST     MUST       MUST

Legend:
  MUST     = Required — reject CSR if absent
  REC      = Recommended — warn if absent, CA applies from template
  OPT      = Optional — present or absent, both acceptable
  NO       = Not applicable for this cert type
  NEVER    = Must not be present — reject CSR if found
  OPT(leg) = Optional (legacy format only)
  OPT(AD)  = Optional, only in Active Directory environments
  RSA only = Only when RSA key used
  EC only  = Only when EC key used
```

---

*End of Document — PKCS#10 CSR Structure Guide v1.0*  
*Related: RA_Approval_Workflow_Architecture_V2.md, CSR_Validation_7_Layer_Guide.txt*


