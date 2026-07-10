# End-to-End Integration Test — Complete RA Approval Workflow

**Branch:** `csr-approval-dev`  
**Date:** 2026-06-26  
**Scope:** Admin config → CSR intake → Approval (SINGLE + DUAL) → CA submission → Callback → Certificate download

---

## PRE-REQUISITE: System State

```
Users seeded:
  admin       (ROLE_ADMIN)    — configures workflow, monitors
  operator1   (ROLE_OPERATOR) — acts as Maker or single approver
  operator2   (ROLE_OPERATOR) — acts as Checker
  rahul       (ROLE_USER)     — submits CSR (client/requester)

Tables ready:
  csr_requests               — main CSR storage
  csr_request_transitions    — transition history
  workflow_config             — global workflow settings
  approval_matrix             — per-profile approval rules
  audit_log                   — async audit entries
```

---

## PHASE 1: ADMIN CONFIGURES SINGLE OPERATOR MODE

### Step 1.1 — Admin reads current config
```
GET /api/ra/admin/config/workflow
Auth: admin / admin123

Response 200:
{
  "approvalMode": "DUAL",
  "assignmentMode": "HYBRID",
  "maxPendingPerOperator": 10,
  "makerCanReturnToAdmin": true,
  "requireRemarks": true
}
```

### Step 1.2 — Admin reads approval matrix
```
GET /api/ra/admin/approval-matrix
Auth: admin / admin123

Response 200:
[
  { "id": 1, "csrProfile": null,              "approvalMode": "DUAL",   ... },
  { "id": 2, "csrProfile": "TLS_SERVER",      "approvalMode": "SINGLE", ... },
  { "id": 3, "csrProfile": "TLS_CLIENT",      "approvalMode": "SINGLE", ... },
  { "id": 4, "csrProfile": "CODE_SIGNING",    "approvalMode": "DUAL",   ... },
  { "id": 5, "csrProfile": "SMIME",           "approvalMode": "SINGLE", ... },
  { "id": 6, "csrProfile": "DOCUMENT_SIGNING","approvalMode": "DUAL",   ... }
]
```

### Step 1.3 — Admin sets TLS_SERVER to SINGLE mode (already default)
```
PUT /api/ra/admin/approval-matrix/2
Auth: admin / admin123
Body:
{
  "approvalMode": "SINGLE",
  "makerRole": "ROLE_OPERATOR",
  "checkerRole": "ROLE_OPERATOR",
  "adminCanBeMaker": true,
  "adminCanBeChecker": false,
  "minRemarksLength": 0,
  "autoApprove": false,
  "maxPendingPerOperator": 15
}

Response 200: updated rule

Audit (async): MATRIX_UPDATE by admin
```

### ✅ Checkpoint: SINGLE mode configured for TLS_SERVER

---

## PHASE 2: CLIENT SUBMITS CSR (TLS_SERVER — SINGLE MODE)

### Step 2.1 — Client submits CSR
```
POST /api/ra/requests
Auth: rahul / rahul123
Body:
{
  "pkcs10": "-----BEGIN CERTIFICATE REQUEST-----\nMIIC...\n-----END CERTIFICATE REQUEST-----",
  "clientTxnId": "TXN-2026-0001",
  "csrProfile": "TLS_SERVER",
  "requestorName": "Rahul Sharma",
  "requestorEmail": "rahul@acme.com",
  "requestorDepartment": "Engineering",
  "validityDays": 365,
  "subjectAltNames": "dns:api.acme.com,dns:api2.acme.com",
  "purpose": "Production API server certificate",
  "priority": "NORMAL",
  "additionalAttributes": {
    "projectCode": "PRJ-BILLING",
    "environment": "PRODUCTION"
  }
}

Response 201:
{
  "requestId": "RA-REQ-2026-000001",
  "clientTxnId": "TXN-2026-0001",
  "status": "RECEIVED",
  "subjectDn": "CN=api.acme.com,O=Acme Corp,C=IN",
  "csrProfile": "TLS_SERVER",
  "message": "CSR received. Validation in progress."
}
```

