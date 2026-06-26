# PKI-RA Approval Workflow — Complete Guide (Hinglish)

**Branch:** `csr-approval-dev`
**Date:** 2026-06-26

---

## 1. System Kya Hai?

PKI-RA ek Registration Authority hai jo CSR (Certificate Signing Request) process karta hai. Jab kisi ko SSL certificate chahiye, wo RA ke paas request bhejta hai. RA verify karta hai, approve karta hai, aur external CA (WLCA) se certificate banwata hai.

```
Client → RA (verify + approve) → CA (sign) → Certificate → Client ko deliver
```

---

## 2. Users Aur Roles

```
+------------+----------------+---------------------------------------------+
| User       | Role           | Kya kar sakta hai                           |
+------------+----------------+---------------------------------------------+
| admin      | ROLE_ADMIN     | Config, assign, close, monitor, dashboard   |
| operator1  | ROLE_OPERATOR  | Pickup, review, approve, reject, return     |
| operator2  | ROLE_OPERATOR  | Checker ban sakta hai (DUAL mode mein)      |
| rahul      | ROLE_USER      | CSR submit karta hai, certificate download  |
| auditor    | ROLE_AUDITOR   | Sirf dekhna — history, logs                 |
+------------+----------------+---------------------------------------------+
```

---

## 3. Tables Kahan Kya Store Hota Hai

```
+----------------------------+------------------------------------------------+
| Table                      | Kya store hota hai                             |
+----------------------------+------------------------------------------------+
| csr_requests               | MAIN table — CSR data + current status         |
|                            | (pkcs10, subject, requestor, profile, etc.)    |
+----------------------------+------------------------------------------------+
| csr_request_transitions    | HISTORY table — har status change ka record    |
|                            | (from, to, who, when, remarks) — append only   |
+----------------------------+------------------------------------------------+
| workflow_config            | Global settings — approval mode, assignment    |
|                            | mode, require remarks, max pending             |
+----------------------------+------------------------------------------------+
| approval_matrix            | Per-profile rules — TLS_SERVER=SINGLE,         |
|                            | CODE_SIGNING=DUAL, maker role, checker role    |
+----------------------------+------------------------------------------------+
| audit_log                  | Async audit — background thread likhta hai     |
|                            | (event-based, business logic se decouple)      |
+----------------------------+------------------------------------------------+
```

---

## 4. 13 Status Aur Unka Matlab

```
STATUS              KYA MATLAB HAI                          KAUN KARTA HAI
─────────────────────────────────────────────────────────────────────────────
RECEIVED            CSR aaya, validation pending             System (auto)
VALIDATED           7-layer validation pass                  System (auto)
VALIDATION_FAILED   Validation fail — client resubmit kare   System (auto)
SUBMITTED           Pool mein hai — operator pick up kare    System (auto)
IN_REVIEW           Operator ne pick up kiya, review ho raha Operator
REVIEWED            Maker ne review kiya (DUAL mode only)    Operator (Maker)
APPROVED            Approve ho gaya — CA ko bhejne ke liye   Operator/Checker
REJECTED            Reject hua — Admin reassign ya close     Operator/Checker
RETURNED            Operator ne wapas Admin ko bheja         Operator
CLOSED              Admin ne permanently band kiya (FINAL)   Admin
SENT_TO_CA          CSR external CA (WLCA) ko bhej diya     System (auto)
ISSUED              Certificate mil gaya CA se (FINAL)       System (CA callback)
FAILED              CA se error aaya — Admin retry kar sakta  System (CA callback)
```

### Terminal States (koi aur action nahi ho sakta):
```
CLOSED, ISSUED, VALIDATION_FAILED
```

---

## 5. State Machine — Kaunsa Status Kahan Ja Sakta Hai

