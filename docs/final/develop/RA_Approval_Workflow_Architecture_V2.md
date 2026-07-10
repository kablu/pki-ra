```
Internal Technical Specification                            Kablu Mandal
Document ID: PKI-RA-APPR-001                                   Magellan
Version: 2.0                                               30 June 2026
Category: Architecture Design
Status: Draft — For Review

              RA APPROVAL WORKFLOW ARCHITECTURE
              Configurable Maker-Checker Model
              Enterprise Registration Authority

================================================================================
Status of This Memo

   This document is an internal technical specification for the pki-ra
   (Enterprise Registration Authority) project, module: raservice.

   This document defines the approval workflow architecture for CSR
   processing within the RA system. It is intended for architects,
   developers, and reviewers involved in the design and implementation
   of the raservice module.

   Distribution: Internal — Magellan Engineering Team

================================================================================
Change Log

   Version  Date         Author         Summary
   -------  -----------  -------------  ----------------------------------------
   0.1      2026-06-25   Kablu Mandal   Initial draft
   1.0      2026-06-30   Kablu Mandal   Revised per architect review (Sebastian)
   2.0      2026-07-01   Kablu Mandal   Reformatted to RFC-style structure;
                                        normative language applied; Security
                                        Considerations section added

================================================================================
```

# RA Approval Workflow Architecture

---

## Abstract

   This document specifies the approval workflow architecture for the
   Enterprise Registration Authority (RA) system.  It defines the
   configurable Maker-Checker model, database schema, REST API design,
   state machine, per-profile workflow configuration, CA integration
   lifecycle, and security considerations for CSR approval and
   certificate issuance.

   This document covers the approval workflow only.  The CSR request
   payload, field-level validations, PKCS#10 vs CRMF format handling,
   subjectAltName cross-verification, and additionalAttributes schema
   are out of scope and will be addressed in a companion document:

      RA_CSR_Request_Payload_and_Validation.md

   The approval workflow begins after a CSR has been received,
   validated, and placed in SUBMITTED status by the validation engine.

---

## Table of Contents

```
1.  Introduction  ..................................................  1
    1.1  Purpose  ..................................................  1
    1.2  Scope  ....................................................  1
    1.3  Entry Condition  ..........................................  1
2.  Conventions and Terminology  ...................................  2
    2.1  RFC 2119 Keywords  ........................................  2
    2.2  Definitions  ..............................................  2
3.  Pre-Approval Status Flow  ......................................  3
4.  Actors and Roles  ..............................................  4
5.  Workflow Configuration — Per-Profile Model  ....................  5
    5.1  Design Principles  ........................................  5
    5.2  Database Tables  ..........................................  5
    5.3  Example Configuration  ....................................  7
    5.4  Admin API for Configuration Management  ...................  8
    5.5  Runtime Resolution  .......................................  9
6.  State Machine  .................................................  10
    6.1  SINGLE Mode  ..............................................  10
    6.2  DUAL Mode  ................................................  10
    6.3  State Definitions  ........................................  11
    6.4  Valid Transitions  ........................................  12
7.  Detailed Flow — SINGLE Mode with Self-Pickup  ..................  13
8.  Detailed Flow — DUAL Mode with Self-Pickup  ....................  18
9.  Database Schema  ...............................................  24
    9.1  csr_requests  .............................................  24
    9.2  csr_requestor_info  .......................................  26
    9.3  csr_approval_workflow  ....................................  27
    9.4  certificate_request_transitions  ..........................  28
10. REST API Reference  ............................................  29
    10.1  Workflow Configuration Management  .......................  29
    10.2  CSR Submission and Lookup  ...............................  29
    10.3  Admin Dashboard and Actions  .............................  30
    10.4  Operator Actions  ........................................  30
    10.5  Certificate Endpoints  ...................................  31
    10.6  CA Callback Endpoint  ....................................  31
11. Record Visibility  .............................................  32
12. Transition History  ............................................  33
13. CA Integration and Issuance States  ............................  34
    13.1  CA Dispatch Flow  ........................................  34
    13.2  Three Expected CA Results  ...............................  34
    13.3  Admin Retry on FAILED  ...................................  35
14. Mode-Switch Rules  .............................................  36
15. Role and Permission Matrix  ....................................  37
16. Admin Dashboard  ...............................................  38
17. Audit Log Events  ..............................................  39
18. Error Codes  ...................................................  40
19. Security Considerations  .......................................  41
20. Testing Plan  ..................................................  43
21. References  ....................................................  45
22. Authors' Addresses  ............................................  45
```

---

## 1.  Introduction

### 1.1  Purpose

   This specification defines the approval workflow that governs how
   Certificate Signing Requests (CSRs) are reviewed and approved within
   the Enterprise Registration Authority (RA) system.

   The workflow supports two approval modes — SINGLE operator and DUAL
   operator (Maker-Checker) — and is configurable independently per
   certificate profile, allowing different risk profiles to follow
   different approval processes without system-level reconfiguration.

### 1.2  Scope

   This document covers:

   -  Approval workflow state machine (SUBMITTED through ISSUED)
   -  Per-profile workflow configuration model
   -  Database schema for request, requestor, and approval data
   -  REST API for all workflow operations
   -  CA integration lifecycle including async issuance
   -  Security considerations for the approval pipeline

   This document does NOT cover:

   -  CSR request payload structure and field definitions
   -  PKCS#10 or CRMF format handling
   -  subjectAltName validation logic
   -  additionalAttributes schema
   -  Active Directory authentication flow

   These topics are addressed in:
   RA_CSR_Request_Payload_and_Validation.md

### 1.3  Entry Condition

   The approval workflow operates exclusively on requests in SUBMITTED
   status.  Requests that fail validation MUST NOT enter the approval
   pipeline.

```
   CSR received by RA
          |
          v
   Validation Engine runs
          |
          +---[PASS]---> status = SUBMITTED  <-- Workflow starts here
          |
          +---[FAIL]---> status = VALIDATION_FAILED  (pipeline exit)
```

---

## 2.  Conventions and Terminology

### 2.1  RFC 2119 Keywords

   The key words "MUST", "MUST NOT", "REQUIRED", "SHALL", "SHALL NOT",
   "SHOULD", "SHOULD NOT", "RECOMMENDED", "MAY", and "OPTIONAL" in
   this document are to be interpreted as described in RFC 2119.

### 2.2  Definitions

   The following terms are used throughout this document:

   **CSR**
      Certificate Signing Request.  A PKCS#10 or CRMF formatted
      request for a digital certificate.

   **RA**
      Registration Authority.  The system that receives, validates,
      and manages approval of CSRs before forwarding to a CA.

   **CA**
      Certificate Authority.  The system that signs certificates.

   **Maker**
      The operator who picks up a CSR request and performs the first
      review.  In SINGLE mode, the Maker is also the final approver.
      In DUAL mode, the Maker submits a recommendation only.

   **Checker**
      In DUAL mode, the second operator who reviews the Maker's
      recommendation and makes the final approval or rejection
      decision.  The Checker MUST NOT be the same person as the Maker
      for the same request.

   **Self-Pickup**
      An assignment strategy where operators independently select
      requests from a shared pool.

   **Admin-Assign**
      An assignment strategy where an administrator explicitly assigns
      each request to a specific operator.

   **Workflow Configuration**
      A named, reusable object that specifies the approval mode,
      assignment strategy, and operational parameters for a set of
      certificate profiles.

   **Profile Mapping**
      A binding between a certificate profile and a workflow
      configuration, with effective date tracking.

---

## 3.  Pre-Approval Status Flow

   These statuses are managed automatically by the system before the
   approval workflow begins.  They are presented here for completeness
   and to define the boundary where the approval workflow starts.

```
   [RECEIVED]
       |
       v
   Validation Engine
       |
       +---[PASS]---> [VALIDATED] ---> [SUBMITTED]  <- workflow starts
       |
       +---[FAIL]---> [VALIDATION_FAILED]  (terminal for this submission)
```

   Status definitions:

   **RECEIVED**
      The CSR has been received and saved by the RA endpoint.
      Validation has not yet started.  Set by SYSTEM.

   **VALIDATED**
      All validation layers passed.  The request transitions
      immediately to SUBMITTED.  Set by SYSTEM.

   **VALIDATION_FAILED**
      One or more validation layers failed.  The request does not
      enter the approval pipeline.  The client MUST fix the issues
      and resubmit a new CSR.  Set by SYSTEM.

   **SUBMITTED**
      Validation passed.  The request is in the approval pool and
      available for operator pickup or admin assignment.
      This is the entry point for the approval workflow.

   IMPORTANT:  The approval workflow MUST operate exclusively on
   requests that have reached SUBMITTED status.