### Step 2.2 — System validates (automatic, internal)
```
Transition: RECEIVED → VALIDATED → SUBMITTED
(3 transitions recorded in csr_request_transitions)

Audit events (async, on audit-1/audit-2 threads):
  CSR_RECEIVED   by system
  CSR_VALIDATED   by system
  CSR_SUBMITTED   by system
```

### Step 2.3 — Verify request is in pool
```
GET /api/ra/requests/pool
Auth: operator1 / operator123

Response 200:
[{
  "id": 1,
  "requestId": "RA-REQ-2026-000001",
  "status": "SUBMITTED",
  "csrProfile": "TLS_SERVER",
  "requestorName": "Rahul Sharma",
  "subjectDn": "CN=api.acme.com,O=Acme Corp,C=IN"
}]
```

### Step 2.4 — Client tracks status via txn ID
```
GET /api/ra/requests/by-txn-id/TXN-2026-0001
Auth: rahul / rahul123

Response 200:
{ "requestId": "RA-REQ-2026-000001", "status": "SUBMITTED", ... }
```

### ✅ Checkpoint: CSR received, validated, in pool — ready for pickup

---

## PHASE 3: SINGLE MODE APPROVAL — OPERATOR APPROVES

### Step 3.1 — Operator picks up from pool
```
POST /api/ra/requests/1/pickup
Auth: operator1 / operator123

Response 200:
{
  "id": 1,
  "status": "IN_REVIEW",
  "assignedToUsername": "operator1",
  "approvalModeAtPickup": "SINGLE"
}

Transition: SUBMITTED → IN_REVIEW
Audit (async): CSR_IN_REVIEW by operator1 (ROLE_OPERATOR)
```

### Step 3.2 — Verify operator sees it in "my work"
```
GET /api/ra/requests/my-work
Auth: operator1 / operator123

Response 200:
[{ "id": 1, "status": "IN_REVIEW", "assignedToUsername": "operator1" }]
```

### Step 3.3 — Operator approves directly (SINGLE mode)
```
POST /api/ra/requests/1/approve
Auth: operator1 / operator123
Body: { "remarks": "CSR verified. Subject DN matches CMDB. SANs valid. Key RSA-4096." }

Response 200:
{
  "id": 1,
  "status": "APPROVED",
  "makerRemarks": "CSR verified. Subject DN matches CMDB..."
}

Transition: IN_REVIEW → APPROVED
Audit (async): CSR_APPROVED by operator1
```

### ✅ Checkpoint: SINGLE mode approval complete — request APPROVED

---

## PHASE 4: SEND APPROVED CSR TO EXTERNAL CA (WLCA)

### Step 4.1 — System sends to WLCA (automatic after APPROVED)
```
Internal: CaIntegrationService.sendToCa(requestId=1)

RA sends to WLCA:
POST https://wlca.example.com/api/ca/sign-async
Body:
{
  "requestId": "RA-REQ-2026-000001",
  "csrPem": "-----BEGIN CERTIFICATE REQUEST-----...",
  "csrProfile": "TLS_SERVER",
  "validityDays": 365,
  "subjectAltNames": "dns:api.acme.com,dns:api2.acme.com",
  "postBackUrl": "https://ra.acme.com/api/ra/callback/certificate"
}

WLCA Response 202:
{ "caTransactionId": "CA-TXN-2026-000042" }

Transition: APPROVED → SENT_TO_CA
Audit (async): CSR_SENT_TO_CA by system

DB updated:
  csr_requests.status = SENT_TO_CA
  csr_requests.ca_transaction_id = CA-TXN-2026-000042
  csr_requests.sent_to_ca_at = 2026-06-26T10:30:00Z
  csr_requests.post_back_url = https://ra.acme.com/api/ra/callback/certificate
```

### Step 4.2 — Admin monitors in dashboard
```
GET /api/ra/requests/summary
Auth: admin / admin123

Response 200:
{
  "submitted": 0,
  "inReview": 0,
  "approved": 0,
  "sentToCa": 1,      ← request is with CA
  "issued": 0,
  ...
}
```