```
RECEIVED ──→ VALIDATED ──→ SUBMITTED ──→ IN_REVIEW
   │                                        │
   └──→ VALIDATION_FAILED (final)           ├──→ APPROVED ──→ SENT_TO_CA ──→ ISSUED (final)
                                            │                      │
                                            ├──→ REVIEWED          └──→ FAILED
                                            │      ├──→ APPROVED          │
                                            │      └──→ REJECTED          └──→ APPROVED (retry)
                                            │
                                            ├──→ REJECTED ──→ IN_REVIEW (reassign)
                                            │           └──→ CLOSED (final)
                                            │
                                            └──→ RETURNED ──→ IN_REVIEW (reassign)
                                                        └──→ CLOSED (final)
```

---

## 6. Approval Modes — SINGLE vs DUAL

### SINGLE Mode (1 Operator):
```
Client CSR bhejta hai
  → System validate karta hai (RECEIVED → VALIDATED → SUBMITTED)
  → Operator pool se pick up karta hai (→ IN_REVIEW)
  → Operator seedha approve ya reject karta hai (→ APPROVED / REJECTED)
  → Approve hone pe system CA ko bhejta hai (→ SENT_TO_CA → ISSUED)

Total: 1 operator, fast process
Use: Low-risk certificates (TLS_SERVER, TLS_CLIENT, SMIME)
```

### DUAL Mode (Maker + Checker):
```
Client CSR bhejta hai
  → System validate karta hai (RECEIVED → VALIDATED → SUBMITTED)
  → Maker pool se pick up karta hai (→ IN_REVIEW)
  → Maker review karta hai remarks ke saath (→ REVIEWED)
  → DIFFERENT Checker accept ya reject karta hai (→ APPROVED / REJECTED)
  → Approve hone pe system CA ko bhejta hai (→ SENT_TO_CA → ISSUED)

IMPORTANT RULES:
  ❌ Maker khud Checker nahi ban sakta (Separation of Duties)
  ❌ Jo CSR submit kiya usne na Maker ban sakta na Checker
  ❌ DUAL mode mein direct approve karna BLOCKED hai

Total: 2 operators, secure process
Use: High-risk certificates (CODE_SIGNING, DOCUMENT_SIGNING)
```

---

## 7. Approval Matrix — Per Profile Rules

Admin har certificate profile ke liye alag rules set kar sakta hai:

```
Profile            Mode    Maker Role      Admin Maker?  Min Remarks  Max Pending
───────────────────────────────────────────────────────────────────────────────────
Global Default     DUAL    ROLE_OPERATOR   No            0            10
TLS_SERVER         SINGLE  ROLE_OPERATOR   Yes           0            15
TLS_CLIENT         SINGLE  ROLE_OPERATOR   Yes           0            15
CODE_SIGNING       DUAL    ROLE_OPERATOR   No            10           5
SMIME              SINGLE  ROLE_OPERATOR   Yes           0            20
DOCUMENT_SIGNING   DUAL    ROLE_OPERATOR   No            5            10
```

**Matlab:** TLS_SERVER ke liye ek operator kaafi hai, lekin CODE_SIGNING ke liye do operators chahiye aur minimum 10 character ka remark bhi dena padega.

**Resolution Order:**
1. Pehle profile-specific rule dekho (e.g., CODE_SIGNING)
2. Agar nahi mila to global default use karo (csrProfile = NULL)
3. Agar wo bhi nahi to error — "Admin must configure"

---

## 8. Complete API List

### Admin APIs:
```
GET  /api/ra/admin/config/workflow           → Current workflow config dekho
PUT  /api/ra/admin/config/workflow           → Workflow config change karo
GET  /api/ra/admin/approval-matrix           → Sab profile rules dekho
POST /api/ra/admin/approval-matrix           → Naya profile rule banao
PUT  /api/ra/admin/approval-matrix/{id}      → Profile rule update karo
GET  /api/ra/requests/summary                → Dashboard counts
POST /api/ra/requests/{id}/assign            → Operator ko assign karo
POST /api/ra/requests/{id}/close             → Permanently band karo
POST /api/ra/requests/{id}/retry             → Failed CA call retry karo
GET  /api/ra/requests/{id}/history           → Transition history dekho
```

