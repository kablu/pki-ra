package com.pki.ra.raservice.csr;

import com.pki.ra.common.certificate.dto.csr.*;
import com.pki.ra.common.model.enums.CsrStatus;
import com.pki.ra.common.user.service.UserLookupService;
import com.pki.ra.common.util.AuditLogService;
import com.pki.ra.common.web.AbstractSecuredController;
import com.pki.ra.common.web.AuditContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ra")
@Tag(name = "CSR Approval Workflow", description = "Configurable Maker-Checker approval")
public class CsrApprovalController extends AbstractSecuredController {

    private final ApprovalWorkflowService workflowService;
    private final WorkflowConfigService configService;

    public CsrApprovalController(AuditLogService auditLogService,
                                  UserLookupService userLookupService,
                                  ApprovalWorkflowService workflowService,
                                  WorkflowConfigService configService) {
        super(auditLogService, userLookupService);
        this.workflowService = workflowService;
        this.configService = configService;
    }

    // =========================================================================
    // CONFIGURATION
    // =========================================================================

    @GetMapping("/admin/config/workflow")
    @Operation(summary = "Get current workflow configuration")
    public WorkflowConfigDto getWorkflowConfig() {
        return configService.getCurrentConfig();
    }

    // =========================================================================
    // LOOKUP
    // =========================================================================

    @GetMapping("/requests/{id}")
    @Operation(summary = "Get request by internal ID")
    public CsrRequestDto getById(@PathVariable Long id) {
        return workflowService.getById(id);
    }

    @GetMapping("/requests/by-request-id/{requestId}")
    @Operation(summary = "Get request by system-generated request ID")
    public CsrRequestDto getByRequestId(@PathVariable String requestId) {
        return workflowService.getByRequestId(requestId);
    }

    @GetMapping("/requests/by-txn-id/{txnId}")
    @Operation(summary = "Get request by client transaction ID")
    public CsrRequestDto getByClientTxnId(@PathVariable String txnId) {
        return workflowService.getByClientTxnId(txnId);
    }

    @GetMapping("/requests/{id}/history")
    @Operation(summary = "Get transition history for a request")
    public List<CsrRequestDto.TransitionDto> getHistory(@PathVariable Long id) {
        return workflowService.getHistory(id);
    }

    // =========================================================================
    // ADMIN DASHBOARD
    // =========================================================================

    @GetMapping("/requests/summary")
    @Operation(summary = "Status-wise counts for Admin dashboard")
    public DashboardSummaryDto getSummary() {
        return workflowService.getSummary();
    }

    @GetMapping("/requests")
    @Operation(summary = "List requests with optional status filter")
    public Page<CsrRequestDto> listRequests(
            @RequestParam(required = false) CsrStatus status,
            Pageable pageable) {
        if (status != null) {
            return workflowService.listByStatus(status, pageable);
        }
        return workflowService.listAll(pageable);
    }

    // =========================================================================
    // ADMIN ACTIONS
    // =========================================================================

    @PostMapping("/requests/{id}/assign")
    @Operation(summary = "Admin assigns request to an operator")
    public CsrRequestDto assign(@PathVariable Long id,
                                 @Valid @RequestBody AssignRequest body,
                                 HttpServletRequest httpRequest) {
        AuditContext ctx = resolveAuditContext(httpRequest);
        return workflowService.assign(id, body.getOperatorId(), ctx.username(), ctx.ip());
    }

    @PostMapping("/requests/{id}/close")
    @Operation(summary = "Admin permanently closes a request")
    public CsrRequestDto close(@PathVariable Long id,
                                @Valid @RequestBody CloseRequest body,
                                HttpServletRequest httpRequest) {
        AuditContext ctx = resolveAuditContext(httpRequest);
        return workflowService.close(id, body.getReason(), ctx.username(), ctx.ip());
    }

    // =========================================================================
    // OPERATOR — COMMON
    // =========================================================================