### Step 4.3 — Client checks status
```
GET /api/ra/requests/by-txn-id/TXN-2026-0001
Response: { "status": "SENT_TO_CA", ... }
```

### ✅ Checkpoint: CSR sent to WLCA, waiting for callback

---

## PHASE 5: CA PROCESSES AND SENDS CERTIFICATE BACK

### Step 5.1 — WLCA posts certificate to RA callback
```
(This is WLCA calling RA — machine-to-machine, no user auth)

POST https://ra.acme.com/api/ra/callback/certificate
Body:
{
  "requestId": "RA-REQ-2026-000001",
  "caTransactionId": "CA-TXN-2026-000042",
  "status": "ISSUED",
  "certificatePem": "-----BEGIN CERTIFICATE-----\nMIID...\n-----END CERTIFICATE-----",
  "certificateChain": "-----BEGIN CERTIFICATE-----\n...end-entity...\n-----END CERTIFICATE-----\n-----BEGIN CERTIFICATE-----\n...intermediate...\n-----END CERTIFICATE-----\n-----BEGIN CERTIFICATE-----\n...root...\n-----END CERTIFICATE-----",
  "serialNumber": "4F2A1B3C9E8D7F60",
  "signatureAlgorithm": "SHA256withRSA",
  "subject": "CN=api.acme.com,O=Acme Corp,C=IN",
  "issuer": "CN=PKI Intermediate CA,O=Acme Corp,C=IN",
  "notBefore": "2026-06-26T00:00:00Z",
  "notAfter": "2027-06-26T23:59:59Z"
}

Response 200:
{
  "requestId": "RA-REQ-2026-000001",
  "status": "ISSUED"
}

Transition: SENT_TO_CA → ISSUED
Audit (async): CSR_ISSUED by system

DB updated:
  csr_requests.status = ISSUED
  csr_requests.ca_response_received_at = 2026-06-26T10:35:00Z
```

### ✅ Checkpoint: Certificate received from CA, request ISSUED

---

## PHASE 6: CLIENT DOWNLOADS CERTIFICATE

### Step 6.1 — Client checks status
```
GET /api/ra/requests/by-txn-id/TXN-2026-0001
Auth: rahul / rahul123

Response 200:
{
  "requestId": "RA-REQ-2026-000001",
  "clientTxnId": "TXN-2026-0001",
  "status": "ISSUED",
  "subjectDn": "CN=api.acme.com,O=Acme Corp,C=IN",
  "csrProfile": "TLS_SERVER",
  "assignedToUsername": "operator1",
  "makerRemarks": "CSR verified. Subject DN matches CMDB...",
  "approvalModeAtPickup": "SINGLE",
  "certificateId": 501
}
```

### Step 6.2 — Client downloads certificate
```
GET /api/ra/certificates/4F2A1B3C9E8D7F60/download?format=pem
Auth: rahul / rahul123

Response 200:
Content-Disposition: attachment; filename="4F2A1B3C9E8D7F60.pem"
Content-Type: application/x-pem-file

-----BEGIN CERTIFICATE-----
MIIDxTCCAq2gAwIBAgIJAE...
-----END CERTIFICATE-----
```

### Step 6.3 — Client downloads full chain
```
GET /api/ra/certificates/4F2A1B3C9E8D7F60/download?format=chain
Auth: rahul / rahul123

Response 200:
(end-entity + intermediate + root PEM concatenated)
```

### ✅ Checkpoint: SINGLE MODE COMPLETE — Certificate delivered to client

---

## PHASE 7: VERIFY COMPLETE TRANSITION HISTORY (SINGLE MODE)