---

## 4.  Actors and Roles

   The following roles interact with the approval workflow:

   **CLIENT / USER**
      Submits CSR requests.  Polls for status updates.  Downloads
      issued certificates.  MUST NOT have access to approval
      operations or transition history.

   **ROLE_ADMIN**
      Defines workflow configurations.  Maps certificate profiles to
      configurations.  Monitors all requests across all statuses.
      Assigns requests to operators.  Closes requests permanently.
      Retries failed CA calls.  MUST be the only role able to
      modify workflow configuration.

   **ROLE_OPERATOR**
      In SINGLE mode: picks up requests, reviews, and approves or
      rejects directly.
      In DUAL mode, as Maker: picks up requests and submits review
      remarks.  As Checker: picks up reviewed requests and makes
      the final approval or rejection decision.
      An operator MUST NOT act as both Maker and Checker for the
      same request.

   **ROLE_AUDITOR**
      Read-only access to all requests, transition history, and
      audit log entries.  MUST NOT perform any workflow actions.

---

## 5.  Workflow Configuration — Per-Profile Model

### 5.1  Design Principles

   The system MUST support multiple independent workflow configurations.
   Each certificate profile MUST be mapped to exactly one active
   workflow configuration at any given time.

   A single system-wide configuration MUST NOT be used.  Different
   certificate profiles carry different risk levels and MUST be able to
   follow different approval processes independently.

   When a request is picked up by an operator, the system resolves the
   active workflow configuration for that request's profile and locks it
   permanently on the request record.  Subsequent changes to the profile
   mapping MUST NOT affect requests already in-flight.

   This resolution process is described in Section 5.5.

### 5.2  Database Tables

#### 5.2.1  Table: workflow_configurations

   Stores named, reusable workflow configuration objects.

```
   +-----------------------------+-------------+------+--------------------+
   | Column                      | Type        | Null | Description        |
   +-----------------------------+-------------+------+--------------------+
   | id                          | BIGINT PK   | NO   | Auto-increment PK  |
   | name                        | VARCHAR(100)| NO   | Human-readable name|
   |                             |             |      | e.g. "DUAL Pickup" |
   | approval_mode               | VARCHAR(10) | NO   | SINGLE or DUAL     |
   | assignment_mode             | VARCHAR(20) | NO   | SELF_PICKUP or     |
   |                             |             |      | ADMIN_ASSIGN       |
   | require_remarks             | BOOLEAN     | NO   | Remarks mandatory  |
   | max_pending_per_operator    | INT         | NO   | Max requests held  |
   |                             |             |      | per operator       |
   | maker_can_return            | BOOLEAN     | NO   | Maker may return   |
   |                             |             |      | to Admin           |
   | is_active                   | BOOLEAN     | NO   | Inactive configs   |
   |                             |             |      | cannot be mapped   |
   | created_at                  | DATETIME(6) | NO   | Creation timestamp |
   | created_by                  | VARCHAR(100)| NO   | Admin who created  |
   +-----------------------------+-------------+------+--------------------+
```

#### 5.2.2  Table: csr_profiles

   Certificate profiles as first-class entities.  Replaces the
   previous csr_profile VARCHAR(50) column in csr_requests.

```
   +-----------------------------+-------------+------+--------------------+
   | Column                      | Type        | Null | Description        |
   +-----------------------------+-------------+------+--------------------+
   | id                          | BIGINT PK   | NO   | Auto-increment PK  |
   | name                        | VARCHAR(50) | NO   | Profile code e.g.  |
   |                             |             |      | TLS_SERVER,        |
   |                             |             |      | CODE_SIGNING, SMIME|
   | description                 | VARCHAR(500)| YES  | Human description  |
   | is_active                   | BOOLEAN     | NO   | Active flag        |
   | created_at                  | DATETIME(6) | NO   | Creation timestamp |
   +-----------------------------+-------------+------+--------------------+
```

#### 5.2.3  Table: profile_workflow_mapping

   Binds each certificate profile to exactly one workflow configuration.
   Effective date columns provide full history and support for
   future-dated mapping changes.

```
   +-----------------------------+-------------+------+--------------------+
   | Column                      | Type        | Null | Description        |
   +-----------------------------+-------------+------+--------------------+
   | id                          | BIGINT PK   | NO   | Auto-increment PK  |
   | profile_id                  | BIGINT FK   | NO   | → csr_profiles.id  |
   | workflow_config_id          | BIGINT FK   | NO   | → workflow_        |
   |                             |             |      |   configurations.id|
   | effective_from              | DATETIME(6) | NO   | When mapping starts|
   | effective_until             | DATETIME(6) | YES  | NULL = currently   |
   |                             |             |      | active             |
   | created_by                  | VARCHAR(100)| NO   | Admin who created  |
   | created_at                  | DATETIME(6) | NO   | Creation timestamp |
   +-----------------------------+-------------+------+--------------------+
```

   CONSTRAINT:  Only one mapping per profile MAY have effective_until
   IS NULL at any time.  When Admin creates a new mapping, the system
   MUST set effective_until = NOW() on the previous active mapping.

### 5.3  Example Configuration Setup

#### Configuration 1 — High Security DUAL Self-Pickup

```
   id              = 1
   name            = "High Security DUAL Self-Pickup"
   approval_mode   = DUAL
   assignment_mode = SELF_PICKUP
   require_remarks = true
   max_pending     = 5
   maker_can_return= true
```

   Mapped profiles: TLS_SERVER, CODE_SIGNING

   Rationale: These profiles issue certificates for public-facing
   servers and code signing.  Two-operator review reduces the risk
   of a misconfigured or fraudulent certificate being issued.

#### Configuration 2 — Standard SINGLE Self-Pickup

```
   id              = 2
   name            = "Standard SINGLE Self-Pickup"
   approval_mode   = SINGLE
   assignment_mode = SELF_PICKUP
   require_remarks = true
   max_pending     = 10
   maker_can_return= true
```

   Mapped profiles: TLS_CLIENT, SMIME, DOCUMENT_SIGNING

   Rationale: These profiles are lower risk and internal.  A single-
   operator review is sufficient.

### 5.4  Admin API for Configuration Management

   All configuration management endpoints MUST be restricted to
   ROLE_ADMIN.  Operators and auditors MUST NOT have access.

```
   POST   /api/ra/admin/workflow-configs        Create workflow config
   GET    /api/ra/admin/workflow-configs        List all configs
   GET    /api/ra/admin/workflow-configs/{id}   Get a specific config
   PUT    /api/ra/admin/workflow-configs/{id}   Update a config
   POST   /api/ra/admin/profile-mappings        Map profile to config
   GET    /api/ra/admin/profile-mappings        List active mappings
   PUT    /api/ra/admin/profile-mappings/{id}   Update mapping
```

   Example — Create Workflow Configuration:

```http
   POST /api/ra/admin/workflow-configs
   Authorization: Bearer <admin-token>
   Content-Type: application/json

   {
     "name": "High Security DUAL Self-Pickup",
     "approvalMode": "DUAL",
     "assignmentMode": "SELF_PICKUP",
     "requireRemarks": true,
     "maxPendingPerOperator": 5,
     "makerCanReturn": true
   }
```

   Response 201 Created:

```json
   {
     "id": 1,
     "name": "High Security DUAL Self-Pickup",
     "approvalMode": "DUAL",
     "assignmentMode": "SELF_PICKUP",
     "requireRemarks": true,
     "maxPendingPerOperator": 5,
     "isActive": true
   }
```

   Example — Map Profile to Configuration:

```http
   POST /api/ra/admin/profile-mappings
   Authorization: Bearer <admin-token>
   Content-Type: application/json

   {
     "profileId": 1,
     "workflowConfigId": 1
   }
```

   Response 201 Created:

```json
   {
     "profileName": "TLS_SERVER",
     "workflowConfigName": "High Security DUAL Self-Pickup",
     "effectiveFrom": "2026-07-01T00:00:00Z"
   }
```

