# RA Validation by Certificate Type — Category Plan with Examples

> Har certificate type ke liye: RA kaunsi validation karega, kis category mein, ek real example ke saath.
> Deep-research sources: CABF TLS BR (SC-081, 2026), Code Signing BR (2026 1-year cap), S/MIME BR
> (Legacy profile retired July 2025), CCA India IVG (video eKYC), RFC 2986/5280/8555/8659.
> Companion docs: [csr-validation.md](csr-validation.md) (check-level detail),
> [ra-validation-master-catalog.md](ra-validation-master-catalog.md) (201-check tier catalog).

## The 6 Universal Categories

Har certificate type pe yahi 6 categories apply hoti hain — bas **weight** alag hota hai:

```
CAT-1  TECHNICAL      CSR parse, PoP signature, key strength, extensions
CAT-2  CONTROL        "Kya aap us cheez ko control karte ho jiska naam cert mein hai?"
                      (domain / mailbox / device / identity control)
CAT-3  IDENTITY       "Kya aap woh ho jo aap keh rahe ho?" (org registry, KYC, HR)
CAT-4  POLICY         Profile rules: EKU/KU combos, validity caps, naming rules
CAT-5  RISK           Blocklists, sanctions, phishing/malware, anomalies
CAT-6  WORKFLOW       Approval, separation of duties, audit evidence
```

### Weight Matrix — kis type pe kaunsi category heavy hai

| Category | TLS_SERVER | TLS_CLIENT | S/MIME | CODE_SIGNING | DOC_SIGNING |
|----------|:---------:|:----------:|:------:|:------------:|:-----------:|
| CAT-1 Technical | ●●● | ●●● | ●●● | ●●● | ●●● |
| CAT-2 Control   | ●●● domain | ●● identity | ●●● mailbox | ● | ● |
| CAT-3 Identity  | ● (OV/EV only) | ●● HR/CMDB | ●● (sponsor) | ●●● registry+KYC | ●●● full KYC |
| CAT-4 Policy    | ●●● | ●● | ●● | ●●● | ●● |
| CAT-5 Risk      | ●● phishing | ● | ● | ●●● malware | ● |
| CAT-6 Workflow  | ● (auto ok) | ●● | ● | ●●● dual manual | ●●● manual |

**Reading the matrix:** TLS server = "control-heavy, identity-light" (DV mein identity zero).
Code/document signing = "identity-heavy, control-light" (koi domain hi nahi hai verify karne ko).
Yahi ek line aapke RA ka architecture decide karti hai: **CAT-2 automated engines chahiye,
CAT-3 manual vetting queue chahiye.**

---

## 1. TLS_SERVER — Example: Salman ko `api.salmantech.com` ke liye cert chahiye

**Scenario:** Salman, Salman Technologies Pvt Ltd ka DevOps engineer, payment API ke liye
TLS server cert maangta hai.

```bash
openssl req -new -key server.key \
  -subj "/CN=api.salmantech.com/O=Salman Technologies Pvt Ltd/C=IN" \
  -addext "subjectAltName=DNS:api.salmantech.com,DNS:api-dr.salmantech.com" \
  -addext "keyUsage=critical,digitalSignature,keyEncipherment" \
  -addext "extendedKeyUsage=serverAuth" \
  -out server.csr
```

### RA kya karega — category-wise

| Cat | Validation | Is example mein concretely |
|-----|-----------|---------------------------|
| CAT-1 | PoP self-signature verify | CSR signature `server.key` ke public half se verify hui ✓ |
| CAT-1 | Key check | RSA 2048, e=65537, ROCA/Debian/pwnedkeys clean ✓ |
| CAT-1 | FQDN syntax | `api.salmantech.com` — labels valid, PSL pe registrable ✓ |
| CAT-2 | **Domain Control Validation** | RA token generate karta hai `t9x2...`; Salman DNS TXT record `_pki-validation.salmantech.com = t9x2...` banata hai; RA resolve karke match karta hai ✓ — **yeh dono SANs cover karta hai** kyunki dono `salmantech.com` ke under hain |
| CAT-2 | CAA check (RFC 8659) | `dig CAA salmantech.com` → `0 issue "ourca.example"` — hamari CA allowed ✓ (issuance se ≤ 8 hrs pehle) |
| CAT-3 | Org verification (OV profile) | `Salman Technologies Pvt Ltd` MCA registry mein active, CIN match ✓ — *(DV profile hota to yeh step skip)* |
| CAT-4 | Profile rules | EKU=serverAuth ✓, CN SAN mein present ✓, no wildcard, no IP ✓ |
| CAT-4 | Validity clamp | Salman ne 365 days maange → **200 days pe clamp** (SC-081, Mar 2026 se) |
| CAT-5 | Risk screen | `salmantech.com` Safe Browsing clean, high-value list pe nahi ✓ |
| CAT-6 | Workflow | DCV automated pass → auto-approve allowed (policy); audit log mein DNS lookup evidence + timestamp stored |