### Step 7.1 — Admin views full history
```
GET /api/ra/requests/1/history
Auth: admin / admin123

Response 200:
[
  { "fromStatus": null,         "toStatus": "RECEIVED",    "changedByUsername": "system",    "changedByRole": "SYSTEM",   "remarks": "CSR received" },
  { "fromStatus": "RECEIVED",   "toStatus": "VALIDATED",   "changedByUsername": "system",    "changedByRole": "SYSTEM",   "remarks": "7-layer passed" },
  { "fromStatus": "VALIDATED",  "toStatus": "SUBMITTED",   "changedByUsername": "system",    "changedByRole": "SYSTEM",   "remarks": "Queued for approval" },
  { "fromStatus": "SUBMITTED",  "toStatus": "IN_REVIEW",   "changedByUsername": "operator1", "changedByRole": "ROLE_OPERATOR", "remarks": "Picked up from pool" },
  { "fromStatus": "IN_REVIEW",  "toStatus": "APPROVED",    "changedByUsername": "operator1", "changedByRole": "ROLE_OPERATOR", "remarks": "CSR verified..." },
  { "fromStatus": "APPROVED",   "toStatus": "SENT_TO_CA",  "changedByUsername": "system",    "changedByRole": "SYSTEM",   "remarks": "CSR sent to external CA" },
  { "fromStatus": "SENT_TO_CA", "toStatus": "ISSUED",      "changedByUsername": "system",    "changedByRole": "SYSTEM",   "remarks": "Certificate received. Serial: 4F2A..." }
]

Total: 7 transitions — complete audit trail ✅
```

### Step 7.2 — Verify audit_log entries (async)
```
GET /api/admin/audit-logs?resourceId=RA-REQ-2026-000001
Auth: admin / admin123

Response 200: 7 entries matching — one per transition
Each entry written by background "audit-N" thread (async ✅)
```

---

## PHASE 8: ADMIN SWITCHES TO DUAL MODE

### Step 8.1 — Admin updates global default to DUAL
```
PUT /api/ra/admin/approval-matrix/1
Auth: admin / admin123
Body:
{
  "csrProfile": null,
  "approvalMode": "DUAL",
  "makerRole": "ROLE_OPERATOR",
  "checkerRole": "ROLE_OPERATOR",
  "adminCanBeMaker": false,
  "adminCanBeChecker": false,
  "minRemarksLength": 5,
  "maxPendingPerOperator": 10
}

Response 200: updated
Audit: MATRIX_UPDATE by admin
```

### Step 8.2 — Admin also sets CODE_SIGNING to DUAL (already default)
```
GET /api/ra/admin/approval-matrix → verify CODE_SIGNING = DUAL ✅
```

### ✅ Checkpoint: DUAL mode active for CODE_SIGNING profile

---

## PHASE 9: CLIENT SUBMITS CODE_SIGNING CSR (DUAL MODE)

### Step 9.1 — Client submits CSR
```
POST /api/ra/requests
Auth: rahul / rahul123
Body:
{
  "pkcs10": "-----BEGIN CERTIFICATE REQUEST-----\n...",
  "clientTxnId": "TXN-2026-0002",
  "csrProfile": "CODE_SIGNING",
  "requestorName": "Rahul Sharma",
  "requestorEmail": "rahul@acme.com",
  "validityDays": 1095,
  "purpose": "Internal code signing for build pipeline"
}

Response 201:
{ "requestId": "RA-REQ-2026-000002", "status": "RECEIVED" }

Transitions: RECEIVED → VALIDATED → SUBMITTED
```

---

## PHASE 10: DUAL MODE — MAKER REVIEWS

### Step 10.1 — Maker picks up
```
POST /api/ra/requests/2/pickup
Auth: operator1 / operator123

Response 200:
{
  "status": "IN_REVIEW",
  "assignedToUsername": "operator1",
  "approvalModeAtPickup": "DUAL"
}

Transition: SUBMITTED → IN_REVIEW
```

### Step 10.2 — Maker tries to approve directly → BLOCKED
```
POST /api/ra/requests/2/approve
Auth: operator1 / operator123
Body: { "remarks": "Looks good" }

Response 400:
{
  "error": "DUAL mode active for profile CODE_SIGNING — use /review first, then Checker /accept"
}

No transition — correctly blocked ✅
```

### Step 10.3 — Maker submits review with remarks
```
POST /api/ra/requests/2/review
Auth: operator1 / operator123
Body:
{
  "remarks": "CSR verified. Code signing profile correct. Key RSA-4096 meets policy. Recommend approval."
}

Response 200:
{
  "status": "REVIEWED",
  "makerRemarks": "CSR verified. Code signing profile correct..."
}

Transition: IN_REVIEW → REVIEWED
Audit: CSR_REVIEWED by operator1
```

