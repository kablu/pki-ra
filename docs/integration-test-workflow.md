# CSR Approval Workflow — Integration Test Plan

## Test Environment
- **Branch:** `csr-approval-dev`
- **Profile:** `h2` (in-memory database)
- **Auth:** HTTP Basic (admin/admin123)

---

## TEST-1: SINGLE MODE — Full Happy Path

### Setup
```
PUT /api/ra/admin/approval-matrix/1  (global default)
Body: { "approvalMode": "SINGLE", ... }
```

### Flow
```
Step 1: Client submits CSR
  POST /api/ra/requests
  Body: { "pkcs10": "...", "clientTxnId": "TXN-001", "csrProfile": "TLS_SERVER", ... }
  → 201: status=RECEIVED, requestId=RA-REQ-2026-000001

  Transition: NULL → RECEIVED
  Audit (async): CSR_RECEIVED by system

Step 2: System validates (automatic)
  → status=VALIDATED → SUBMITTED

  Transitions: RECEIVED → VALIDATED → SUBMITTED
  Audit (async): CSR_VALIDATED, CSR_SUBMITTED by system

Step 3: Operator picks up
  POST /api/ra/requests/1/pickup (auth: operator1)
  → 200: status=IN_REVIEW, assignedTo=operator1

  Transition: SUBMITTED → IN_REVIEW
  Audit (async): CSR_IN_REVIEW by operator1 (ROLE_OPERATOR)

Step 4: Operator approves directly (SINGLE mode)
  POST /api/ra/requests/1/approve (auth: operator1)
  Body: { "remarks": "CSR verified. All checks passed." }
  → 200: status=APPROVED

  Transition: IN_REVIEW → APPROVED
  Audit (async): CSR_APPROVED by operator1

Step 5: CA signs → ISSUED (automatic)
  Transition: APPROVED → ISSUED
  Audit (async): CSR_ISSUED by system

Step 6: Verify transition history
  GET /api/ra/requests/1/history
  → 5+ entries: RECEIVED → VALIDATED → SUBMITTED → IN_REVIEW → APPROVED → ISSUED
```

### Assertions
- [ ] Each transition recorded in `csr_request_transitions` table
- [ ] Each transition generated an async audit event
- [ ] `audit_log` has entries for ALL transitions
- [ ] API response was NOT blocked by audit writes
- [ ] Thread names in logs show `audit-` prefix (async thread pool)

---

## TEST-2: SINGLE MODE — Reject → Admin Reassigns → Approve

### Flow
```
Step 1-3: Same as TEST-1 (submit → validate → pickup)

Step 4: Operator rejects
  POST /api/ra/requests/2/reject (auth: operator1)
  Body: { "remarks": "Domain not in approved list." }
  → status=REJECTED

  Transition: IN_REVIEW → REJECTED
  Audit: CSR_REJECTED by operator1

Step 5: Admin sees rejected in dashboard
  GET /api/ra/requests/summary → rejected: 1
  GET /api/ra/requests?status=REJECTED → shows request #2

Step 6: Admin reassigns to different operator
  POST /api/ra/requests/2/assign (auth: admin)
  Body: { "operatorId": 7 }
  → status=IN_REVIEW (back in pipeline)

  Transition: REJECTED → IN_REVIEW
  Audit: CSR_IN_REVIEW by admin (ADMIN)

Step 7: New operator approves
  POST /api/ra/requests/2/approve (auth: operator2)
  → status=APPROVED → ISSUED

Step 8: Verify full history
  → RECEIVED → VALIDATED → SUBMITTED → IN_REVIEW → REJECTED → IN_REVIEW → APPROVED → ISSUED
  (8 transitions — full trail maintained)
```

---

## TEST-3: SINGLE MODE — Return → Admin Closes

### Flow
```
Step 1-3: Submit → validate → pickup

Step 4: Operator returns to Admin
  POST /api/ra/requests/3/return (auth: operator1)
  Body: { "reason": "Key size too small." }
  → status=RETURNED

  Transition: IN_REVIEW → RETURNED
  Audit: CSR_RETURNED by operator1

Step 5: Admin closes permanently
  POST /api/ra/requests/3/close (auth: admin)
  Body: { "reason": "Request cannot be fulfilled." }
  → status=CLOSED (true final state)

  Transition: RETURNED → CLOSED
  Audit: CSR_CLOSED by admin

Step 6: Verify no further action possible
  POST /api/ra/requests/3/approve → 400 (CLOSED is terminal)
  POST /api/ra/requests/3/assign → 400 (CLOSED is terminal)
```