### 5.5  Runtime Resolution

   At the time an operator picks up a request, the system MUST perform
   the following resolution steps in order:

   1.  Read csr_requests.profile_id.

   2.  Query profile_workflow_mapping WHERE profile_id = ? AND
       effective_until IS NULL to obtain the active workflow_config_id.

   3.  Load the associated workflow_configurations row.

   4.  Store workflow_config_id and approval_mode on the request record
       as approval_mode_at_pickup.

   5.  Apply that configuration for the entire lifecycle of this
       request.

   The system MUST store approval_mode_at_pickup at pickup time.
   Subsequent changes to the profile mapping MUST NOT alter the
   approval mode of any in-flight request.

```
   Request arrives — csrProfile = TLS_SERVER
          |
          v
   csr_profiles: profile_id = 1 (TLS_SERVER)
          |
          v
   profile_workflow_mapping: workflow_config_id = 1 (DUAL)
          |
          v
   workflow_configurations: approval_mode = DUAL
                            assignment_mode = SELF_PICKUP
          |
          v
   Lock on request:
     workflow_config_id       = 1
     approval_mode_at_pickup  = DUAL
```

---

## 6.  State Machine

### 6.1  SINGLE Mode

```
   [SUBMITTED]
        |
        | Operator self-pickup or Admin assigns
        v
   [IN_REVIEW]
        |
        +---[/approve]-----> [APPROVED] ---> [SENT_TO_CA]
        |                                         |
        |                                         +---> [ISSUED]
        |                                         +---> [PENDING] --> [ISSUED]
        |                                         |              --> [FAILED]
        |                                         +---> [FAILED]
        |
        +---[/approve REJECT]-> [REJECTED] ---> Admin
        |
        +---[/return]-------> [RETURNED]  ---> Admin
```

### 6.2  DUAL Mode

```
   [SUBMITTED]
        |
        | Maker self-pickup or Admin assigns Maker
        v
   [IN_REVIEW]
        |
        +---[/review]-------> [REVIEWED]
        |                         |
        |                         | Checker self-pickup (MUST NOT be Maker)
        |                         v
        |                    [Checker reviews]
        |                         |
        |                         +---[/approve]-------> [APPROVED]
        |                         |                           |
        |                         |                      [SENT_TO_CA]
        |                         |                           |
        |                         |                      [ISSUED / PENDING / FAILED]
        |                         |
        |                         +---[/approve REJECT]-> [REJECTED] ---> Admin
        |
        +---[/return]-------> [RETURNED] ---> Admin
```

### 6.3  State Definitions

```
   +------------------+--------------------------------------------------+
   | State            | Description                                      |
   +------------------+--------------------------------------------------+
   | SUBMITTED        | Validation passed. Available for pickup.         |
   +------------------+--------------------------------------------------+
   | IN_REVIEW        | Operator picked up. SINGLE: operator approves.   |
   |                  | DUAL: this operator is the Maker.                |
   +------------------+--------------------------------------------------+
   | REVIEWED         | DUAL only. Maker submitted remarks. Awaiting     |
   |                  | Checker pickup.                                  |
   +------------------+--------------------------------------------------+
   | RETURNED         | Operator returned to Admin for clarification     |
   |                  | or reassignment.                                 |
   +------------------+--------------------------------------------------+
   | APPROVED         | Approved by single operator (SINGLE) or Checker  |
   |                  | (DUAL). Request dispatched to CA.                |
   +------------------+--------------------------------------------------+
   | REJECTED         | Rejected. Routed to Admin for reassign or close. |
   +------------------+--------------------------------------------------+
   | CLOSED           | Permanently closed by Admin. Terminal state.     |
   +------------------+--------------------------------------------------+
   | SENT_TO_CA       | Request dispatched to CA. Awaiting response.     |
   +------------------+--------------------------------------------------+
   | PENDING          | CA acknowledged but processing asynchronously.   |
   |                  | Awaiting CA callback.                            |
   +------------------+--------------------------------------------------+
   | ISSUED           | CA signed successfully. Certificate active.      |
   +------------------+--------------------------------------------------+
   | FAILED           | CA signing failed. Admin may retry or close.     |
   +------------------+--------------------------------------------------+
```

### 6.4  Valid Transitions

```
   +---------------+---------------+----------------------------------+
   | From          | To            | Who / Condition                  |
   +---------------+---------------+----------------------------------+
   | SUBMITTED     | IN_REVIEW     | Operator self-pickup or Admin    |
   |               |               | assigns                          |
   | IN_REVIEW     | APPROVED      | SINGLE: operator approves        |
   | IN_REVIEW     | REJECTED      | SINGLE: operator rejects         |
   | IN_REVIEW     | REVIEWED      | DUAL: Maker submits remarks      |
   | IN_REVIEW     | RETURNED      | Operator returns (any mode)      |
   | REVIEWED      | APPROVED      | DUAL: Checker approves           |
   | REVIEWED      | REJECTED      | DUAL: Checker rejects            |
   | RETURNED      | IN_REVIEW     | Admin reassigns                  |
   | RETURNED      | CLOSED        | Admin permanently closes         |
   | REJECTED      | IN_REVIEW     | Admin reassigns                  |
   | REJECTED      | CLOSED        | Admin permanently closes         |
   | APPROVED      | SENT_TO_CA    | System dispatches to CA          |
   | SENT_TO_CA    | ISSUED        | CA sync response with cert       |
   | SENT_TO_CA    | PENDING       | CA ack — async processing        |
   | SENT_TO_CA    | FAILED        | CA returned error                |
   | PENDING       | ISSUED        | CA callback — cert ready         |
   | PENDING       | FAILED        | CA callback — failure            |
   | FAILED        | SENT_TO_CA    | Admin retries                    |
   | FAILED        | CLOSED        | Admin permanently closes         |
   +---------------+---------------+----------------------------------+
```

---

## 7.  Detailed Flow — SINGLE Mode with Self-Pickup

   This flow applies when a certificate profile is mapped to a workflow
   configuration with approval_mode = SINGLE and assignment_mode =
   SELF_PICKUP.  One operator picks the request from the shared pool
   and is solely responsible for reviewing and deciding.

### 7.1  Step 1 — Client Submits CSR

   The client submits a CSR via POST /api/ra/requests.  The system
   runs the validation engine and, if all checks pass, places the
   request in SUBMITTED status.

   The system resolves the workflow configuration:

   -  Profile = TLS_CLIENT → mapped to: SINGLE Self-Pickup config
   -  approval_mode_at_pickup = SINGLE (stored at operator pickup)

   Response to client (201 Created):

```json
   {
     "requestId": "RA-REQ-2026-00000042",
     "status": "SUBMITTED",
     "message": "Validation passed. Request is in the approval queue."
   }
```

   The request is visible in the shared operator pool.  All operators
   with ROLE_OPERATOR MAY view it.

### 7.2  Step 2 — Operator Views the Pool

```
   GET /api/ra/requests/pool
   Auth: ROLE_OPERATOR
```

   Returns all requests in SUBMITTED status with no maker_id assigned.
   Response includes workflowMode: SINGLE per request.

### 7.3  Step 3 — Operator Picks Up the Request

```
   POST /api/ra/requests/{requestId}/pickup
   Auth: ROLE_OPERATOR
```

   The system MUST enforce the following checks before allowing pickup:

   a.  Request MUST be in SUBMITTED status.
   b.  Operator MUST NOT be the original requestor.
   c.  Operator MUST NOT exceed max_pending_per_operator.

   On success:

   -  status → IN_REVIEW
   -  maker_id → current operator user ID
   -  assigned_at → current timestamp
   -  approval_mode_at_pickup → SINGLE (locked)
   -  Transition history entry created

   All other operators see this request as IN_REVIEW.  They MUST NOT
   be able to pick it up.

### 7.4  Step 4a — Operator Approves

```http
   POST /api/ra/requests/{requestId}/approve
   Auth: ROLE_OPERATOR

   {
     "remarks": "CSR verified. Key size RSA-4096. Approved."
   }
```

   Server-side checks:

   -  Caller MUST be the Maker (maker_id == currentUser.id).
   -  approval_mode_at_pickup MUST be SINGLE.
   -  Remarks MUST be provided if require_remarks = true.

   On success:

   -  status → APPROVED → SENT_TO_CA
   -  maker_remarks and maker_reviewed_at stored

### 7.5  Step 4b — Operator Rejects

```http
   POST /api/ra/requests/{requestId}/approve
   Auth: ROLE_OPERATOR

   {
     "decision": "REJECT",
     "remarks": "Domain not in approved list."
   }
```

   The same /approve endpoint handles both decisions.  The server
   routes based on the decision field.

   On success: status → REJECTED.  Request appears in Admin dashboard.