### ✅ Checkpoint: Maker reviewed — waiting for Checker

---

## PHASE 11: DUAL MODE — CHECKER ACCEPTS

### Step 11.1 — Same operator tries to accept → BLOCKED (separation of duties)
```
POST /api/ra/requests/2/accept
Auth: operator1 / operator123

Response 403:
{
  "error": "The operator who reviewed this request cannot accept or reject it. A different operator must perform the Checker role."
}

No transition — separation of duties enforced ✅
```

### Step 11.2 — Requester tries to accept → BLOCKED
```
POST /api/ra/requests/2/accept
Auth: rahul / rahul123

Response 403:
{
  "error": "Requester cannot be Maker or Checker"
}
```

### Step 11.3 — Different operator (Checker) accepts
```
POST /api/ra/requests/2/accept
Auth: operator2 / operator123
Body: { "remarks": "Agreed with Maker assessment. Code signing scope appropriate." }

Response 200:
{
  "status": "APPROVED",
  "checkerUsername": "operator2",
  "checkerDecision": "APPROVED",
  "checkerRemarks": "Agreed with Maker assessment..."
}

Transition: REVIEWED → APPROVED
Audit: CSR_APPROVED by operator2 (Checker)
```

### ✅ Checkpoint: DUAL mode approval complete — APPROVED

---

## PHASE 12: SEND TO CA → CALLBACK → ISSUED (same as SINGLE)

### Step 12.1 — System sends to WLCA
```
Transition: APPROVED → SENT_TO_CA
(Same flow as Phase 4)
```

### Step 12.2 — WLCA posts certificate back
```
POST /api/ra/callback/certificate
Body: { "requestId": "RA-REQ-2026-000002", "status": "ISSUED", ... }

Transition: SENT_TO_CA → ISSUED
```

### Step 12.3 — Client downloads certificate
```
GET /api/ra/requests/by-txn-id/TXN-2026-0002
→ status: ISSUED, certificateId: 502

GET /api/ra/certificates/{serial}/download?format=pem
→ certificate PEM file
```

### ✅ Checkpoint: DUAL MODE COMPLETE — Certificate delivered

---

## PHASE 13: VERIFY DUAL MODE TRANSITION HISTORY

```
GET /api/ra/requests/2/history

[
  { "from": null,         "to": "RECEIVED",    "by": "system",    "role": "SYSTEM" },
  { "from": "RECEIVED",   "to": "VALIDATED",   "by": "system",    "role": "SYSTEM" },
  { "from": "VALIDATED",  "to": "SUBMITTED",   "by": "system",    "role": "SYSTEM" },
  { "from": "SUBMITTED",  "to": "IN_REVIEW",   "by": "operator1", "role": "ROLE_OPERATOR" },
  { "from": "IN_REVIEW",  "to": "REVIEWED",    "by": "operator1", "role": "ROLE_OPERATOR", "remarks": "CSR verified..." },
  { "from": "REVIEWED",   "to": "APPROVED",    "by": "operator2", "role": "ROLE_OPERATOR", "remarks": "Agreed with Maker..." },
  { "from": "APPROVED",   "to": "SENT_TO_CA",  "by": "system",    "role": "SYSTEM" },
  { "from": "SENT_TO_CA", "to": "ISSUED",      "by": "system",    "role": "SYSTEM", "remarks": "Certificate received..." }
]

Total: 8 transitions
Maker = operator1, Checker = operator2 (different persons ✅)
Separation of duties verified ✅
```

---

## PHASE 14: NEGATIVE TEST CASES

### Test 14.1 — Duplicate CSR submission
```
POST /api/ra/requests (same clientTxnId TXN-2026-0001)
→ 409: "Client transaction ID already exists"
```

### Test 14.2 — CA callback for wrong status
```
POST /api/ra/callback/certificate
Body: { "requestId": "RA-REQ-2026-000001", "status": "ISSUED" }
→ 400: "Callback received but request is not in SENT_TO_CA status. Current: ISSUED"
```