### Reject example (isi scenario ka evil twin)

Salman galti se `/CN=*.salmantech.com` + SAN `DNS:salmantech.co` (typo — dusra domain!) bhejta hai:
- CAT-2 FAIL: `salmantech.co` ke liye DCV token kabhi place hi nahi ho sakta — Salman us domain
  ko control nahi karta → `PKI_DCV_001`
- CAT-4 WARN: wildcard needs DNS-based DCV explicitly + policy approval
- Result: REJECTED, precise error code, koi manual review waste nahi hua

---

## 2. TLS_CLIENT — Example: `service-a` ko mTLS se `service-b` se baat karni hai

**Scenario:** Salman apne pqc-demo jaise setup mein `service-a` ke liye client cert maangta hai
taaki `service-b` use mTLS pe accept kare.

```bash
openssl req -new -key client.key \
  -subj "/CN=service-a/O=Salman Technologies Pvt Ltd/C=IN" \
  -addext "keyUsage=critical,digitalSignature" \
  -addext "extendedKeyUsage=clientAuth" \
  -out client.csr
```

### RA kya karega — category-wise

| Cat | Validation | Is example mein concretely |
|-----|-----------|---------------------------|
| CAT-1 | PoP + key + parse | Standard — same as server |
| CAT-2 | **Identity control** | `service-a` company ke service inventory (CMDB) mein registered hai? Owner team = Salman ki team? ✓ |
| CAT-2 | Requester binding | Salman `service-a` ka registered owner/deployer hai — koi aur team ke service ka cert nahi maang sakta |
| CAT-3 | Requester identity | Salman AD mein active employee, MFA session ✓ |
| CAT-4 | Profile rules | EKU=clientAuth only (serverAuth reject), isCA=false, hostname-style CN nahi hai ✓ |
| CAT-4 | Validity | Policy: client certs ≤ 1 year |
| CAT-5 | Risk | `service-a` ke liye already active cert? Rotation window check |
| CAT-6 | Workflow | ITSM ticket linked, team-lead approval (human cert hota to manager approval) |

### Human variant (Salman khud ke laptop ke liye)

Same categories, CAT-2/CAT-3 badal jaati hain: HR database match (employee ID), email challenge
`salman@salmantech.com` pe, UPN SAN = AD userPrincipalName exact match (smartcard login).

### Reject example

Salman `CN=service-b` maangta hai (jo payment team ka hai):
- CAT-2 FAIL: CMDB says owner = payments team, requester = Salman (platform team) → `PKI_AUTHZ_003`
- Yahi **impersonation guard** hai — client certs mein sabse important check.

---

## 3. S/MIME — Example: Salman ko `salman@salmantech.com` ke liye email cert chahiye

**Scenario:** Company policy — sab employees signed email bhejenge. Salman
**sponsor-validated** profile ka cert maangta hai (enterprise ka standard profile:
person + organization dono subject mein).

```bash
openssl req -new -key smime.key \
  -subj "/CN=Salman Khan/O=Salman Technologies Pvt Ltd/C=IN" \
  -addext "subjectAltName=email:salman@salmantech.com" \
  -addext "keyUsage=critical,digitalSignature,keyEncipherment" \
  -addext "extendedKeyUsage=emailProtection" \
  -out smime.csr
```

### RA kya karega — category-wise