### 7.6  Step 4c — Operator Returns to Admin

```http
   POST /api/ra/requests/{requestId}/return
   Auth: ROLE_OPERATOR

   {
     "reason": "Domain flagged as decommissioned. Needs Admin verification."
   }
```

   On success: status → RETURNED.  Admin MUST decide to reassign or
   permanently close.

### 7.7  Step 5 — Admin Actions on REJECTED or RETURNED

   Admin reassigns:

```http
   POST /api/ra/requests/{requestId}/assign
   Auth: ROLE_ADMIN

   { "operatorId": 7, "remarks": "Reassigned." }
```

   On success: status → IN_REVIEW, new maker_id assigned.

   Admin closes permanently:

```http
   POST /api/ra/requests/{requestId}/close
   Auth: ROLE_ADMIN

   { "reason": "Request withdrawn by requestor." }
```

   On success: status → CLOSED.  No further actions are possible.

### 7.8  SINGLE Mode — Complete Status Lifecycle

```
   SUBMITTED
     → IN_REVIEW          (operator self-pickup)
         → APPROVED        (operator approves)
             → SENT_TO_CA  (system dispatches)
                 → ISSUED
                 → PENDING → ISSUED / FAILED
                 → FAILED → Admin retry → SENT_TO_CA
         → REJECTED        (operator rejects)
             → IN_REVIEW   (Admin reassigns)
             → CLOSED      (Admin closes)
         → RETURNED        (operator returns)
             → IN_REVIEW   (Admin reassigns)
             → CLOSED      (Admin closes)
```

---

## 8.  Detailed Flow — DUAL Mode with Self-Pickup

   This flow applies when a certificate profile is mapped to a workflow
   configuration with approval_mode = DUAL and assignment_mode =
   SELF_PICKUP.

   Two operators are REQUIRED.  The Maker reviews and submits a
   recommendation.  A different operator (the Checker) makes the final
   approval or rejection decision.  The same person MUST NOT act as
   both Maker and Checker for the same request.

### 8.1  Step 1 — Client Submits CSR

   Same as Section 7.1.  On successful validation, status = SUBMITTED
   with workflowMode: DUAL visible in the pool.

### 8.2  Step 2 — Maker Views the Pool

```
   GET /api/ra/requests/pool
   Auth: ROLE_OPERATOR
```

   Response includes workflowMode: DUAL.  The operator knows that after
   reviewing, a second operator will make the final decision.

### 8.3  Step 3 — Maker Picks Up the Request

```
   POST /api/ra/requests/{requestId}/pickup
   Auth: ROLE_OPERATOR
```

   Same validation checks as Section 7.3.

   On success:

   -  status → IN_REVIEW
   -  maker_id → current operator user ID
   -  approval_mode_at_pickup → DUAL (locked)

   Response includes yourRole: MAKER confirming the operator's role.

### 8.4  Step 4 — Maker Submits Review

   The Maker MUST review:

   -  Subject DN correctness
   -  Key algorithm and size against profile policy
   -  Validity period against profile constraints
   -  Requestor identity and stated purpose
   -  Validation flags raised by the validation engine

   The Maker MUST NOT make the final approval decision.

```http
   POST /api/ra/requests/{requestId}/review
   Auth: ROLE_OPERATOR

   {
     "remarks": "CSR verified. RSA-4096. Domain matches registry. Recommend approval."
   }
```

   Server-side checks:

   -  Caller MUST be the Maker (maker_id == currentUser.id).
   -  approval_mode_at_pickup MUST be DUAL.
   -  Remarks MUST be provided if require_remarks = true.

   On success:

   -  status → REVIEWED
   -  maker_remarks and maker_reviewed_at stored

   The request appears in the Checker pool — visible to all operators
   EXCEPT the Maker.

### 8.5  Step 4 (Alternate) — Maker Returns to Admin

```http
   POST /api/ra/requests/{requestId}/return
   Auth: ROLE_OPERATOR

   {
     "reason": "Domain flagged as decommissioned. Needs Admin verification."
   }
```

   On success: status → RETURNED.  Admin reassigns or closes.

### 8.6  Step 5 — Checker Views the Checker Pool

```
   GET /api/ra/requests/pool?forChecker=true
   Auth: ROLE_OPERATOR
```

   Returns all REVIEWED requests where maker_id != currentUser.id.
   Response includes makerName and makerRemarks for each record.

### 8.7  Step 6 — Checker Picks Up the Reviewed Request

```
   POST /api/ra/requests/{requestId}/pickup
   Auth: ROLE_OPERATOR
```

   The system MUST enforce separation of duties:

   -  Request MUST be in REVIEWED status.
   -  Caller MUST NOT be the Maker (maker_id != currentUser.id).
   -  If caller IS the Maker: MUST return 403 PKI_APR_005.

   On success:

   -  checker_id → current operator user ID
   -  Status remains REVIEWED

   Response includes yourRole: CHECKER and the Maker's remarks.

### 8.8  Step 7a — Checker Approves

```http
   POST /api/ra/requests/{requestId}/approve
   Auth: ROLE_OPERATOR

   {
     "remarks": "Agreed with Maker. Domain verified. Approving."
   }
```

   Server routes this as a Checker approval because checker_id ==
   currentUser.id and approval_mode_at_pickup == DUAL.

   On success:

   -  checker_decision → APPROVED
   -  status → APPROVED → SENT_TO_CA

### 8.9  Step 7b — Checker Rejects

```http
   POST /api/ra/requests/{requestId}/approve
   Auth: ROLE_OPERATOR

   {
     "decision": "REJECT",
     "remarks": "Domain scheduled for retirement. Certificate not appropriate."
   }
```

   On success: checker_decision → REJECTED, status → REJECTED.
   Request routed to Admin dashboard.

### 8.10  Separation of Duties — Enforcement Rules

```
   +----------------------------------------+-------------------------+
   | Rule                                   | Enforcement             |
   +----------------------------------------+-------------------------+
   | Maker MUST NOT be Checker for same     | maker_id != currentUser |
   | request.                               | checked at Checker      |
   |                                        | pickup. 403 PKI_APR_005 |
   +----------------------------------------+-------------------------+
   | Requestor MUST NOT be Maker or Checker.| requestor_user_id !=    |
   |                                        | currentUser checked at  |
   |                                        | pickup. 403 PKI_APR_006 |
   +----------------------------------------+-------------------------+
   | Maker MUST NOT approve directly in     | Server rejects /approve |
   | DUAL mode.                             | when called by maker_id |
   |                                        | with mode=DUAL and      |
   |                                        | status=IN_REVIEW.       |
   |                                        | 400 PKI_APR_015         |
   +----------------------------------------+-------------------------+
```

### 8.11  DUAL Mode — Complete Status Lifecycle

```
   SUBMITTED
     → IN_REVIEW               (Maker self-pickup)
         → REVIEWED             (Maker submits remarks)
             → [checker_id assigned via /pickup]
                 → APPROVED     (Checker approves)
                     → SENT_TO_CA
                         → ISSUED
                         → PENDING → ISSUED / FAILED
                         → FAILED → Admin retry
                 → REJECTED     (Checker rejects)
                     → IN_REVIEW (Admin reassigns new Maker)
                     → CLOSED    (Admin closes)
         → RETURNED             (Maker returns)
             → IN_REVIEW        (Admin reassigns new Maker)
             → CLOSED           (Admin closes)
```

---

## 9.  Database Schema

### 9.1  Table: csr_requests

   Stores core CSR data.  Approval tracking and requestor info are
   separated into dedicated tables (Sections 9.2 and 9.3).