### Operator APIs:
```
GET  /api/ra/requests/pool                   → Available requests (SUBMITTED)
GET  /api/ra/requests/my-work                → Mere assigned requests
POST /api/ra/requests/{id}/pickup            → Pool se pick up karo
POST /api/ra/requests/{id}/return            → Admin ko wapas bhejo
POST /api/ra/requests/{id}/approve           → Approve (SINGLE mode only)
POST /api/ra/requests/{id}/reject            → Reject karo
POST /api/ra/requests/{id}/review            → Maker review (DUAL mode only)
GET  /api/ra/requests/pending-check          → Checker ke liye ready requests
POST /api/ra/requests/{id}/accept            → Checker accept (DUAL mode only)
```

### Client APIs:
```
POST /api/ra/requests                        → CSR submit karo
GET  /api/ra/requests/by-txn-id/{txnId}      → Status check (txn ID se)
GET  /api/ra/requests/by-request-id/{reqId}   → Status check (request ID se)
GET  /api/ra/certificates/{serial}/download   → Certificate download (PEM/chain)
```

### CA Callback (Machine-to-Machine):
```
POST /api/ra/callback/certificate            → WLCA certificate bhejta hai
```

---

## 9. External CA (WLCA) Integration

### RA → WLCA (CSR bhejte waqt):
```
POST https://wlca.example.com/api/ca/sign-async

Body:
{
  "requestId": "RA-REQ-2026-000042",
  "csrPem": "-----BEGIN CERTIFICATE REQUEST-----\n...",
  "csrProfile": "TLS_SERVER",
  "validityDays": 365,
  "postBackUrl": "https://ra.acme.com/api/ra/callback/certificate"
}

Response 202:
{ "caTransactionId": "CA-TXN-2026-000042" }

Ye ASYNCHRONOUS hai — CA turant certificate nahi deta.
CA apna time leke process karta hai.
```

### WLCA → RA (Certificate wapas bhejte waqt):
```
POST https://ra.acme.com/api/ra/callback/certificate

SUCCESS:
{
  "requestId": "RA-REQ-2026-000042",
  "status": "ISSUED",
  "certificatePem": "-----BEGIN CERTIFICATE-----\n...",
  "serialNumber": "4F2A1B3C9E8D7F60",
  "notBefore": "2026-06-26",
  "notAfter": "2027-06-26"
}

FAILURE:
{
  "requestId": "RA-REQ-2026-000042",
  "status": "FAILED",
  "failureReason": "CSR signature verification failed"
}
```

---

## 10. Async Audit — Event-Based Architecture

### Problem (Pehle):
```
Har service method mein manually audit call:
  pickup()  → auditLogService.log(...)   ← 10 jagah copy-paste
  approve() → auditLogService.log(...)   ← developer bhool jaaye to miss
  reject()  → auditLogService.log(...)   ← API response slow
```

### Solution (Ab):
```
Observer Pattern + Spring Events + @Async

CsrTransitionService (single point)
    │
    │ publishEvent(CsrStatusChangedEvent)
    │
    ▼ (async, AFTER_COMMIT)
CsrAuditEventListener
    │
    │ background thread "audit-1"
    │
    ▼
audit_log table mein INSERT

Benefits:
  ✅ Service code mein ZERO audit calls
  ✅ API response FAST — audit background mein hota hai
  ✅ Audit fail hua to business transaction safe
  ✅ Naya listener add karna = naya class, service UNCHANGED
```

### Thread Pool:
```
auditExecutor:
  Core:     2 threads
  Max:      5 threads
  Queue:    100 events
  Policy:   CallerRunsPolicy (queue full → calling thread handles)
  Shutdown: Wait 30 seconds for pending audits
```

---

## 11. Database Columns — csr_requests Table

