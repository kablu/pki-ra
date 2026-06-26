# Approval Workflow — Implementation Change Log

**Branch:** `csr-approval-dev`  
**Base:** `ra-csr-dev`  
**Date:** 2026-06-25  
**Total Files:** 40+ (code + docs + migrations)

---

## 1. Enums (common module)

| # | Class | Full Path |
|---|-------|-----------|
| 1 | `CsrStatus` | `subprojects/common/src/main/java/com/pki/ra/common/model/enums/CsrStatus.java` |
| 2 | `CsrStatusTransition` | `subprojects/common/src/main/java/com/pki/ra/common/model/enums/CsrStatusTransition.java` |
| 3 | `ApprovalMode` | `subprojects/common/src/main/java/com/pki/ra/common/model/enums/ApprovalMode.java` |
| 4 | `AssignmentMode` | `subprojects/common/src/main/java/com/pki/ra/common/model/enums/AssignmentMode.java` |
| 5 | `CsrProfile` | `subprojects/common/src/main/java/com/pki/ra/common/model/enums/CsrProfile.java` |
| 6 | `RequestPriority` | `subprojects/common/src/main/java/com/pki/ra/common/model/enums/RequestPriority.java` |

## 2. JPA Entities (common module)

| # | Class | Full Path |
|---|-------|-----------|
| 7 | `CsrRequest` | `subprojects/common/src/main/java/com/pki/ra/common/model/CsrRequest.java` |
| 8 | `CsrRequestTransition` | `subprojects/common/src/main/java/com/pki/ra/common/model/CsrRequestTransition.java` |
| 9 | `WorkflowConfig` | `subprojects/common/src/main/java/com/pki/ra/common/model/WorkflowConfig.java` |

## 3. Repositories (common module)

| # | Class | Full Path |
|---|-------|-----------|
| 10 | `CsrRequestRepository` | `subprojects/common/src/main/java/com/pki/ra/common/certificate/CsrRequestRepository.java` |
| 11 | `CsrRequestTransitionRepository` | `subprojects/common/src/main/java/com/pki/ra/common/certificate/CsrRequestTransitionRepository.java` |
| 12 | `WorkflowConfigRepository` | `subprojects/common/src/main/java/com/pki/ra/common/certificate/WorkflowConfigRepository.java` |

## 4. DTOs (common module)

| # | Class | Full Path |
|---|-------|-----------|
| 11 | `CsrSubmitRequest` | `subprojects/common/src/main/java/com/pki/ra/common/certificate/dto/csr/CsrSubmitRequest.java` |
| 12 | `RemarksRequest` | `subprojects/common/src/main/java/com/pki/ra/common/certificate/dto/csr/RemarksRequest.java` |
| 13 | `ReturnRequest` | `subprojects/common/src/main/java/com/pki/ra/common/certificate/dto/csr/ReturnRequest.java` |
| 14 | `AssignRequest` | `subprojects/common/src/main/java/com/pki/ra/common/certificate/dto/csr/AssignRequest.java` |
| 15 | `CloseRequest` | `subprojects/common/src/main/java/com/pki/ra/common/certificate/dto/csr/CloseRequest.java` |
| 16 | `WorkflowConfigDto` | `subprojects/common/src/main/java/com/pki/ra/common/certificate/dto/csr/WorkflowConfigDto.java` |
| 17 | `CsrRequestDto` | `subprojects/common/src/main/java/com/pki/ra/common/certificate/dto/csr/CsrRequestDto.java` |
| 18 | `DashboardSummaryDto` | `subprojects/common/src/main/java/com/pki/ra/common/certificate/dto/csr/DashboardSummaryDto.java` |

## 5. Utility (common module)

| # | Class | Full Path |
|---|-------|-----------|
| 19 | `RequestIdGenerator` | `subprojects/common/src/main/java/com/pki/ra/common/util/RequestIdGenerator.java` |

## 6. Services (raservice module)

| # | Class | Full Path |
|---|-------|-----------|
| 20 | `CsrTransitionService` | `subprojects/raservice/src/main/java/com/pki/ra/raservice/csr/CsrTransitionService.java` |
| 21 | `WorkflowConfigService` | `subprojects/raservice/src/main/java/com/pki/ra/raservice/csr/WorkflowConfigService.java` |
| 22 | `ApprovalWorkflowService` | `subprojects/raservice/src/main/java/com/pki/ra/raservice/csr/ApprovalWorkflowService.java` |

## 7. Controller (raservice module)

| # | Class | Full Path |
|---|-------|-----------|
| 23 | `CsrApprovalController` | `subprojects/raservice/src/main/java/com/pki/ra/raservice/csr/CsrApprovalController.java` |

## 8. Security Config (raservice module)

| # | Class | Full Path |
|---|-------|-----------|
| 24 | `RaSecurityConfig` | `subprojects/raservice/src/main/java/com/pki/ra/raservice/config/RaSecurityConfig.java` |

## 9. Error Codes (raservice module)

| # | Class | Full Path |
|---|-------|-----------|
| 25 | `RaErrorCode` (updated) | `subprojects/raservice/src/main/java/com/pki/ra/raservice/error/RaErrorCode.java` |

## 10. Flyway Migrations (raservice module)

