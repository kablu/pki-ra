-- =============================================================================
-- V11 — Seed approval workflow error codes into error_catalog
-- DB      : MariaDB 10.6+
-- Module  : raservice
-- Purpose : 13 error codes for the configurable Maker-Checker approval workflow.
--           Used by ExceptionFactory via RaErrorCode enum.
-- =============================================================================

INSERT INTO error_catalog
    (internal_code, external_code, message, description, category, severity, http_status, is_retryable, is_active, created_by, updated_by)
VALUES

-- APPROVAL WORKFLOW errors (PKI_APR_001 — PKI_APR_013)

('PKI_APR_001', 'ERR-401', 'Request is not in the correct status for this operation.',
 'The CSR request status does not allow the requested transition. Check the current status and valid transitions.',
 'APPROVAL', 'WARNING', 400, FALSE, TRUE, 'system', 'system'),

('PKI_APR_002', 'ERR-402', 'You cannot process your own request.',
 'The authenticated user is the same as the CSR requestor. Self-approval is not allowed.',
 'APPROVAL', 'ERROR', 403, FALSE, TRUE, 'system', 'system'),

('PKI_APR_003', 'ERR-403', 'Only Admin can perform this action.',
 'This operation requires ROLE_ADMIN. The authenticated user does not have sufficient privileges.',
 'APPROVAL', 'ERROR', 403, FALSE, TRUE, 'system', 'system'),

('PKI_APR_004', 'ERR-404', 'Only the assigned operator can perform this action.',
 'The authenticated user is not the operator assigned to (or who picked up) this request.',
 'APPROVAL', 'ERROR', 403, FALSE, TRUE, 'system', 'system'),

('PKI_APR_005', 'ERR-405', 'The operator who reviewed this request cannot accept or reject it. A different operator must perform the Checker role.',
 'Separation of duties violation: the Maker (who reviewed and submitted remarks) is strictly prohibited from acting as the Checker for the same request. This is enforced at the API level by comparing the current user ID against the assigned_to_id.',
 'APPROVAL', 'ERROR', 403, FALSE, TRUE, 'system', 'system'),

('PKI_APR_006', 'ERR-406', 'The requestor cannot act as Maker or Checker.',
 'The person or system that submitted the CSR cannot participate in its approval process.',
 'APPROVAL', 'ERROR', 403, FALSE, TRUE, 'system', 'system'),

('PKI_APR_007', 'ERR-407', 'Operator not found.',
 'The specified operator ID does not exist or the user is not active.',
 'APPROVAL', 'ERROR', 404, FALSE, TRUE, 'system', 'system'),

('PKI_APR_008', 'ERR-408', 'Remarks are required for this operation.',
 'The workflow configuration requires remarks to be provided when reviewing, approving, or rejecting a request.',
 'APPROVAL', 'WARNING', 400, FALSE, TRUE, 'system', 'system'),

('PKI_APR_009', 'ERR-409', 'This request has already been picked up by another operator.',
 'Another operator has already picked up this request from the pool. Concurrent pickup is not allowed.',
 'APPROVAL', 'WARNING', 409, FALSE, TRUE, 'system', 'system'),

('PKI_APR_010', 'ERR-410', 'A return reason is required.',
 'When returning a request to Admin, a reason explaining why the request cannot be processed must be provided.',
 'APPROVAL', 'WARNING', 400, FALSE, TRUE, 'system', 'system'),

('PKI_APR_011', 'ERR-411', 'Invalid workflow configuration.',
 'The provided workflow configuration values are invalid. Check approval_mode, assignment_mode, and other settings.',
 'APPROVAL', 'ERROR', 400, FALSE, TRUE, 'system', 'system'),

('PKI_APR_012', 'ERR-412', 'This operation is not available in the current approval mode.',
 'The requested API endpoint is blocked because it does not apply to the current approval mode (SINGLE or DUAL). In SINGLE mode, use /approve or /reject directly. In DUAL mode, use /review first, then Checker /accept or /reject.',
 'APPROVAL', 'WARNING', 400, FALSE, TRUE, 'system', 'system'),

('PKI_APR_013', 'ERR-413', 'Operator has reached the maximum pending request limit.',
 'The operator already has the maximum number of requests assigned. Complete or return existing requests before picking up new ones.',
 'APPROVAL', 'WARNING', 400, FALSE, TRUE, 'system', 'system');