```
COLUMN                    TYPE           PURPOSE
────────────────────────────────────────────────────────────────
id                        BIGINT PK      Auto-increment
request_id                VARCHAR(25)    RA-REQ-2026-000001 (system generated)
client_txn_id             VARCHAR(100)   Client ka transaction ID (unique)
csr_pem                   TEXT           PKCS#10 CSR blob
csr_hash                  VARCHAR(64)    SHA-256 hash (duplicate check)
subject_dn                VARCHAR(500)   CN=api.acme.com,O=Acme,C=IN
key_algorithm             VARCHAR(20)    RSA / EC / Ed25519
key_size                  INT            2048 / 4096
signature_algorithm       VARCHAR(50)    SHA256withRSA
csr_profile               VARCHAR(30)    TLS_SERVER / CODE_SIGNING / etc.
requestor_name            VARCHAR(200)   Rahul Sharma
requestor_email           VARCHAR(200)   rahul@acme.com
requestor_department      VARCHAR(100)   Engineering
priority                  VARCHAR(10)    NORMAL / HIGH / URGENT
status                    VARCHAR(20)    Current status (13 mein se ek)
status_reason             VARCHAR(2000)  Reject/fail reason
validation_passed         BOOLEAN        Validation pass hua ya nahi
assigned_to_id            BIGINT FK      Assigned operator
assigned_at               DATETIME       Kab assign hua
maker_remarks             VARCHAR(2000)  Maker ke remarks
checker_decision          VARCHAR(10)    APPROVED / REJECTED
checker_remarks           VARCHAR(2000)  Checker ke remarks
approval_mode_at_pickup   VARCHAR(10)    Pickup ke waqt mode (SINGLE/DUAL)
ca_transaction_id         VARCHAR(100)   CA ka transaction ID
sent_to_ca_at             DATETIME       Kab CA ko bheja
post_back_url             VARCHAR(500)   CA certificate yahan post karega
ca_response_received_at   DATETIME       CA se response kab aaya
certificate_id            BIGINT FK      Issued certificate ka reference
created_at                DATETIME       Kab create hua
created_by                VARCHAR(100)   Kisne create kiya
updated_at                DATETIME       Last update kab
updated_by                VARCHAR(100)   Kisne update kiya
```

---

## 12. Complete Flow — Step by Step

### SINGLE MODE FLOW:
```
┌──────────┐   POST /requests    ┌─────────┐
│  Client  │ ──────────────────→ │   RA    │
│ (rahul)  │   CSR + profile     │ System  │
└──────────┘                     └────┬────┘
                                      │
                          ┌───────────┴───────────┐
                          │ 1. csr_requests INSERT │
                          │    status = RECEIVED   │
                          │ 2. 7-layer validation  │
                          │    status = VALIDATED   │
                          │ 3. Pool mein daal diya  │
                          │    status = SUBMITTED   │
                          └───────────┬───────────┘
                                      │
                    ┌─────────────────┴─────────────────┐
                    │ Operator1 picks up from pool      │
                    │ POST /requests/{id}/pickup         │
                    │ status = IN_REVIEW                 │
                    │                                    │
                    │ Operator1 approves                 │
                    │ POST /requests/{id}/approve         │
                    │ status = APPROVED                  │
                    └─────────────────┬─────────────────┘
                                      │
                          ┌───────────┴───────────┐
                          │ System sends to WLCA   │
                          │ status = SENT_TO_CA    │
                          │                        │
                          │ ... CA processes ...   │
                          │                        │
                          │ CA callback received   │
                          │ status = ISSUED        │
                          └───────────┬───────────┘
                                      │
                    ┌─────────────────┴─────────────────┐
                    │ Client downloads certificate       │
                    │ GET /certificates/{serial}/download │
                    └───────────────────────────────────┘
```

### DUAL MODE FLOW:
```
┌──────────┐   POST /requests    ┌─────────┐
│  Client  │ ──────────────────→ │   RA    │
│ (rahul)  │                     │ System  │
└──────────┘                     └────┬────┘
                                      │
                     RECEIVED → VALIDATED → SUBMITTED
                                      │
              ┌───────────────────────┴───────────────────────┐
              │ Operator1 (MAKER) picks up                    │
              │ POST /requests/{id}/pickup → IN_REVIEW        │
              │                                               │
              │ Maker reviews with remarks                    │
              │ POST /requests/{id}/review → REVIEWED         │
              └───────────────────────┬───────────────────────┘
                                      │
              ┌───────────────────────┴───────────────────────┐
              │ Operator2 (CHECKER) — MUST be different person │
              │                                               │
              │ ❌ Operator1 tries accept → "Maker cannot be  │
              │    Checker" (BLOCKED)                         │
              │ ❌ Rahul tries accept → "Requester cannot be   │
              │    Maker or Checker" (BLOCKED)                │
              │                                               │
              │ ✅ Operator2 accepts                           │
              │ POST /requests/{id}/accept → APPROVED         │
              └───────────────────────┬───────────────────────┘
                                      │
                     APPROVED → SENT_TO_CA → ISSUED
                                      │
                          Client downloads certificate
```