### Test 14.3 — CA returns failure
```
POST /api/ra/callback/certificate
Body: {
  "requestId": "RA-REQ-2026-000003",
  "status": "FAILED",
  "failureReason": "CSR signature verification failed at CA"
}
→ 200: status = FAILED
→ Admin can retry later
```

### Test 14.4 — Admin closes rejected request
```
POST /api/ra/requests/4/close
Body: { "reason": "Domain permanently blocked" }
→ status = CLOSED (true final state)
→ No further actions possible
```

### Test 14.5 — Operator returns to Admin
```
POST /api/ra/requests/5/return
Body: { "reason": "Key size 1024 does not meet policy" }
→ status = RETURNED
→ Admin can reassign or close
```

---

## PHASE 15: VERIFY ASYNC AUDIT ACROSS ALL TESTS

```
For EVERY phase above, verify:

1. csr_request_transitions — synchronous, recorded in same transaction ✅
2. audit_log — asynchronous, written by CsrAuditEventListener ✅
3. Thread names in logs: "audit-1", "audit-2" (background pool) ✅
4. API response NOT delayed by audit writes ✅
5. Event published AFTER_COMMIT — if business txn rolls back,
   no orphan audit entries created ✅

Event flow:
  CsrTransitionService.transition()
    → saves to csr_request_transitions (sync)
    → publishes CsrStatusChangedEvent
    → transaction COMMITS
    → CsrAuditEventListener fires on "audit-N" thread
    → writes to audit_log (async, separate REQUIRES_NEW txn)
```

---

## COMPLETE STATUS FLOW VERIFIED

```
SINGLE MODE (TLS_SERVER):
  RECEIVED → VALIDATED → SUBMITTED → IN_REVIEW → APPROVED → SENT_TO_CA → ISSUED
  (7 transitions, 1 operator, 7 audit entries)

DUAL MODE (CODE_SIGNING):
  RECEIVED → VALIDATED → SUBMITTED → IN_REVIEW → REVIEWED → APPROVED → SENT_TO_CA → ISSUED
  (8 transitions, 2 operators, 8 audit entries, Maker ≠ Checker ✅)

REJECT → REASSIGN:
  ... → IN_REVIEW → REJECTED → IN_REVIEW → APPROVED → SENT_TO_CA → ISSUED
  (full trail preserved)

RETURN → CLOSE:
  ... → IN_REVIEW → RETURNED → CLOSED
  (terminal state, no further action)

CA FAILURE:
  ... → APPROVED → SENT_TO_CA → FAILED
  (Admin can retry → APPROVED → SENT_TO_CA → ISSUED)
```

---

## SUMMARY TABLE

| Phase | Action | Status Change | Actor | Async Audit |
|-------|--------|---------------|-------|-------------|
| 2.1 | Client submits CSR | → RECEIVED | rahul | CSR_RECEIVED |
| 2.2 | System validates | → VALIDATED → SUBMITTED | system | CSR_VALIDATED, CSR_SUBMITTED |
| 3.1 | Operator picks up | → IN_REVIEW | operator1 | CSR_IN_REVIEW |
| 3.3 | Operator approves (SINGLE) | → APPROVED | operator1 | CSR_APPROVED |
| 4.1 | System sends to CA | → SENT_TO_CA | system | CSR_SENT_TO_CA |
| 5.1 | CA sends cert back | → ISSUED | system | CSR_ISSUED |
| 6.2 | Client downloads cert | (no change) | rahul | — |
| 10.1 | Maker picks up | → IN_REVIEW | operator1 | CSR_IN_REVIEW |
| 10.3 | Maker reviews (DUAL) | → REVIEWED | operator1 | CSR_REVIEWED |
| 11.3 | Checker accepts (DUAL) | → APPROVED | operator2 | CSR_APPROVED |
| 12.1 | System sends to CA | → SENT_TO_CA | system | CSR_SENT_TO_CA |
| 12.2 | CA sends cert back | → ISSUED | system | CSR_ISSUED |