    @GetMapping("/requests/pool")
    @Operation(summary = "Available requests for operator pickup")
    public Page<CsrRequestDto> getPool(Pageable pageable) {
        return workflowService.listByStatus(CsrStatus.SUBMITTED, pageable);
    }

    @GetMapping("/requests/my-work")
    @Operation(summary = "Requests picked up by current operator")
    public List<CsrRequestDto> getMyWork(HttpServletRequest httpRequest) {
        String username = resolveUsername();
        return workflowService.getMyWork(username);
    }

    @PostMapping("/requests/{id}/pickup")
    @Operation(summary = "Operator picks up request from pool")
    public CsrRequestDto pickup(@PathVariable Long id, HttpServletRequest httpRequest) {
        AuditContext ctx = resolveAuditContext(httpRequest);
        return workflowService.pickup(id, ctx.username(), ctx.ip());
    }

    @PostMapping("/requests/{id}/return")
    @Operation(summary = "Operator returns request to Admin")
    public CsrRequestDto returnToAdmin(@PathVariable Long id,
                                        @Valid @RequestBody ReturnRequest body,
                                        HttpServletRequest httpRequest) {
        AuditContext ctx = resolveAuditContext(httpRequest);
        return workflowService.returnToAdmin(id, body.getReason(), ctx.username(), ctx.ip());
    }

    // =========================================================================
    // SINGLE MODE — OPERATOR
    // =========================================================================

    @PostMapping("/requests/{id}/approve")
    @Operation(summary = "Operator approves directly (SINGLE mode only)")
    public CsrRequestDto approve(@PathVariable Long id,
                                  @RequestBody(required = false) RemarksRequest body,
                                  HttpServletRequest httpRequest) {
        AuditContext ctx = resolveAuditContext(httpRequest);
        String remarks = body != null ? body.getRemarks() : null;
        return workflowService.approve(id, remarks, ctx.username(), ctx.ip());
    }

    @PostMapping("/requests/{id}/reject")
    @Operation(summary = "Operator rejects directly (SINGLE mode only)")
    public CsrRequestDto reject(@PathVariable Long id,
                                 @RequestBody(required = false) RemarksRequest body,
                                 HttpServletRequest httpRequest) {
        AuditContext ctx = resolveAuditContext(httpRequest);
        String remarks = body != null ? body.getRemarks() : null;
        return workflowService.rejectDirect(id, remarks, ctx.username(), ctx.ip());
    }

    // =========================================================================
    // DUAL MODE — MAKER
    // =========================================================================

    @PostMapping("/requests/{id}/review")
    @Operation(summary = "Maker submits review with remarks (DUAL mode only)")
    public CsrRequestDto review(@PathVariable Long id,
                                 @Valid @RequestBody RemarksRequest body,
                                 HttpServletRequest httpRequest) {
        AuditContext ctx = resolveAuditContext(httpRequest);
        return workflowService.review(id, body.getRemarks(), ctx.username(), ctx.ip());
    }

    // =========================================================================
    // DUAL MODE — CHECKER
    // =========================================================================

    @GetMapping("/requests/pending-check")
    @Operation(summary = "Reviewed requests available for Checker pickup")
    public Page<CsrRequestDto> getPendingCheck(Pageable pageable) {
        return workflowService.listByStatus(CsrStatus.REVIEWED, pageable);
    }

    @PostMapping("/requests/{id}/accept")
    @Operation(summary = "Checker accepts (DUAL mode only)")
    public CsrRequestDto accept(@PathVariable Long id,
                                 @RequestBody(required = false) RemarksRequest body,
                                 HttpServletRequest httpRequest) {
        AuditContext ctx = resolveAuditContext(httpRequest);
        String remarks = body != null ? body.getRemarks() : null;
        return workflowService.accept(id, remarks, ctx.username(), ctx.ip());
    }
}