| # | File | Full Path |
|---|------|-----------|
| 26 | `V9__create_csr_requests.sql` | `subprojects/raservice/src/main/resources/db/migration/V9__create_csr_requests.sql` |
| 27 | `V10__create_csr_request_transitions.sql` | `subprojects/raservice/src/main/resources/db/migration/V10__create_csr_request_transitions.sql` |
| 28 | `V11__seed_approval_error_codes.sql` | `subprojects/raservice/src/main/resources/db/migration/V11__seed_approval_error_codes.sql` |

## 11. Architecture Documents

| # | File | Full Path |
|---|------|-----------|
| 29 | `RA_Approval_Workflow_Architecture.txt` | `docs/RA_Approval_Workflow_Architecture.txt` |
| 30 | `RA_Approval_Workflow_Architecture.txt` | `docs/final/develop/RA_Approval_Workflow_Architecture.txt` |
| 31 | `RA_Approval_Workflow_Architecture.txt` | `docs/req/RA_Approval_Workflow_Architecture.txt` |
| 32 | `RA_Approval_Workflow_Architecture.txt` | `docs/master/ra-csr-dev-final/RA_Approval_Workflow_Architecture.txt` |

---

## REST API Endpoints (18)

| # | Method | Path | Purpose | Auth |
|---|--------|------|---------|------|
| 1 | `GET` | `/api/ra/admin/config/workflow` | Get workflow config | ADMIN |
| 2 | `GET` | `/api/ra/requests/{id}` | Get by internal ID | ADMIN/OPER/AUDITOR |
| 3 | `GET` | `/api/ra/requests/by-request-id/{reqId}` | Get by RA-REQ ID | ADMIN/OPER/AUDITOR |
| 4 | `GET` | `/api/ra/requests/by-txn-id/{txnId}` | Get by client txn ID | ADMIN/OPER/AUDITOR |
| 5 | `GET` | `/api/ra/requests/{id}/history` | Transition history | ADMIN/AUDITOR |
| 6 | `GET` | `/api/ra/requests/summary` | Dashboard counts | ADMIN |
| 7 | `GET` | `/api/ra/requests` | List/filter requests | ADMIN/OPER/AUDITOR |
| 8 | `POST` | `/api/ra/requests/{id}/assign` | Assign to operator | ADMIN |
| 9 | `POST` | `/api/ra/requests/{id}/close` | Close permanently | ADMIN |
| 10 | `GET` | `/api/ra/requests/pool` | Available for pickup | ADMIN/OPER |
| 11 | `GET` | `/api/ra/requests/my-work` | My picked-up requests | ADMIN/OPER |
| 12 | `POST` | `/api/ra/requests/{id}/pickup` | Pick up from pool | ADMIN/OPER |
| 13 | `POST` | `/api/ra/requests/{id}/return` | Return to Admin | ADMIN/OPER |
| 14 | `POST` | `/api/ra/requests/{id}/approve` | Approve (SINGLE) | ADMIN/OPER |
| 15 | `POST` | `/api/ra/requests/{id}/reject` | Reject (SINGLE) | ADMIN/OPER |
| 16 | `POST` | `/api/ra/requests/{id}/review` | Maker remarks (DUAL) | ADMIN/OPER |
| 17 | `GET` | `/api/ra/requests/pending-check` | Reviewed for Checker | ADMIN/OPER |
| 18 | `POST` | `/api/ra/requests/{id}/accept` | Checker accepts (DUAL) | ADMIN/OPER |

---

## Error Codes Added (13)

| Code | HTTP | Message |
|------|------|---------|
| `PKI_APR_001` | 400 | Request not in correct status |
| `PKI_APR_002` | 403 | Cannot process own request |
| `PKI_APR_003` | 403 | Admin only action |
| `PKI_APR_004` | 403 | Not assigned operator |
| `PKI_APR_005` | 403 | Maker cannot be Checker (separation of duties) |
| `PKI_APR_006` | 403 | Requestor cannot be Maker or Checker |
| `PKI_APR_007` | 404 | Operator not found |
| `PKI_APR_008` | 400 | Remarks required |
| `PKI_APR_009` | 409 | Already picked up |
| `PKI_APR_010` | 400 | Return reason required |
| `PKI_APR_011` | 400 | Invalid workflow config |
| `PKI_APR_012` | 400 | Mode-blocked API |
| `PKI_APR_013` | 400 | Max pending limit reached |

---

## Design Patterns Applied

| Pattern | Where |
|---------|-------|
| State Machine | `CsrStatusTransition` — single source of truth for all valid transitions |
| Strategy | `WorkflowConfigService` — switches SINGLE/DUAL behavior at runtime |
| Template Method | `AbstractSecuredController` — audit context resolution |
| Single Responsibility | `CsrTransitionService` handles only transitions, `ApprovalWorkflowService` handles only business logic |
| Open/Closed | New status = add enum value; new profile = add enum value |
| Specification | `JpaSpecificationExecutor` on `CsrRequestRepository` for dynamic Admin filters |
| Builder | All DTOs use `@Builder` for clean construction |
| Immutable Record | `CsrRequestTransition` — insert only, never updated |

---

## Commit History

| # | Commit | Message |
|---|--------|---------|
| 1 | `0d4ef40` | docs: copy approval workflow to docs/req/ on csr-approval-dev branch |
| 2 | `537fe31` | feat(approval): Phase 1 — core entities, enums, state machine, migrations |
| 3 | `6856317` | feat(approval): Phase 2 — services, controller, DTOs for approval workflow |
| 4 | `edda91f` | feat(security): role-based endpoint protection for approval workflow |
| 5 | `f8823ad` | feat(error-catalog): seed 13 approval workflow error codes |