```
   +----------------------------+---------------+------+------------------+
   | Column                     | Type          | Null | Description      |
   +----------------------------+---------------+------+------------------+
   | id                         | BIGINT PK     | NO   | Auto-increment   |
   | request_id                 | VARCHAR(60)   | NO   | System-generated |
   |                            |               |      | RA-REQ-{YYYY}-   |
   |                            |               |      | {8-digit-seq}    |
   |                            |               |      | UNIQUE           |
   | client_txn_id              | VARCHAR(100)  | YES  | Client-provided  |
   |                            |               |      | optional ID.     |
   |                            |               |      | UNIQUE if set.   |
   | csr_pem                    | TEXT          | NO   | Raw CSR in PEM   |
   | subject_dn                 | VARCHAR(500)  | NO   | Parsed subject DN|
   | key_algorithm              | VARCHAR(20)   | NO   | RSA, ECDSA, etc. |
   | key_size                   | INT           | YES  | Key size in bits |
   | signature_algorithm        | VARCHAR(50)   | NO   | SHA256withRSA    |
   | subject_alt_names          | VARCHAR(2000) | YES  | SANs from CSR    |
   | csr_hash                   | VARCHAR(64)   | NO   | SHA-256 of CSR   |
   |                            |               |      | for dup detection|
   | profile_id                 | BIGINT FK     | NO   | → csr_profiles   |
   | workflow_config_id         | BIGINT FK     | YES  | → workflow_       |
   |                            |               |      |   configurations |
   |                            |               |      | Set at pickup    |
   | approval_mode_at_pickup    | VARCHAR(10)   | YES  | SINGLE or DUAL   |
   |                            |               |      | Locked at pickup |
   | status                     | VARCHAR(20)   | NO   | Current status   |
   | status_reason              | VARCHAR(1000) | YES  | Reason for status|
   | validation_passed          | BOOLEAN       | YES  | Validation result|
   | validation_warnings        | JSON          | YES  | Warnings         |
   | validation_flags           | JSON          | YES  | Flags for review |
   | certificate_id             | BIGINT FK     | YES  | → certificates   |
   | created_at                 | DATETIME(6)   | NO   | Request received |
   | created_by                 | VARCHAR(100)  | NO   | Submitter        |
   | updated_at                 | DATETIME(6)   | NO   | Last update      |
   | updated_by                 | VARCHAR(100)  | NO   | Last updater     |
   +----------------------------+---------------+------+------------------+
```

   NOTES:

   -  request_id uses 8-digit sequence: RA-REQ-2026-00000042.
      Maximum 99,999,999 requests per year.

   -  client_txn_id is OPTIONAL.  If not provided by the client,
      the field is left NULL.  Client MUST use requestId for
      tracking in that case.  If provided, a 1:1 mapping with
      requestId is established.

   Indexes:

```
   UNIQUE  uq_request_id       (request_id)
   UNIQUE  uq_client_txn_id    (client_txn_id)   -- nullable unique
   INDEX   idx_csr_status       (status)
   INDEX   idx_csr_profile      (profile_id)
   INDEX   idx_csr_hash         (csr_hash)
   INDEX   idx_csr_created      (created_at)
   INDEX   idx_csr_workflow     (workflow_config_id)
```

### 9.2  Table: csr_requestor_info

   Stores requestor context.  For authenticated users, identity is
   resolved via requestor_user_id FK to the users table — it MUST NOT
   be duplicated here.

```
   +----------------------------+---------------+------+------------------+
   | Column                     | Type          | Null | Description      |
   +----------------------------+---------------+------+------------------+
   | id                         | BIGINT PK     | NO   | Auto-increment   |
   | request_id                 | BIGINT FK     | NO   | → csr_requests   |
   | requestor_user_id          | BIGINT FK     | YES  | → users.id       |
   |                            |               |      | Set for auth     |
   |                            |               |      | users. Name/email|
   |                            |               |      | fetched via JOIN.|
   | requestor_name             | VARCHAR(200)  | YES  | Anonymous only   |
   | requestor_email            | VARCHAR(200)  | YES  | Anonymous only   |
   | requestor_department       | VARCHAR(200)  | YES  | Anonymous only   |
   | requestor_phone            | VARCHAR(50)   | YES  | Anonymous only   |
   | requested_not_before       | DATETIME(6)   | YES  | NULL = now       |
   | requested_not_after        | DATETIME(6)   | YES  | NULL = profile   |
   |                            |               |      | default          |
   | purpose                    | VARCHAR(1000) | YES  | Why cert needed  |
   | priority                   | VARCHAR(10)   | NO   | NORMAL/HIGH/     |
   |                            |               |      | URGENT           |
   | additional_attributes      | JSON          | YES  | Org metadata.    |
   |                            |               |      | Keys validated   |
   |                            |               |      | per profile.     |
   +----------------------------+---------------+------+------------------+
```

   Validity period resolution rules:

```
   +---------------------------+----------------------------------------+
   | Fields Provided           | Result                                 |
   +---------------------------+----------------------------------------+
   | Neither                   | notBefore = now                        |
   |                           | notAfter  = now + profile default days |
   | Only notBefore            | notAfter  = notBefore + profile default|
   | Only notAfter             | notBefore = now                        |
   |                           | Validate notAfter > now                |
   | Both provided             | Use as-is                              |
   |                           | Validate range within profile limits   |
   +---------------------------+----------------------------------------+
```

### 9.3  Table: csr_approval_workflow

   Stores approval tracking data.  Created when a request enters
   IN_REVIEW status.  Does not exist for SUBMITTED or earlier.

```
   +----------------------------+---------------+------+------------------+
   | Column                     | Type          | Null | Description      |
   +----------------------------+---------------+------+------------------+
   | id                         | BIGINT PK     | NO   | Auto-increment   |
   | request_id                 | BIGINT FK     | NO   | → csr_requests   |
   | maker_id                   | BIGINT FK     | YES  | → users.id       |
   |                            |               |      | (Maker operator) |
   | assigned_at                | DATETIME(6)   | YES  | Pickup/assign ts |
   | maker_remarks              | VARCHAR(2000) | YES  | Maker review note|
   | maker_reviewed_at          | DATETIME(6)   | YES  | When Maker done  |
   | checker_id                 | BIGINT FK     | YES  | → users.id       |
   |                            |               |      | (Checker operator|
   | checker_decision           | VARCHAR(10)   | YES  | APPROVED/REJECTED|
   | checker_remarks            | VARCHAR(2000) | YES  | Checker decision |
   | checker_decided_at         | DATETIME(6)   | YES  | When Checker done|
   | closed_by_id               | BIGINT FK     | YES  | → users.id Admin |
   | closed_at                  | DATETIME(6)   | YES  | When Admin closed|
   | closed_reason              | VARCHAR(2000) | YES  | Closure reason   |
   +----------------------------+---------------+------+------------------+
```

   NOTE:  The column previously named assigned_to_id has been renamed
   to maker_id for clarity and symmetry with checker_id.

### 9.4  Table: certificate_request_transitions

   Every status change MUST create an immutable history entry.
   Entries in this table MUST NOT be updated or deleted.

```
   +----------------------------+---------------+------+------------------+
   | Column                     | Type          | Null | Description      |
   +----------------------------+---------------+------+------------------+
   | id                         | BIGINT PK     | NO   | Auto-increment   |
   | request_id                 | BIGINT FK     | NO   | → csr_requests   |
   | from_status                | VARCHAR(20)   | YES  | Previous status  |
   |                            |               |      | NULL on first    |
   | to_status                  | VARCHAR(20)   | NO   | New status       |
   | changed_by_id              | BIGINT FK     | NO   | → users.id       |
   | changed_by_role            | VARCHAR(20)   | NO   | ADMIN/OPERATOR/  |
   |                            |               |      | SYSTEM           |
   | remarks                    | VARCHAR(2000) | YES  | Reason/remarks   |
   | created_at                 | DATETIME(6)   | NO   | When occurred    |
   +----------------------------+---------------+------+------------------+
```

---

## 10.  REST API Reference

### 10.1  Workflow Configuration Management

   All endpoints in this section MUST require ROLE_ADMIN.

```
   +--------+------------------------------------------+---------------------+
   | Method | Path                                     | Purpose             |
   +--------+------------------------------------------+---------------------+
   | POST   | /api/ra/admin/workflow-configs           | Create config       |
   | GET    | /api/ra/admin/workflow-configs           | List all configs    |
   | GET    | /api/ra/admin/workflow-configs/{id}      | Get specific config |
   | PUT    | /api/ra/admin/workflow-configs/{id}      | Update config       |
   | POST   | /api/ra/admin/profile-mappings           | Map profile→config  |
   | GET    | /api/ra/admin/profile-mappings           | List mappings       |
   | PUT    | /api/ra/admin/profile-mappings/{id}      | Update mapping      |
   +--------+------------------------------------------+---------------------+
```

### 10.2  CSR Submission and Lookup

   NOTE:  The previous /by-request-id/{id} and /by-txn-id/{id}
   endpoints have been replaced with a unified query-parameter style.
   The previous /{dbId}/history endpoint used an internal database
   auto-increment ID that is never exposed to the client.  This has
   been replaced with {requestId} (e.g., RA-REQ-2026-00000042).