---

## 13. Transition History — Kaise Track Hota Hai

### Har status change pe 3 jagah record hota hai:

```
1. csr_requests.status           ← CURRENT status (UPDATE — sirf latest)
2. csr_request_transitions       ← HISTORY (INSERT — har change ka record)
3. audit_log                     ← AUDIT (async INSERT — background thread)
```

### Example — DUAL Mode Request History:
```
GET /api/ra/requests/2/history

#  FROM          TO            WHO         ROLE           WHEN                 REMARKS
─────────────────────────────────────────────────────────────────────────────────────────
1  NULL          RECEIVED      system      SYSTEM         2026-06-26 10:00:01  CSR received
2  RECEIVED      VALIDATED     system      SYSTEM         2026-06-26 10:00:01  7-layer passed
3  VALIDATED     SUBMITTED     system      SYSTEM         2026-06-26 10:00:02  Queued for approval
4  SUBMITTED     IN_REVIEW     operator1   ROLE_OPERATOR  2026-06-26 10:05:30  Picked up from pool
5  IN_REVIEW     REVIEWED      operator1   ROLE_OPERATOR  2026-06-26 10:15:45  CSR verified, recommend approval
6  REVIEWED      APPROVED      operator2   ROLE_OPERATOR  2026-06-26 10:20:12  Agreed with Maker
7  APPROVED      SENT_TO_CA    system      SYSTEM         2026-06-26 10:20:13  Sent to WLCA
8  SENT_TO_CA    ISSUED        system      SYSTEM         2026-06-26 10:25:00  Certificate received. Serial: 4F2A...

Total: 8 rows — complete audit trail
Maker = operator1, Checker = operator2 (alag log ✅)
```

---

## 14. Security Rules

```
RULE                                    KYA HOTA HAI
────────────────────────────────────────────────────────────────────
Maker ≠ Checker                         Jo review kiya wo accept nahi kar sakta
Requester ≠ Maker                       Jo CSR submit kiya wo approve nahi kar sakta
Requester ≠ Checker                     Jo CSR submit kiya wo check nahi kar sakta
SINGLE mein /review blocked             "SINGLE mode — use /approve directly"
DUAL mein /approve blocked              "DUAL mode — use /review first"
Terminal state pe koi action nahi        CLOSED/ISSUED pe approve/assign = 400 error
Max pending limit                        Operator ke paas zyada requests = blocked
Min remarks length                       CODE_SIGNING mein 10 char minimum
CA callback sirf SENT_TO_CA pe           Wrong status pe callback = 400 error
```

---

## 15. Config Properties

```yaml
# application.yml
ra:
  ca:
    base-url: http://localhost:8082        # WLCA ka URL
  callback:
    base-url: http://localhost:8083/ra-api  # RA ka callback URL (WLCA yahan bhejega)
```

```
# workflow_config table (DB mein)
approval_mode              = DUAL       # ya SINGLE
assignment_mode            = HYBRID     # ya SELF_PICKUP ya ADMIN_ASSIGN
max_pending_per_operator   = 10
maker_can_return_to_admin  = true
require_remarks            = true
```

---

## 16. Error Codes

```
CODE            MEANING
──────────────────────────────────────────────
PKI_APR_001     Invalid status transition
PKI_APR_002     Request not found
PKI_APR_003     Already assigned to another operator
PKI_APR_004     Not assigned to you
PKI_APR_005     Maker cannot be Checker
PKI_APR_006     Requester cannot be Maker/Checker
PKI_APR_007     DUAL mode — use /review first
PKI_APR_008     SINGLE mode — use /approve directly
PKI_APR_009     Remarks required
PKI_APR_010     Remarks too short
PKI_APR_011     Max pending limit reached
PKI_APR_012     CA submission failed
PKI_APR_013     CA callback invalid status
```