---

## TEST-4: Admin Switches SINGLE → DUAL

### Setup
```
PUT /api/ra/admin/approval-matrix/1
Body: { "approvalMode": "DUAL", ... }
```

---

## TEST-5: DUAL MODE — Full Happy Path (Maker + Checker)

### Flow
```
Step 1-2: Client submits CSR, validated → SUBMITTED

Step 3: Maker picks up
  POST /api/ra/requests/5/pickup (auth: operator1)
  → status=IN_REVIEW, approvalModeAtPickup=DUAL

Step 4: Maker tries to approve directly → BLOCKED
  POST /api/ra/requests/5/approve (auth: operator1)
  → 400: "DUAL mode active — use /review first"

Step 5: Maker submits review with remarks
  POST /api/ra/requests/5/review (auth: operator1)
  Body: { "remarks": "CSR verified. DN matches CMDB. Recommend approval." }
  → status=REVIEWED

  Transition: IN_REVIEW → REVIEWED
  Audit: CSR_REVIEWED by operator1

Step 6: Same operator tries to accept → BLOCKED (separation of duties)
  POST /api/ra/requests/5/accept (auth: operator1)
  → 403: "Maker cannot act as Checker"

Step 7: Different operator (Checker) accepts
  POST /api/ra/requests/5/accept (auth: operator2)
  Body: { "remarks": "Agreed with Maker." }
  → status=APPROVED → ISSUED

  Transitions: REVIEWED → APPROVED → ISSUED
  Audit: CSR_APPROVED by operator2 (Checker)

Step 8: Verify history
  → RECEIVED → VALIDATED → SUBMITTED → IN_REVIEW → REVIEWED → APPROVED → ISSUED
  (7 transitions)
  → Maker = operator1, Checker = operator2 (different persons ✅)
```

---

## TEST-6: DUAL MODE — Checker Rejects → Admin Reassigns

### Flow
```
Step 1-5: Submit → pickup → Maker reviews → REVIEWED

Step 6: Checker rejects
  POST /api/ra/requests/6/reject (auth: operator2)
  Body: { "remarks": "Domain issue." }
  → status=REJECTED

Step 7: Admin reassigns
  POST /api/ra/requests/6/assign (auth: admin)
  → status=IN_REVIEW (new Maker)

Step 8: New flow: review → accept → issued
  → Full history: ...REVIEWED → REJECTED → IN_REVIEW → REVIEWED → APPROVED → ISSUED
```

---

## TEST-7: DUAL MODE — Maker Returns → Admin Closes

### Flow
```
Step 1-3: Submit → pickup (Maker)

Step 4: Maker returns
  POST /api/ra/requests/7/return (auth: operator1)
  → status=RETURNED

Step 5: Admin closes
  POST /api/ra/requests/7/close (auth: admin)
  → status=CLOSED
```

---

## TEST-8: Requester Cannot Be Maker

```
Step 1: user1 submits CSR
Step 2: user1 tries to pickup → 403 "Requester cannot be Maker"
```

---

## TEST-9: Per-Profile Mode Override

```
Step 1: Admin sets TLS_SERVER=SINGLE, CODE_SIGNING=DUAL

Step 2: Submit TLS_SERVER CSR → operator can approve directly ✅
Step 3: Submit CODE_SIGNING CSR → operator must review, Checker must accept ✅

Same system, different profiles, different rules — all from approval_matrix.
```

---

## TEST-10: Async Audit Verification

```
For EVERY test above, verify:

1. audit_log table has entries for ALL transitions
2. Thread name in logs = "audit-1" or "audit-2" (background thread)
3. API response time is NOT affected by audit writes
4. If audit DB is slow → API still responds fast
5. Transition history (csr_request_transitions) = synchronous ✅
   Audit log (audit_log) = asynchronous ✅ (via event)
```

---

## Async Audit Event Flow (verified in every test)

```
Business Thread                    Audit Thread (async)
===============                    ====================

1. Controller receives request
2. Service validates + transitions
3. TransitionService:
   a. Validates state machine
   b. Saves transition history (sync)
   c. Updates request status (sync)
   d. Publishes CsrStatusChangedEvent ──→ (queued)
4. Transaction COMMITS
5. HTTP 200 returned to client        6. @TransactionalEventListener
   (FAST — no audit wait)                (AFTER_COMMIT fires)
                                      7. CsrAuditEventListener
                                         runs on "audit-N" thread
                                      8. auditLogService.logSuccess()
                                         writes to audit_log table
                                         (separate REQUIRES_NEW txn)
```