```
   +--------+------------------------------------------+---------------------+--------+
   | Method | Path                                     | Purpose             | Auth   |
   +--------+------------------------------------------+---------------------+--------+
   | POST   | /api/ra/requests                         | Submit CSR          | Any    |
   | GET    | /api/ra/requests?requestId=...           | Lookup by requestId | Any    |
   | GET    | /api/ra/requests?clientTxnId=...         | Lookup by client ID | Any    |
   | GET    | /api/ra/requests?status=...&from=...     | Filter requests     | ADMIN  |
   | GET    | /api/ra/requests/{requestId}/history     | Transition history  | ADMIN  |
   |        |                                          |                     | AUDIT  |
   +--------+------------------------------------------+---------------------+--------+
```

### 10.3  Admin Dashboard and Actions

```
   +--------+------------------------------------------+---------------------+--------+
   | Method | Path                                     | Purpose             | Auth   |
   +--------+------------------------------------------+---------------------+--------+
   | GET    | /api/ra/requests/summary                 | Status-wise counts  | ADMIN  |
   | GET    | /api/ra/operators                        | List operators      | ADMIN  |
   | POST   | /api/ra/requests/{requestId}/assign      | Assign to operator  | ADMIN  |
   | POST   | /api/ra/requests/{requestId}/close       | Close permanently   | ADMIN  |
   | POST   | /api/ra/requests/{requestId}/retry       | Retry failed CA     | ADMIN  |
   +--------+------------------------------------------+---------------------+--------+
```

### 10.4  Operator Actions

   NOTE:  /pickup and /pickup-check have been merged into a single
   /pickup endpoint.  /approve and /accept have been merged into a
   single /approve endpoint.  Server-side routing determines the
   caller's role and applicable action based on request state and
   approval_mode_at_pickup.  Error code PKI_APR_012 has been removed.

```
   +--------+------------------------------------------+---------------------+--------+
   | Method | Path                                     | Purpose             | Auth   |
   +--------+------------------------------------------+---------------------+--------+
   | GET    | /api/ra/requests/pool                    | View pickup pool    | OPER   |
   | GET    | /api/ra/requests/my-work                 | My in-progress work | OPER   |
   | POST   | /api/ra/requests/{requestId}/pickup      | Pick up request     | OPER   |
   |        |                                          | Maker: SUBMITTED    |        |
   |        |                                          | Checker: REVIEWED   |        |
   | POST   | /api/ra/requests/{requestId}/review      | Maker submits notes | OPER   |
   |        |                                          | DUAL mode only      |        |
   | POST   | /api/ra/requests/{requestId}/approve     | Approve or reject   | OPER   |
   |        |                                          | Routes by role/mode |        |
   | POST   | /api/ra/requests/{requestId}/return      | Return to Admin     | OPER   |
   +--------+------------------------------------------+---------------------+--------+
```

   /pickup routing:

```
   Request status = SUBMITTED
     → Caller becomes Maker (maker_id = currentUser.id)

   Request status = REVIEWED AND maker_id != currentUser.id
     → Caller becomes Checker (checker_id = currentUser.id)

   Request status = REVIEWED AND maker_id == currentUser.id
     → 403 PKI_APR_005 (separation of duties)
```

   /approve routing:

```
   approval_mode_at_pickup = SINGLE AND maker_id == currentUser.id
     → Direct operator approval

   approval_mode_at_pickup = DUAL AND checker_id == currentUser.id
     → Checker final decision

   approval_mode_at_pickup = DUAL AND maker_id == currentUser.id
     → 400 PKI_APR_015 — Maker MUST use /review in DUAL mode
```

### 10.5  Certificate Endpoints

   NOTE:  The previous /certificates/{serial} endpoint has been
   replaced.  A serial number alone does not uniquely identify a
   certificate across multiple CAs.  The globally unique identifier
   per RFC 5280 is the combination of issuerDn and serialNr.

```
   +--------+------------------------------------------+---------------------+--------+
   | Method | Path                                     | Purpose             | Auth   |
   +--------+------------------------------------------+---------------------+--------+
   | GET    | /api/ra/certificates?issuerDn=..         | Lookup by issuer +  | Any    |
   |        | &serialNr=...                            | serial (RFC 5280)   |        |
   | GET    | /api/ra/certificates?requestId=...       | Lookup by requestId | Any    |
   | GET    | /api/ra/certificates/download?requestId= | Download PEM cert   | Any    |
   | GET    | /api/ra/certificates/download?issuerDn=  | Download PEM cert   | Any    |
   |        | &serialNr=...                            |                     |        |
   +--------+------------------------------------------+---------------------+--------+
```

### 10.6  CA Callback Endpoint

   This endpoint receives asynchronous issuance results from the CA.
   It MUST be authenticated using an HMAC signature validated against
   a shared secret provisioned between the RA and the CA.

```
   POST  /api/ra/ca/callback     Auth: CA system (HMAC-signed)
```

   Request body:

```json
   {
     "requestId":    "RA-REQ-2026-00000043",
     "result":       "ISSUED",
     "certificatePem": "-----BEGIN CERTIFICATE-----\n...",
     "serialNumber": "4F2A1B3C",
     "issuedAt":     "2026-07-01T11:00:00Z"
   }
```

   On result = ISSUED:  status → ISSUED, certificate stored.
   On result = FAILED:  status → FAILED, status_reason set.

---

## 11.  Record Visibility

```
   +------------------+-----------------------------------------------------+
   | Role             | What They See                                       |
   +------------------+-----------------------------------------------------+
   | ADMIN            | All records at all statuses. Full transition        |
   |                  | history. Who picked up, reviewed, and decided.      |
   |                  | Filter by status, operator, profile, date, DN.      |
   |                  | Configuration and mapping management.               |
   +------------------+-----------------------------------------------------+
   | OPERATOR         | All records with current status. SUBMITTED records  |
   |                  | available for Maker pickup. REVIEWED records        |
   |                  | available for Checker pickup (excluding own).       |
   |                  | Own in-progress requests.                           |
   +------------------+-----------------------------------------------------+
   | AUDITOR          | All records (read-only). Full transition history.   |
   |                  | Audit log entries.                                  |
   +------------------+-----------------------------------------------------+
   | CLIENT / USER    | Own submitted requests only. Queryable by           |
   |                  | requestId or clientTxnId. Status and certificate    |
   |                  | download after issuance. No transition history.     |
   +------------------+-----------------------------------------------------+
```

---

## 12.  Transition History

   Every status change MUST create a permanent, immutable entry in
   certificate_request_transitions.  This table provides the full
   audit trail for any request.

   Example history for a DUAL Self-Pickup flow:

```
   +---+---------------+---------------+---------------+----------+-------------------+
   | # | From          | To            | By            | Role     | Remarks           |
   +---+---------------+---------------+---------------+----------+-------------------+
   | 1 | —             | RECEIVED      | system        | SYSTEM   | CSR received      |
   | 2 | RECEIVED      | VALIDATED     | system        | SYSTEM   | Validation passed |
   | 3 | VALIDATED     | SUBMITTED     | system        | SYSTEM   | Queued — DUAL     |
   | 4 | SUBMITTED     | IN_REVIEW     | priya.patel   | OPERATOR | Maker self-pickup |
   | 5 | IN_REVIEW     | REVIEWED      | priya.patel   | OPERATOR | RSA-4096. OK.     |
   | 6 | REVIEWED      | REVIEWED      | vikram.singh  | OPERATOR | Checker picked up |
   | 7 | REVIEWED      | APPROVED      | vikram.singh  | OPERATOR | Agreed. Approved. |
   | 8 | APPROVED      | SENT_TO_CA    | system        | SYSTEM   | Dispatched to CA  |
   | 9 | SENT_TO_CA    | ISSUED        | system        | SYSTEM   | Serial: 4F2A1B3C  |
   +---+---------------+---------------+---------------+----------+-------------------+
```

   Access:

```
   GET /api/ra/requests/{requestId}/history
   Auth: ROLE_ADMIN or ROLE_AUDITOR
```

---

## 13.  CA Integration and Issuance States

### 13.1  CA Dispatch Flow

   When a request reaches APPROVED status, the system MUST immediately
   dispatch it to the CA.  Three outcomes are expected.