---

## 17. File Structure

```
common/
├── model/
│   ├── CsrRequest.java                    ← Main entity (45+ columns)
│   ├── CsrRequestTransition.java          ← Transition history entity
│   ├── WorkflowConfig.java                ← Global config entity
│   ├── ApprovalMatrix.java                ← Per-profile rules entity
│   └── enums/
│       ├── CsrStatus.java                 ← 13 statuses
│       ├── CsrStatusTransition.java       ← State machine (valid transitions)
│       ├── ApprovalMode.java              ← SINGLE, DUAL
│       ├── AssignmentMode.java            ← SELF_PICKUP, ADMIN_ASSIGN, HYBRID
│       ├── CsrProfile.java               ← TLS_SERVER, CODE_SIGNING, etc.
│       └── RequestPriority.java           ← NORMAL, HIGH, URGENT
├── certificate/
│   ├── CsrRequestRepository.java
│   ├── CsrRequestTransitionRepository.java
│   ├── WorkflowConfigRepository.java
│   ├── ApprovalMatrixRepository.java
│   └── dto/csr/
│       ├── CsrSubmitRequest.java
│       ├── CsrRequestDto.java
│       ├── WorkflowConfigDto.java
│       ├── ApprovalMatrixDto.java
│       ├── DashboardSummaryDto.java
│       ├── CaSubmissionRequest.java       ← RA → WLCA
│       └── CaCallbackRequest.java         ← WLCA → RA
└── event/
    └── CsrStatusChangedEvent.java         ← Domain event (record)

raservice/
├── csr/
│   ├── CsrTransitionService.java          ← State machine + event publisher
│   ├── ApprovalWorkflowService.java       ← Business logic (ZERO audit calls)
│   ├── WorkflowConfigService.java         ← Global config reader
│   ├── ApprovalMatrixService.java         ← Per-profile rule resolver
│   ├── CaIntegrationService.java          ← CA send + callback handler
│   ├── CsrApprovalController.java         ← 20+ endpoints
│   ├── CaCallbackController.java          ← CA callback endpoint
│   └── CsrAuditEventListener.java         ← Async audit (Observer pattern)
├── config/
│   ├── RaSecurityConfig.java              ← Role-based endpoint protection
│   └── AsyncConfig.java                   ← Audit thread pool
└── resources/db/migration/
    ├── V9__create_csr_requests.sql
    ├── V10__create_csr_request_transitions.sql
    ├── V11__seed_approval_error_codes.sql
    ├── V12__create_workflow_config.sql
    ├── V13__create_approval_matrix.sql
    └── V14__add_ca_integration_columns.sql
```

---

## 18. Design Patterns Used

```
Pattern              Kahan Use Hua                    Kyun
──────────────────────────────────────────────────────────────────────
Observer             CsrStatusChangedEvent +          Audit decouple
                     CsrAuditEventListener            Business se

State Machine        CsrStatusTransition              Invalid transitions
                     (EnumMap based)                   block karo

Template Method      AbstractSecuredController         Common audit context
                                                       resolve karo

Strategy             ApprovalMatrixService             Per-profile rules
                     (profile-based resolution)        DB se aayein

Builder              All DTOs (@Builder)               Clean object creation

Repository           JpaRepository + Specifications    Dynamic queries
```

---

## 19. Backward Compatibility

```
✅ Purana app_config table UNTOUCHED — LDAP, mail, CA settings safe
✅ Purana CaClient (sync flow) still works — naya async OPTIONAL hai
✅ Sab existing 18 endpoints UNCHANGED — sirf naye add hue
✅ Purana certificate_requests table UNTOUCHED — naya csr_requests alag
✅ ra.ca.base-url configure nahi kiya to log warning, crash nahi
✅ Security config mein pehle ke sab rules waise ke waise
```
