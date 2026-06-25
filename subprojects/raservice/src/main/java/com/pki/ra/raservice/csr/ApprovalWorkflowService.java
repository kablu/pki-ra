package com.pki.ra.raservice.csr;

import com.pki.ra.common.certificate.CsrRequestRepository;
import com.pki.ra.common.certificate.CsrRequestTransitionRepository;
import com.pki.ra.common.certificate.dto.csr.*;
import com.pki.ra.common.exception.ExceptionFactory;
import com.pki.ra.common.model.CsrRequest;
import com.pki.ra.common.model.CsrRequestTransition;
import com.pki.ra.common.model.User;
import com.pki.ra.common.model.enums.ApprovalMode;
import com.pki.ra.common.model.enums.CsrStatus;
import com.pki.ra.common.user.UserRepository;
import com.pki.ra.common.util.AuditLogService;
import com.pki.ra.raservice.error.RaErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ApprovalWorkflowService {

    private final CsrRequestRepository csrRepo;
    private final CsrRequestTransitionRepository transitionRepo;
    private final UserRepository userRepo;
    private final CsrTransitionService transitionService;
    private final WorkflowConfigService workflowConfig;
    private final AuditLogService auditLogService;
    private final ExceptionFactory exceptionFactory;

    public ApprovalWorkflowService(CsrRequestRepository csrRepo,
                                    CsrRequestTransitionRepository transitionRepo,
                                    UserRepository userRepo,
                                    CsrTransitionService transitionService,
                                    WorkflowConfigService workflowConfig,
                                    AuditLogService auditLogService,
                                    ExceptionFactory exceptionFactory) {
        this.csrRepo = csrRepo;
        this.transitionRepo = transitionRepo;
        this.userRepo = userRepo;
        this.transitionService = transitionService;
        this.workflowConfig = workflowConfig;
        this.auditLogService = auditLogService;
        this.exceptionFactory = exceptionFactory;
    }

    // =========================================================================
    // OPERATOR: Pickup from pool
    // =========================================================================

    @Transactional
    public CsrRequestDto pickup(Long requestId, String username, String ip) {
        CsrRequest request = findRequest(requestId);
        User operator = findUser(username);

        requireStatus(request, CsrStatus.SUBMITTED);
        requireNotRequester(request, operator);

        request.setAssignedTo(operator);
        request.setAssignedAt(Instant.now());
        request.setApprovalModeAtPickup(workflowConfig.getApprovalMode());

        transitionService.transition(request, CsrStatus.IN_REVIEW,
                operator, "OPERATOR", "Picked up from pool");

        csrRepo.save(request);
        auditLogService.logSuccess(username, "CSR_PICKUP",
                request.getRequestId(), "Picked up by " + username, ip);

        return toDto(request);
    }

    // =========================================================================
    // ADMIN: Assign to operator
    // =========================================================================

    @Transactional
    public CsrRequestDto assign(Long requestId, Long operatorId,
                                 String adminUsername, String ip) {
        CsrRequest request = findRequest(requestId);
        User admin = findUser(adminUsername);
        User operator = userRepo.findById(operatorId)
                .orElseThrow(() -> exceptionFactory.create(RaErrorCode.CA_UNREACHABLE,
                        "Operator not found: " + operatorId));

        requireStatusOneOf(request, CsrStatus.SUBMITTED, CsrStatus.RETURNED, CsrStatus.REJECTED);
        requireNotRequester(request, operator);

        request.setAssignedTo(operator);
        request.setAssignedAt(Instant.now());
        if (request.getStatus() == CsrStatus.SUBMITTED) {
            request.setApprovalModeAtPickup(workflowConfig.getApprovalMode());
        }

        transitionService.transition(request, CsrStatus.IN_REVIEW,
                admin, "ADMIN", "Assigned to " + operator.getUsername());

        csrRepo.save(request);
        auditLogService.logSuccess(adminUsername, "CSR_ASSIGN",
                request.getRequestId(), "Assigned to " + operator.getUsername(), ip);

        return toDto(request);
    }

    // =========================================================================
    // SINGLE MODE: Approve directly
    // =========================================================================

    @Transactional
    public CsrRequestDto approve(Long requestId, String remarks,
                                  String username, String ip) {
        requireSingleMode(requestId);
        CsrRequest request = findRequest(requestId);
        User operator = findUser(username);

        requireStatus(request, CsrStatus.IN_REVIEW);
        requireAssignedOperator(request, operator);
        requireRemarksIfConfigured(remarks);

        request.setMakerRemarks(remarks);
        request.setMakerReviewedAt(Instant.now());

        transitionService.transition(request, CsrStatus.APPROVED,
                operator, "OPERATOR", remarks);

        csrRepo.save(request);
        auditLogService.logSuccess(username, "CSR_APPROVE",
                request.getRequestId(), "Approved: " + remarks, ip);

        return toDto(request);
    }

    // =========================================================================
    // SINGLE MODE: Reject directly
    // =========================================================================

    @Transactional
    public CsrRequestDto rejectDirect(Long requestId, String remarks,
                                       String username, String ip) {
        requireSingleMode(requestId);
        CsrRequest request = findRequest(requestId);
        User operator = findUser(username);

        requireStatus(request, CsrStatus.IN_REVIEW);
        requireAssignedOperator(request, operator);
        requireRemarksIfConfigured(remarks);

        request.setMakerRemarks(remarks);
        request.setStatusReason(remarks);

        transitionService.transition(request, CsrStatus.REJECTED,
                operator, "OPERATOR", remarks);

        csrRepo.save(request);
        auditLogService.logSuccess(username, "CSR_REJECT",
                request.getRequestId(), "Rejected: " + remarks, ip);

        return toDto(request);
    }

    // =========================================================================
    // DUAL MODE: Maker submits review
    // =========================================================================

    @Transactional
    public CsrRequestDto review(Long requestId, String remarks,
                                 String username, String ip) {
        requireDualMode(requestId);
        CsrRequest request = findRequest(requestId);
        User maker = findUser(username);

        requireStatus(request, CsrStatus.IN_REVIEW);
        requireAssignedOperator(request, maker);
        requireRemarksIfConfigured(remarks);

        request.setMakerRemarks(remarks);
        request.setMakerReviewedAt(Instant.now());

        transitionService.transition(request, CsrStatus.REVIEWED,
                maker, "OPERATOR", "Maker reviewed: " + remarks);

        csrRepo.save(request);
        auditLogService.logSuccess(username, "CSR_REVIEW",
                request.getRequestId(), "Maker reviewed: " + remarks, ip);

        return toDto(request);
    }

    // =========================================================================
    // DUAL MODE: Checker accepts
    // =========================================================================

    @Transactional
    public CsrRequestDto accept(Long requestId, String remarks,
                                 String username, String ip) {
        requireDualMode(requestId);
        CsrRequest request = findRequest(requestId);
        User checker = findUser(username);

        requireStatus(request, CsrStatus.REVIEWED);
        requireNotMaker(request, checker);
        requireNotRequester(request, checker);

        request.setChecker(checker);
        request.setCheckerDecision("APPROVED");
        request.setCheckerRemarks(remarks);
        request.setCheckerDecidedAt(Instant.now());

        transitionService.transition(request, CsrStatus.APPROVED,
                checker, "OPERATOR", "Checker accepted: " + remarks);

        csrRepo.save(request);
        auditLogService.logSuccess(username, "CSR_ACCEPT",
                request.getRequestId(), "Checker accepted: " + remarks, ip);

        return toDto(request);
    }

    // =========================================================================
    // DUAL MODE: Checker rejects
    // =========================================================================

    @Transactional
    public CsrRequestDto rejectByChecker(Long requestId, String remarks,
                                          String username, String ip) {
        requireDualMode(requestId);
        CsrRequest request = findRequest(requestId);
        User checker = findUser(username);

        requireStatus(request, CsrStatus.REVIEWED);
        requireNotMaker(request, checker);
        requireRemarksIfConfigured(remarks);

        request.setChecker(checker);
        request.setCheckerDecision("REJECTED");
        request.setCheckerRemarks(remarks);
        request.setCheckerDecidedAt(Instant.now());
        request.setStatusReason(remarks);

        transitionService.transition(request, CsrStatus.REJECTED,
                checker, "OPERATOR", "Checker rejected: " + remarks);

        csrRepo.save(request);
        auditLogService.logSuccess(username, "CSR_REJECT",
                request.getRequestId(), "Checker rejected: " + remarks, ip);

        return toDto(request);
    }

    // =========================================================================
    // OPERATOR: Return to Admin
    // =========================================================================

    @Transactional
    public CsrRequestDto returnToAdmin(Long requestId, String reason,
                                        String username, String ip) {
        CsrRequest request = findRequest(requestId);
        User operator = findUser(username);

        requireStatus(request, CsrStatus.IN_REVIEW);
        requireAssignedOperator(request, operator);

        request.setMakerRemarks(reason);
        request.setStatusReason(reason);

        transitionService.transition(request, CsrStatus.RETURNED,
                operator, "OPERATOR", "Returned: " + reason);

        csrRepo.save(request);
        auditLogService.logSuccess(username, "CSR_RETURN",
                request.getRequestId(), "Returned: " + reason, ip);

        return toDto(request);
    }

    // =========================================================================
    // ADMIN: Close permanently
    // =========================================================================

    @Transactional
    public CsrRequestDto close(Long requestId, String reason,
                                String adminUsername, String ip) {
        CsrRequest request = findRequest(requestId);
        User admin = findUser(adminUsername);

        requireStatusOneOf(request, CsrStatus.RETURNED, CsrStatus.REJECTED);

        request.setClosedBy(admin);
        request.setClosedAt(Instant.now());
        request.setClosedReason(reason);

        transitionService.transition(request, CsrStatus.CLOSED,
                admin, "ADMIN", "Closed: " + reason);

        csrRepo.save(request);
        auditLogService.logSuccess(adminUsername, "CSR_CLOSED",
                request.getRequestId(), "Closed: " + reason, ip);

        return toDto(request);
    }

    // =========================================================================
    // QUERIES
    // =========================================================================

    @Transactional(readOnly = true)
    public CsrRequestDto getById(Long id) {
        return toDto(findRequest(id));
    }

    @Transactional(readOnly = true)
    public CsrRequestDto getByRequestId(String requestId) {
        CsrRequest request = csrRepo.findByRequestId(requestId)
                .orElseThrow(() -> exceptionFactory.create(RaErrorCode.CERT_NOT_FOUND, requestId));
        return toDto(request);
    }

    @Transactional(readOnly = true)
    public CsrRequestDto getByClientTxnId(String clientTxnId) {
        CsrRequest request = csrRepo.findByClientTxnId(clientTxnId)
                .orElseThrow(() -> exceptionFactory.create(RaErrorCode.CERT_NOT_FOUND, clientTxnId));
        return toDto(request);
    }

    @Transactional(readOnly = true)
    public Page<CsrRequestDto> listByStatus(CsrStatus status, Pageable pageable) {
        return csrRepo.findByStatus(status, pageable).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public Page<CsrRequestDto> listAll(Pageable pageable) {
        return csrRepo.findAll(pageable).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public List<CsrRequestDto> getMyWork(String username) {
        User operator = findUser(username);
        List<CsrStatus> activeStatuses = List.of(CsrStatus.IN_REVIEW, CsrStatus.REVIEWED);
        return csrRepo.findByAssignedToIdAndStatusIn(operator.getId(), activeStatuses)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public DashboardSummaryDto getSummary() {
        Map<CsrStatus, Long> counts = new EnumMap<>(CsrStatus.class);
        for (CsrStatus s : CsrStatus.values()) counts.put(s, 0L);
        csrRepo.countByStatusGrouped().forEach(row ->
                counts.put((CsrStatus) row[0], (Long) row[1]));

        long total = counts.values().stream().mapToLong(Long::longValue).sum();

        return DashboardSummaryDto.builder()
                .received(counts.get(CsrStatus.RECEIVED))
                .validationFailed(counts.get(CsrStatus.VALIDATION_FAILED))
                .submitted(counts.get(CsrStatus.SUBMITTED))
                .inReview(counts.get(CsrStatus.IN_REVIEW))
                .reviewed(counts.get(CsrStatus.REVIEWED))
                .approved(counts.get(CsrStatus.APPROVED))
                .issued(counts.get(CsrStatus.ISSUED))
                .closed(counts.get(CsrStatus.CLOSED))
                .rejected(counts.get(CsrStatus.REJECTED))
                .returned(counts.get(CsrStatus.RETURNED))
                .failed(counts.get(CsrStatus.FAILED))
                .total(total)
                .build();
    }

    @Transactional(readOnly = true)
    public List<CsrRequestDto.TransitionDto> getHistory(Long requestId) {
        return transitionRepo.findByRequestIdOrderByCreatedAtAsc(requestId)
                .stream().map(this::toTransitionDto).collect(Collectors.toList());
    }

    // =========================================================================
    // VALIDATION HELPERS
    // =========================================================================

    private CsrRequest findRequest(Long id) {
        return csrRepo.findById(id)
                .orElseThrow(() -> exceptionFactory.create(RaErrorCode.CERT_NOT_FOUND, String.valueOf(id)));
    }

    private User findUser(String username) {
        return userRepo.findByUsername(username)
                .orElseThrow(() -> exceptionFactory.create(RaErrorCode.AUTH_FAILED, username));
    }

    private void requireStatus(CsrRequest request, CsrStatus expected) {
        if (request.getStatus() != expected) {
            throw exceptionFactory.create(RaErrorCode.VALIDATION_INVALID_CSR,
                    "Expected status " + expected + " but found " + request.getStatus());
        }
    }

    private void requireStatusOneOf(CsrRequest request, CsrStatus... allowed) {
        for (CsrStatus s : allowed) {
            if (request.getStatus() == s) return;
        }
        throw exceptionFactory.create(RaErrorCode.VALIDATION_INVALID_CSR,
                "Status " + request.getStatus() + " is not valid for this operation");
    }

    private void requireAssignedOperator(CsrRequest request, User operator) {
        if (request.getAssignedTo() == null ||
                !request.getAssignedTo().getId().equals(operator.getId())) {
            throw exceptionFactory.create(RaErrorCode.ACCESS_DENIED,
                    "Only the assigned operator can perform this action");
        }
    }

    private void requireNotMaker(CsrRequest request, User checker) {
        if (request.getAssignedTo() != null &&
                request.getAssignedTo().getId().equals(checker.getId())) {
            throw exceptionFactory.create(RaErrorCode.ACCESS_DENIED,
                    "The operator who reviewed this request cannot accept or reject it. " +
                    "A different operator must perform the Checker role.");
        }
    }

    private void requireNotRequester(CsrRequest request, User operator) {
        if (request.getRequestorUser() != null &&
                request.getRequestorUser().getId().equals(operator.getId())) {
            throw exceptionFactory.create(RaErrorCode.ACCESS_DENIED,
                    "Requester cannot be Maker or Checker");
        }
    }

    private void requireSingleMode(Long requestId) {
        CsrRequest request = findRequest(requestId);
        ApprovalMode mode = request.getApprovalModeAtPickup() != null
                ? request.getApprovalModeAtPickup()
                : workflowConfig.getApprovalMode();
        if (mode == ApprovalMode.DUAL) {
            throw exceptionFactory.create(RaErrorCode.VALIDATION_INVALID_CSR,
                    "DUAL mode active — use /review first, then Checker /accept");
        }
    }

    private void requireDualMode(Long requestId) {
        CsrRequest request = findRequest(requestId);
        ApprovalMode mode = request.getApprovalModeAtPickup() != null
                ? request.getApprovalModeAtPickup()
                : workflowConfig.getApprovalMode();
        if (mode == ApprovalMode.SINGLE) {
            throw exceptionFactory.create(RaErrorCode.VALIDATION_INVALID_CSR,
                    "SINGLE mode active — use /approve or /reject directly");
        }
    }

    private void requireRemarksIfConfigured(String remarks) {
        if (workflowConfig.isRequireRemarks() &&
                (remarks == null || remarks.isBlank())) {
            throw exceptionFactory.create(RaErrorCode.VALIDATION_REQUIRED_FIELD,
                    "Remarks are required");
        }
    }

    // =========================================================================
    // DTO MAPPERS
    // =========================================================================

    private CsrRequestDto toDto(CsrRequest r) {
        return CsrRequestDto.builder()
                .id(r.getId())
                .requestId(r.getRequestId())
                .clientTxnId(r.getClientTxnId())
                .subjectDn(r.getSubjectDn())
                .keyAlgorithm(r.getKeyAlgorithm())
                .keySize(r.getKeySize())
                .signatureAlgorithm(r.getSignatureAlgorithm())
                .subjectAltNames(r.getSubjectAltNames())
                .csrProfile(r.getCsrProfile().name())
                .requestorName(r.getRequestorName())
                .requestorEmail(r.getRequestorEmail())
                .requestorDepartment(r.getRequestorDepartment())
                .requestedValidityDays(r.getRequestedValidityDays())
                .purpose(r.getPurpose())
                .priority(r.getPriority().name())
                .status(r.getStatus().name())
                .statusReason(r.getStatusReason())
                .validationPassed(r.getValidationPassed())
                .assignedToUsername(r.getAssignedTo() != null ? r.getAssignedTo().getUsername() : null)
                .assignedAt(r.getAssignedAt())
                .makerRemarks(r.getMakerRemarks())
                .makerReviewedAt(r.getMakerReviewedAt())
                .checkerUsername(r.getChecker() != null ? r.getChecker().getUsername() : null)
                .checkerDecision(r.getCheckerDecision())
                .checkerRemarks(r.getCheckerRemarks())
                .checkerDecidedAt(r.getCheckerDecidedAt())
                .closedByUsername(r.getClosedBy() != null ? r.getClosedBy().getUsername() : null)
                .closedReason(r.getClosedReason())
                .closedAt(r.getClosedAt())
                .approvalModeAtPickup(r.getApprovalModeAtPickup() != null ? r.getApprovalModeAtPickup().name() : null)
                .certificateId(r.getCertificateId())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }

    private CsrRequestDto.TransitionDto toTransitionDto(CsrRequestTransition t) {
        return CsrRequestDto.TransitionDto.builder()
                .fromStatus(t.getFromStatus() != null ? t.getFromStatus().name() : null)
                .toStatus(t.getToStatus().name())
                .changedByUsername(t.getChangedBy().getUsername())
                .changedByRole(t.getChangedByRole())
                .remarks(t.getRemarks())
                .createdAt(t.getCreatedAt())
                .build();
    }
}