| Cat | Validation | Is example mein concretely |
|-----|-----------|---------------------------|
| CAT-1 | PoP + key + parse | Standard |
| CAT-2 | **Mailbox Control Validation** | Do raaste (S/MIME BR 3.2.2): (a) random code `84KQ...` exactly `salman@salmantech.com` pe mail hota hai, Salman portal mein enter karta hai ✓; ya (b) enterprise route — `salmantech.com` domain-validated hai (TLS DCV jaisa) + org attests ki mailbox Salman ka hai |
| CAT-2 | Domain reuse window | Mailbox/domain control evidence ≤ **398 days** purana (S/MIME BR) |
| CAT-3 | Person identity (sponsor-validated) | `CN=Salman Khan` HR record se match — enterprise RA yahan **sponsor** hai; identity evidence ≤ **825 days** purana |
| CAT-3 | Org identity | O= registry-verified (TLS OV vetting reuse ho sakti hai window ke andar) |
| CAT-4 | Generation profile | **Strict ya Multipurpose** — Legacy profile **July 2025 mein retire ho chuka** hai; strict = extension rules tight |
| CAT-4 | Validity cap | Max **824 days** (strict/multipurpose) — Salman ko 2 saal ka mila |
| CAT-4 | EKU/KU | emailProtection only; RSA → keyEncipherment (encryption ke liye), EC hota → keyAgreement rules |
| CAT-5 | Risk | Mail domain spoof-list check; disposable-mail domains reject |
| CAT-6 | Workflow | Mailbox challenge automated → auto-issue allowed; encryption-cert escrow hai to dual-control recovery policy |

### Reject example

Salman SAN mein `salman@gmail.com` daal deta hai:
- CAT-2 FAIL possible (woh mailbox to control karta hai — challenge pass ho jayega!) **lekin**
- CAT-4 FAIL: sponsor-validated profile + `O=Salman Technologies` ke saath **non-org domain**
  allowed nahi — org sirf apne verified mail domains sponsor kar sakta hai → `PKI_POL_006`
- Lesson: **control pass hone ke baad bhi policy fail ho sakti hai** — categories independent hain.

---

## 4. CODE_SIGNING — Example: Salman Technologies apna installer sign karna chahti hai

**Scenario:** Company ka Windows installer `SalmanSetup.exe` bina "Unknown Publisher" warning ke
distribute karna hai. Salman (release engineer) OV code signing cert maangta hai.

```bash
# Key HSM/token ke ANDAR generate hoti hai — file mein nahi!
# CSR bhi token se hi nikalti hai (e.g. YubiKey/SafeNet PKCS#11)
openssl req -new -engine pkcs11 -keyform engine -key "pkcs11:object=cs-key" \
  -subj "/CN=Salman Technologies Pvt Ltd/O=Salman Technologies Pvt Ltd/C=IN" \
  -addext "keyUsage=critical,digitalSignature" \
  -addext "extendedKeyUsage=codeSigning" \
  -out codesign.csr
```

### RA kya karega — category-wise

| Cat | Validation | Is example mein concretely |
|-----|-----------|---------------------------|
| CAT-1 | PoP + key | RSA **3072** minimum (2048 REJECT — CSBR); signature verify ✓ |
| CAT-1 | **Key attestation** | Token se attestation statement: "yeh key FIPS 140-2 L2 hardware mein generate hui, non-exportable" — attestation cert chain YubiKey/vendor root tak verify ✓ (2023 se mandatory; software key = hard reject) |
| CAT-2 | (Light) | Koi domain/mailbox nahi — control = "requester org ka bana hua hai" |
| CAT-3 | **Org legal existence** | MCA/ROC lookup: "Salman Technologies Pvt Ltd", CIN U72900MH2019PTC123456, status ACTIVE ✓ |
| CAT-3 | **Verified callback** | RA registry/QIIS se company ka number nikalta hai (Salman ka diya number NAHI) → call → "kya aapne code signing cert authorize kiya? Salman authorized hai?" → HR/director confirms ✓ |
| CAT-3 | Requester authority | Signed authorization letter on company letterhead, signer verified |
| CAT-4 | Profile | CN=org legal name exact, O+C mandatory, no SAN, EKU=codeSigning only ✓ |
| CAT-4 | Validity cap | **≤ 1 year (Feb 2026 se — pehle 39 months)** |
| CAT-5 | **Malware screening** | Org name + key fingerprints malware-signing DBs mein nahi; koi prior revoked-for-abuse cert nahi; "Salman Technologies" ≈ typosquat of known brand? Nahi ✓ |
| CAT-5 | Sanctions | OFAC/UN/EU lists — org + directors clean ✓ |
| CAT-6 | **Dual manual approval** | Do RA officers independently review — code signing KABHI auto-approve nahi; sab evidence audit-linked |

### Reject example