```
   [APPROVED]
        |
        | System dispatches
        v
   [SENT_TO_CA]
        |
        +--[CA sync response with cert]---------> [ISSUED]
        |
        +--[CA ack, async processing]-----------> [PENDING]
        |                                              |
        |                                         CA callback
        |                                              |
        |                                    +---------+---------+
        |                                    |                   |
        |                                 [ISSUED]           [FAILED]
        |
        +--[CA error]--------------------------> [FAILED]
```

### 13.2  Three Expected CA Results

```
   +----------+------------+-----------------------------------------------+
   | CA Result| RA Status  | Description                                   |
   +----------+------------+-----------------------------------------------+
   | ISSUED   | ISSUED     | CA responded synchronously with a signed      |
   |          |            | certificate. Certificate stored immediately.  |
   +----------+------------+-----------------------------------------------+
   | PENDING  | PENDING    | CA acknowledged and queued the request for    |
   |          |            | async processing. RA waits for CA callback    |
   |          |            | via POST /api/ra/ca/callback.                 |
   +----------+------------+-----------------------------------------------+
   | FAILED   | FAILED     | CA returned an error. Admin reviews and       |
   |          |            | may retry or close permanently.               |
   +----------+------------+-----------------------------------------------+
```

### 13.3  Admin Retry on FAILED

```
   POST /api/ra/requests/{requestId}/retry
   Auth: ROLE_ADMIN
```

   -  status → SENT_TO_CA
   -  System re-dispatches to CA
   -  If CA still unavailable: status → FAILED again

---

## 14.  Mode-Switch Rules

   When Admin remaps a profile to a different workflow configuration,
   only new pickups are affected.  In-flight requests continue under
   the configuration stored in approval_mode_at_pickup.

   **Rule 1 — SINGLE → DUAL switch**

```
   +---------------------------+------------------------------------------+
   | In-flight Status          | Behaviour                                |
   +---------------------------+------------------------------------------+
   | SUBMITTED (not picked up) | Next pickup resolves new DUAL config.    |
   | IN_REVIEW (Maker active)  | Continues under SINGLE (locked at        |
   |                           | pickup). Operator uses /approve directly.|
   | APPROVED, ISSUED, CLOSED  | No change. Past approval stage.          |
   +---------------------------+------------------------------------------+
```

   **Rule 2 — DUAL → SINGLE switch**

```
   +---------------------------+------------------------------------------+
   | In-flight Status          | Behaviour                                |
   +---------------------------+------------------------------------------+
   | SUBMITTED (not picked up) | Next pickup resolves new SINGLE config.  |
   | IN_REVIEW (Maker active,  | approval_mode_at_pickup = DUAL is        |
   | mode was DUAL)            | locked. Maker MUST use /review.          |
   |                           | Cannot skip to /approve.                 |
   | REVIEWED (awaiting        | Checker step MUST still complete.        |
   | Checker)                  | Maker already submitted. Cannot skip.    |
   |                           | System enforces: "Checker required."     |
   | APPROVED, ISSUED, CLOSED  | No change. Past approval stage.          |
   +---------------------------+------------------------------------------+
```

   **Rule 3 — General Principles**

   -  approval_mode_at_pickup is stored on each request at pickup time.
   -  Config changes apply to future pickups ONLY.
   -  In-flight requests complete under their locked mode.

---

## 15.  Role and Permission Matrix

```
   +-----------------------------------+-------+----------+---------+--------+
   | Action                            | ADMIN | OPERATOR | AUDITOR | CLIENT |
   +-----------------------------------+-------+----------+---------+--------+
   | Create/update workflow configs    |  YES  |    NO    |   NO    |   NO   |
   | Map profile to config             |  YES  |    NO    |   NO    |   NO   |
   | View all requests and statuses    |  YES  |   YES    |  YES    |  Own   |
   | View transition history           |  YES  |    NO    |  YES    |   NO   |
   | Filter and search requests        |  YES  |   YES    |  YES    |   NO   |
   | Assign request to operator        |  YES  |    NO    |   NO    |   NO   |
   | Close request permanently         |  YES  |    NO    |   NO    |   NO   |
   | Retry failed CA dispatch          |  YES  |    NO    |   NO    |   NO   |
   | Self-pickup as Maker (SUBMITTED)  |   NO  |   YES    |   NO    |   NO   |
   | Self-pickup as Checker (REVIEWED) |   NO  |  YES *   |   NO    |   NO   |
   | Submit Maker review (DUAL)        |   NO  |   YES    |   NO    |   NO   |
   | Approve or reject                 |   NO  |   YES    |   NO    |   NO   |
   | Return request to Admin           |   NO  |   YES    |   NO    |   NO   |
   | Submit CSR                        |   NO  |    NO    |   NO    |  YES   |
   | Download issued certificate       |   NO  |    NO    |   NO    |  YES   |
   +-----------------------------------+-------+----------+---------+--------+
   * Checker MUST NOT be the Maker for the same request.
```

---

## 16.  Admin Dashboard

   The dashboard MUST provide real-time monitoring across all requests,
   grouped by status bucket.  With per-profile configurations, it MUST
   also display the active configuration per profile.

```
   ╔══════════════════════════════════════════════════════════════════╗
   ║           CSR REQUEST DASHBOARD — ADMIN VIEW                     ║
   ╠══════════════════════════════════════════════════════════════════╣
   ║ Active Workflow Configurations:                                   ║
   ║   TLS_SERVER    → High Security DUAL Self-Pickup                  ║
   ║   CODE_SIGNING  → High Security DUAL Self-Pickup                  ║
   ║   TLS_CLIENT    → Standard SINGLE Self-Pickup                     ║
   ║   SMIME         → Standard SINGLE Self-Pickup                     ║
   ╠══════════════════════════════════════════════════════════════════╣
   ║ [INCOMING]       [POOL]       [IN PROGRESS]     [COMPLETED]       ║
   ║ RECEIVED:   1    SUBMITTED:5  IN_REVIEW:  3     ISSUED:  28       ║
   ║ VAL_FAILED: 2                 REVIEWED:   2     CLOSED:   3       ║
   ║                               SENT_TO_CA: 1                       ║
   ║                               PENDING:    1                       ║
   ║                                                                   ║
   ║ [NEEDS ATTENTION]                                                 ║
   ║ REJECTED: 4     RETURNED: 2     FAILED: 0                         ║
   ╠══════════════════════════════════════════════════════════════════╣
   ║ FILTERS:                                                          ║
   ║ [Status▼] [Profile▼] [Operator▼] [Config▼]                       ║
   ║ [Date From] [Date To] [Subject DN] [Search]                       ║
   ╚══════════════════════════════════════════════════════════════════╝
```

---

## 17.  Audit Log Events

```
   +-------------------------+-----------+----------------------------------+
   | Event Code              | Triggered | Description                      |
   |                         | By        |                                  |
   +-------------------------+-----------+----------------------------------+
   | CSR_SUBMIT              | Requestor | CSR submitted for {subjectDn}    |
   | CSR_VALIDATED           | System    | Validation passed                |
   | CSR_VALIDATION_FAILED   | System    | Validation failed — {error}      |
   | CSR_PICKUP_MAKER        | Operator  | Picked up as Maker by {operator} |
   | CSR_PICKUP_CHECKER      | Operator  | Picked up as Checker by {op}     |
   | CSR_ASSIGN              | Admin     | Assigned as Maker to {operator}  |
   | CSR_REVIEW              | Maker     | Maker review submitted           |
   | CSR_APPROVE             | Operator  | Approved — {remarks}             |
   | CSR_REJECT              | Operator  | Rejected — {remarks}             |
   | CSR_RETURN              | Operator  | Returned to Admin — {reason}     |
   | CSR_REASSIGN            | Admin     | Reassigned to {operator}         |
   | CSR_CLOSED              | Admin     | Closed permanently — {reason}    |
   | CERT_DISPATCHED         | System    | Request dispatched to CA         |
   | CERT_PENDING            | System    | CA acknowledged, async processing|
   | CERT_ISSUE              | System    | Certificate issued — {serial}    |
   | CERT_ISSUE_FAIL         | System    | Issuance failed — {error}        |
   | CONFIG_CREATED          | Admin     | Workflow config created — {name} |
   | CONFIG_UPDATED          | Admin     | Workflow config updated — {name} |
   | PROFILE_MAPPING_CHANGED | Admin     | Profile {p} remapped to {config} |
   +-------------------------+-----------+----------------------------------+
```

---

## 18.  Error Codes