Applicant "Microsofft Solutions Pvt Ltd" (double-f) 3072-bit key ke saath perfect CSR bhejta hai:
- CAT-1 through CAT-4: sab PASS ✓ — technically flawless request
- CAT-5 FAIL: typosquat detector "Microsofft" ≈ "Microsoft" flag karta hai → manual review →
  intent unclear → REJECT + denied-list entry
- Lesson: **code signing mein technical perfection ka matlab kuch nahi** — signed malware ka
  sabse bada source technically-valid certs hi hote hain.

---

## 5. DOCUMENT_SIGNING — Example: Salman legal contracts PDF sign karega (India DSC)

**Scenario:** Salman ko vendor contracts digitally sign karne hain — India mein legally valid
(IT Act 2000). Class 3 individual DSC chahiye.

```bash
# Key FIPS 140-2 L2 crypto token mein generate — CCA mandate
openssl req -new -engine pkcs11 -keyform engine -key "pkcs11:object=dsc-key" \
  -subj "/CN=Salman Khan/O=Salman Technologies Pvt Ltd/C=IN" \
  -addext "keyUsage=critical,digitalSignature,nonRepudiation" \
  -out docsign.csr
```

### RA kya karega — category-wise

| Cat | Validation | Is example mein concretely |
|-----|-----------|---------------------------|
| CAT-1 | PoP + key + token | Key crypto token mein hai (CCA: FIPS 140-2 L2 USB token mandatory for signing DSC) |
| CAT-2 | (Light) | Identity control = KYC hi hai |
| CAT-3 | **Full personal KYC (CCA IVG)** | Options: (a) Aadhaar eKYC — OTP/biometric, no documents needed; (b) PAN + attested documents; (c) banking KYC. PLUS **video verification — issuance se ≤ 2 din purani** honi chahiye: Salman live video mein PAN dikhata hai, randomized questions ka jawab deta hai ✓ |
| CAT-3 | Org affiliation (org-person DSC) | O= field hai to: employment proof + org authorization letter |
| CAT-4 | KU rules | digitalSignature + **nonRepudiation mandatory** — legal "maine sign nahi kiya" defense band |
| CAT-4 | Scope isolation | No serverAuth/clientAuth/codeSigning EKU |
| CAT-4 | Validity | CCA: **1, 2, ya 3 saal** — Salman 2 saal leta hai |
| CAT-5 | Risk | PAN/Aadhaar blocklist, prior fraud flags |
| CAT-6 | **Manual RA verification** | RA officer video + documents review karta hai; evidence 7 saal retain (CCA audit) |

### Reject example

Salman ka video KYC 5 din pehle hua tha, aaj issue karna hai:
- CAT-3 FAIL: CCA IVG — video verification issuance se **≤ 2 days** honi chahiye → re-video
- Lesson: **evidence freshness bhi ek validation hai** — sirf evidence hona kaafi nahi.

---

## Planning Summary — isko implement kaise karein

### Ek hi pipeline, pluggable category engines

```
Request → [CAT-1 Technical Engine]      ← pure code, Bouncy Castle (aapka 7-layer service)
        → [CAT-4 Policy Engine]         ← profile YAML/DB driven, per cert type
        → [CAT-2 Control Engine]        ← DNS/HTTP/email challenge automation + CMDB/HR connectors
        → [CAT-5 Risk Engine]           ← blocklists, fuzzy matching, external feeds
        → [CAT-3 Identity Queue]        ← manual vetting UI + registry connectors (sirf jahan chahiye)
        → [CAT-6 Workflow Engine]       ← state machine, maker-checker, audit sink
```

### Cert type sirf configuration hai

| Type | CAT-2 mode | CAT-3 depth | CAT-6 approval | Validity cap (2026) |
|------|-----------|-------------|----------------|---------------------|
| TLS_SERVER (DV) | DNS/HTTP/ACME auto | none | auto | 200 days |
| TLS_SERVER (OV) | DNS/HTTP/ACME auto | org registry | auto after vetting | 200 days |
| TLS_CLIENT | HR/CMDB lookup | employee/service record | 1 approver | 1 year (policy) |
| S/MIME (sponsor) | mailbox challenge | HR + org (reusable ≤825d) | auto | 824 days |
| CODE_SIGNING | key attestation | registry + callback + authority | **2 officers, manual** | 1 year |
| DOC_SIGNING (IN) | token check | full KYC + video ≤2d fresh | manual officer | 3 years |

**Golden rule:** naya certificate type add karna = nayi row is table mein + profile config —
naya code nahi. Yahi enterprise RA ka architecture test hai.