```
   +---------------+-----+--------------------------------------------------+
   | Code          | HTTP| Meaning                                          |
   +---------------+-----+--------------------------------------------------+
   | PKI_APR_001   | 400 | CSR is malformed or cannot be parsed             |
   | PKI_APR_002   | 400 | CSR signature verification failed               |
   | PKI_APR_003   | 400 | Subject DN does not match policy                 |
   | PKI_APR_004   | 404 | Request not found for given requestId            |
   | PKI_APR_005   | 403 | Separation of duties violated — Maker MUST NOT   |
   |               |     | also be Checker on the same request              |
   | PKI_APR_006   | 409 | Request is in terminal state — no action allowed |
   | PKI_APR_007   | 409 | Request is already picked up                     |
   | PKI_APR_008   | 400 | Invalid state transition for current status      |
   | PKI_APR_009   | 400 | remarks field required for REJECT action         |
   | PKI_APR_010   | 400 | Profile is not mapped to any workflow config     |
   | PKI_APR_011   | 409 | clientTxnId is already used by another request   |
   | PKI_APR_013   | 503 | CA system unreachable — retry later              |
   | PKI_APR_014   | 400 | Validity period invalid (notBefore >= notAfter)  |
   | PKI_APR_015   | 400 | Maker MUST use /review in DUAL mode; /approve is |
   |               |     | reserved for the Checker                         |
   | PKI_APR_016   | 403 | Operator not authorized for requested action     |
   | PKI_APR_017   | 400 | CA callback HMAC signature validation failed     |
   +---------------+-----+--------------------------------------------------+
```

   NOTE:  Error code PKI_APR_012 ("Unknown workflow mode") has been
   removed.  Server-side routing in the unified /pickup and /approve
   endpoints eliminates the need for callers to specify a mode.

---

## 19.  Security Considerations

### 19.1  Authentication

   -  All RA API endpoints MUST be protected by the organization's
      standard authentication mechanism (Active Directory-backed
      OAuth2 / OpenID Connect tokens).
   -  The CA callback endpoint MUST use HMAC-SHA256 mutual
      authentication with a shared secret provisioned out-of-band.
      The shared secret MUST NOT be committed to source control.

### 19.2  Authorization

   -  Role-based access control (RBAC) MUST be enforced at the API
      layer on every request.
   -  Separation of duties (Maker ≠ Checker) MUST be enforced at the
      API layer with error code PKI_APR_005.

### 19.3  Data Integrity

   -  certificate_request_transitions rows MUST be immutable once
      written.  No UPDATE or DELETE MUST be permitted on this table
      from application code.
   -  approval_mode_at_pickup and workflow_config_id MUST be set once
      at pickup time and MUST NOT be modified thereafter.
   -  CSR PEM MUST be stored as received and MUST NOT be rewritten.

### 19.4  Cryptographic Requirements

   -  RSA keys MUST be minimum 2048 bits; 4096 bits SHOULD be used
      for high-assurance profiles (CODE_SIGNING, TLS_SERVER).
   -  EC keys MUST use NIST P-256 or P-384 curves.
   -  MD5 and SHA-1 digest algorithms MUST NOT be accepted in CSRs.
   -  CSR signature verification MUST be performed before any
      workflow action is taken.

### 19.5  Audit and Non-Repudiation

   -  Every action that changes request status MUST be logged with
      the authenticated user's identity.
   -  Audit log entries MUST be stored in an append-only manner.
   -  System-generated transitions (SYSTEM role) MUST be identifiable
      separately from human operator actions.

---

## 20.  Testing Plan

### 20.1  Unit Tests

```
   +----+--------------------------------------------+
   | #  | Test Case                                  |
   +----+--------------------------------------------+
   |  1 | CSR parse: valid PKCS#10                   |
   |  2 | CSR parse: malformed PEM → PKI_APR_001     |
   |  3 | CSR sig verify: valid key                  |
   |  4 | CSR sig verify: tampered → PKI_APR_002     |
   |  5 | Profile mapping resolution: active mapping |
   |  6 | Profile mapping: no mapping → PKI_APR_010  |
   |  7 | approval_mode_at_pickup locked at pickup   |
   |  8 | workflow_config_id snapshot at pickup      |
   |  9 | Separation of duties: same user → APR_005  |
   | 10 | requestId format: 8-digit zero-padded      |
   | 11 | clientTxnId optional: absent → no error    |
   | 12 | clientTxnId duplicate → PKI_APR_011        |
   +----+--------------------------------------------+
```

### 20.2  Integration Tests — SINGLE Mode

```
   +---+------------------------------------------------+
   | # | Scenario                                       |
   +---+------------------------------------------------+
   | 1 | Submit CSR → RECEIVED → VALIDATED → SUBMITTED  |
   | 2 | Operator self-pickup → IN_REVIEW (SINGLE mode) |
   | 3 | Operator /approve → APPROVED → SENT_TO_CA      |
   | 4 | CA sync response → ISSUED                      |
   | 5 | CA async ack → PENDING → callback → ISSUED     |
   | 6 | CA error → FAILED; Admin retry → ISSUED        |
   | 7 | Operator /approve with REJECT → REJECTED       |
   +---+------------------------------------------------+
```

### 20.3  Integration Tests — DUAL Mode

```
   +---+------------------------------------------------+
   | # | Scenario                                       |
   +---+------------------------------------------------+
   | 1 | Submit CSR → SUBMITTED                         |
   | 2 | Maker self-pickup → IN_REVIEW                  |
   | 3 | Maker /review → REVIEWED                       |
   | 4 | Maker attempts /approve in DUAL → PKI_APR_015  |
   | 5 | Different operator Checker pickup → REVIEWED   |
   | 6 | Maker attempts Checker pickup → PKI_APR_005    |
   | 7 | Checker /approve → APPROVED → SENT_TO_CA       |
   | 8 | Checker /approve with REJECT → REJECTED        |
   +---+------------------------------------------------+
```

### 20.4  Mode-Switch Integration Tests

```
   +---+------------------------------------------------+
   | # | Scenario                                       |
   +---+------------------------------------------------+
   | 1 | Profile remapped SINGLE→DUAL; existing         |
   |   |   IN_REVIEW request continues as SINGLE        |
   | 2 | Profile remapped SINGLE→DUAL; new pickup       |
   |   |   resolves new DUAL config                     |
   | 3 | Profile remapped DUAL→SINGLE; REVIEWED request |
   |   |   still requires Checker pickup                |
   +---+------------------------------------------------+
```

### 20.5  Security Tests

```
   +---+--------------------------------------------------+
   | # | Scenario                                         |
   +---+--------------------------------------------------+
   | 1 | Unauthenticated request → 401                    |
   | 2 | OPERATOR hits admin config endpoint → 403        |
   | 3 | Maker attempts Checker self-pickup → PKI_APR_005 |
   | 4 | CA callback with invalid HMAC → PKI_APR_017      |
   | 5 | Transition table: attempt UPDATE → DB error      |
   | 6 | Replay: same clientTxnId twice → PKI_APR_011     |
   +---+--------------------------------------------------+
```

---

## 21.  References

   [RFC 2119]  Bradner, S., "Key words for use in RFCs to Indicate
               Requirement Levels", BCP 14, RFC 2119, March 1997.

   [RFC 2986]  Nystrom, M. and B. Kaliski, "PKCS #10: Certification
               Request Syntax Specification Version 1.7", RFC 2986,
               November 2000.

   [RFC 5280]  Cooper, D., Santesson, S., Farrell, S., Boeyen, S.,
               Housley, R., and W. Polk, "Internet X.509 Public Key
               Infrastructure Certificate and Certificate Revocation
               List (CRL) Profile", RFC 5280, May 2008.

   [RFC 4211]  Schaad, J., "Internet X.509 Public Key Infrastructure
               Certificate Request Message Format (CRMF)", RFC 4211,
               September 2005.

   [V1-ARCH]   Mandal, K., "RA Approval Workflow Architecture V1",
               Internal Document, Magellan, June 2026.

   [SEB-FDBK]  Reick, S., "Architecture Review Feedback — RA
               Approval Workflow", Internal Email, Magellan,
               June 2026.

---

## 22.  Authors' Addresses

   Kablu Mandal
   Magellan
   Email: kablu@magellan.internal

   Document review by:  Sebastian Reick (Architect, Magellan)
   Document status:     Internal Technical Specification — DRAFT

---

   END OF DOCUMENT
   PKI-RA-APPR-001  v2.0  /  30 June 2026
